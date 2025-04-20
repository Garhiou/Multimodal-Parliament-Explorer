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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Implementiert von Luana Schäfer



/**
 * Diese Klasse aggregiert NLP-Daten basierend auf einzelnen Rednern (Speakers).
 * Für jeden Speaker werden alle Reden analysiert und die Ergebnisse in der Datenbank gespeichert.
 *
 * @author Luana Schäfer
 */
public class SpeakerAggregation {
    private final MongoDatabaseHandler dbHandler;


    /**
     * Konstruktor zur Initialisierung des Speaker-Aggregators.
     *
     * @param dbHandler Verbindung zur MongoDB.
     *
     * @author Luana Schäfer
     */
    public SpeakerAggregation(MongoDatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }


    /**
     * Führt eine vollständige NLP-Aggregation für einen bestimmten Redner durch.
     * Es werden Topics, Named Entities, Sentiment-Werte und POS-Tags analysiert.
     * Das Ergebnis wird als ein Dokument mit type "speakers" in der MongoDB gespeichert.
     *
     * @param speakerName Der Name des Redners, dessen Reden aggregiert werden sollen.
     *
     * @author Luana Schäfer
     */
    public void aggregateSingleSpeaker(String speakerName) {
        System.out.println("[SpeakerAggregation] Starte Aggregation für Speaker: " + speakerName);

        // Filter für einen bestimmten Speaker
        Bson matchSpeaker = Aggregates.match(new Document("speaker", speakerName));

        // Topics-Aggregation
        List<Bson> topicsPipeline = Arrays.asList(
                matchSpeaker,
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

        // Named Entities Aggregation - Typen
        List<Bson> namedEntitiesByTypePipeline = Arrays.asList(
                matchSpeaker,
                Aggregates.match(new Document("nlpResults.namedEntities", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.namedEntities"),
                Aggregates.group("$nlpResults.namedEntities.type",
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        // Named Entities Aggregation - Text
        List<Bson> namedEntitiesByTextPipeline = Arrays.asList(
                matchSpeaker,
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

        // POS-Tags Aggregation
        List<Bson> posTagsPipeline = Arrays.asList(
                matchSpeaker,
                Aggregates.match(new Document("nlpResults.tokens", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.tokens"),
                Aggregates.match(new Document("nlpResults.tokens.pos", new Document("$ne", null))),
                Aggregates.group("$nlpResults.tokens.pos",
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        // Sentiment Aggregation
        List<Bson> sentimentPipeline = Arrays.asList(
                matchSpeaker,
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
                Aggregates.project(new Document("type", "speakers")
                        .append("value", speakerName)
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
            System.out.println("Speaker " + speakerName + " gespeichert: " + doc.toJson());
        }
    }


    /**
     * Sammelt alle eindeutigen Rednernamen aus der Datenbank und führt die Aggregation für jeden einzeln aus.
     * Die Methode sucht nach Dokumenten mit einem "speaker"-Feld in der Collection "rede".
     *
     * @author Luana Schäfer
     */
    public void aggregateAllSpeakers() {
        Set<String> speakerNames = new HashSet<>();

        MongoCursor<Document> cursor = dbHandler
                .getCollection("rede")
                .find()
                .projection(new Document("speaker", 1))
                .iterator();

        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String name = doc.getString("speaker");
            if (name != null && !name.isBlank()) {
                speakerNames.add(name);
            }
        }

        if (speakerNames.isEmpty()) {
            System.out.println("Keine Redner in der Datenbank gefunden!");
            return;
        }

        for (String name : speakerNames) {
            System.out.println("Aggregiere Redner: " + name);
            aggregateSingleSpeaker(name);
        }

        System.out.println("Aggregation für alle Redner abgeschlossen.");
    }
}

