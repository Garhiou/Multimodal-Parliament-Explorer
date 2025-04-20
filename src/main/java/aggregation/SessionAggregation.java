package aggregation;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import database.MongoDatabaseHandler;
import com.mongodb.client.AggregateIterable;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Facet;
import com.mongodb.client.MongoCollection;

import java.util.*;

// Implementiert von Luana Schäfer


/**
 * Diese Klasse führt NLP-Aggregationen basierend auf einzelnen Sitzungen durch.
 * Für jede Sitzung werden alle Reden analysiert.
 * Die Ergebnisse umfassen Topics, Named Entities, Sentiment und POS-Tags.
 *
 * @author Luana Schäfer
 */
public class SessionAggregation {
    private final MongoDatabaseHandler dbHandler;


    /**
     * Konstruktor zur Initialisierung der SessionAggregation.
     *
     * @param dbHandler Verbindung zur MongoDB.
     *
     * @author Luana Schäfer
     */
    public SessionAggregation(MongoDatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }


    /**
     * Aggregiert alle Reden aus der Datenbank gruppiert nach Sitzungen.
     * Für jede Sitzung wird die Aggregation einzeln durchgeführt.
     * Erkennt alle vorhandenen Werte in `protocol.index` und verarbeitet diese nacheinander.
     *
     * @author Luana Schäfer
     */
    public void aggregateAllSessions() {
        System.out.println("[SessionAggregation] Starte Aggregation für alle Sitzungen...");

        // Alle vorhandenen protocol.index-Werte ermitteln
        MongoCollection<Document> collection = dbHandler.getCollection("rede");
        List<String> sessionIndices = new ArrayList<>();

        try (MongoCursor<String> cursor = collection.distinct("protocol.index", String.class).iterator()) {
            while (cursor.hasNext()) {
                sessionIndices.add(cursor.next().trim());
            }
        }

        System.out.println("Gefundene Sitzungen: " + sessionIndices.size());

        // Jede Sitzung einzeln aggregieren
        for (String sessionIndex : sessionIndices) {
            System.out.println("➡ Aggregiere Sitzung: " + sessionIndex);
            aggregateSingleSession(sessionIndex);
        }

        System.out.println("🏁 [SessionAggregation] Alle Sitzungen verarbeitet und gespeichert.");
    }


    /**
     * Führt die NLP-Aggregation für eine einzelne Sitzung durch.
     * Aggregiert Topics, Named Entities (nach Typ & Text), POS-Tags und Sentiment-Werte.
     * Die Ergebnisse werden als einzelnes Dokument mit `type = "sessions"` in die Datenbank geschrieben.
     *
     * @param sessionIndex Der Sitzungs-Index, für den aggregiert werden soll.
     *
     * @author Luana Schäfer
     */
    public void aggregateSingleSession(String sessionIndex) {
        System.out.println("🔍 Aggregation für Sitzung " + sessionIndex + " läuft...");

        // Filter nur für diese eine Sitzung
        Bson matchSession = Aggregates.match(new Document("protocol.index", sessionIndex));

        // Topics
        List<Bson> topicsPipeline = Arrays.asList(
                matchSession,
                Aggregates.match(new Document("nlpResults.topics", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.topics"),
                Aggregates.addFields(new Field<>("nlpResults.topics.numericScore",
                        new Document("$toDouble", "$nlpResults.topics.score"))),
                Aggregates.group(
                        "$nlpResults.topics.value",
                        Accumulators.avg("averageScore", "$nlpResults.topics.numericScore"),
                        Accumulators.sum("totalScore", "$nlpResults.topics.numericScore")
                ),
                Aggregates.sort(new Document("averageScore", -1))
        );

        // Named Entities (Type)
        List<Bson> namedEntitiesByTypePipeline = Arrays.asList(
                matchSession,
                Aggregates.match(new Document("nlpResults.namedEntities", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.namedEntities"),
                Aggregates.group("$nlpResults.namedEntities.type",
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        // Named Entities (Text)
        List<Bson> namedEntitiesByTextPipeline = Arrays.asList(
                matchSession,
                Aggregates.match(new Document("nlpResults.namedEntities", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.namedEntities"),
                Aggregates.group(
                        new Document("type", "$nlpResults.namedEntities.type")
                                .append("text", "$nlpResults.namedEntities.text"),
                        Accumulators.sum("count", 1)
                ),
                Aggregates.sort(new Document("count", -1)),
                Aggregates.limit(100)
        );

        // POS-Tags
        List<Bson> posTagsPipeline = Arrays.asList(
                matchSession,
                Aggregates.match(new Document("nlpResults.tokens", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.tokens"),
                Aggregates.match(new Document("nlpResults.tokens.pos", new Document("$ne", null))),
                Aggregates.group("$nlpResults.tokens.pos",
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        // Sentiment
        List<Bson> sentimentPipeline = Arrays.asList(
                matchSession,
                Aggregates.match(new Document("nlpResults.sentiment", new Document("$exists", true))),
                Aggregates.addFields(new Field<>("filteredSentiment",
                        new Document("$slice", Arrays.asList("$nlpResults.sentiment", 1, 1000)))),
                Aggregates.unwind("$filteredSentiment"),
                Aggregates.addFields(new Field<>("numericSentiment",
                        new Document("$toDouble", "$filteredSentiment.sentiment"))),
                Aggregates.project(
                        new Document("roundedSentiment",
                                new Document("$round", Arrays.asList("$numericSentiment", 2)))
                ),
                Aggregates.group("$roundedSentiment", Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("_id", 1))
        );

        // Hauptpipeline
        List<Bson> pipeline = Arrays.asList(
                Aggregates.facet(
                        new Facet("topics", topicsPipeline),
                        new Facet("namedEntitiesByType", namedEntitiesByTypePipeline),
                        new Facet("namedEntitiesByText", namedEntitiesByTextPipeline),
                        new Facet("pos_tags", posTagsPipeline),
                        new Facet("sentiment", sentimentPipeline)
                ),
                Aggregates.project(new Document("type", "sessions")
                        .append("value", sessionIndex)
                        .append("nlpAggregation", new Document()
                                .append("topics", "$topics")
                                .append("namedEntitiesByType", "$namedEntitiesByType")
                                .append("namedEntitiesByText", "$namedEntitiesByText")
                                .append("sentiment", "$sentiment")
                                .append("pos_tags", "$pos_tags"))
                )
        );

        // Aggregation ausführen
        AggregateIterable<Document> result = dbHandler.aggregateDocuments("rede", pipeline);

        for (Document doc : result) {
            doc.remove("_id");
            dbHandler.insertDocument("aggregated_data", doc);
            System.out.println("Sitzung " + sessionIndex + " gespeichert: " + doc.toJson());
        }
    }
}

