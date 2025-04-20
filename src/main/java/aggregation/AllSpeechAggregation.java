package aggregation;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import database.MongoDatabaseHandler;
import com.mongodb.client.AggregateIterable;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Facet;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

// Implementiert von Luana Schäfer


/**
 * Diese Klasse führt eine umfassende NLP-Aggregation über alle Reden in der Datenbank durch.
 * Die Ergebnisse werden in Kategorien wie Topics, Named Entities, POS-Tags und Sentiment zusammengefasst.
 * Nach der Verarbeitung wird ein einzelnes Dokument mit den aggregierten Daten in der Collection "aggregated_data" gespeichert.
 *
 * @author Luana Schäfer
 */
public class AllSpeechAggregation {
    private final MongoDatabaseHandler dbHandler;

    /**
     * Konstruktor zur Initialisierung der Aggregation mit einer bestehenden MongoDB-Verbindung.
     *
     * @param dbHandler Die MongoDB-Verbindung über die Hilfsklasse {@link MongoDatabaseHandler}.
     *
     * Implementiert von Luana Schäfer
     */
    public AllSpeechAggregation(MongoDatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }


    /**
     * Führt die Aggregation aller Reden in der Collection "rede" durch.
     * Die NLP-Daten (Topics, Named Entities, Sentiment, POS-Tags) werden dabei mit MongoDB-Aggregationspipelines
     * verarbeitet und zusammengefasst. Die Ergebnisse werden in der Collection "aggregated_data" gespeichert.
     *
     * Implementiert von Luana Schäfer
     */
    public void aggregate() {
        System.out.println("🔵 [AllSpeechAggregation] Starte Aggregation für alle Reden...");

        // Topics-Aggregation - scores der Topics aggregieren
        List<Bson> topicsPipeline = Arrays.asList(
                Aggregates.match(new Document("nlpResults.topics", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.topics"),
                // Konvertiere `score` von String zu Double
                Aggregates.addFields(new Field<>("nlpResults.topics.numericScore",
                        new Document("$toDouble", "$nlpResults.topics.score"))),
                Aggregates.group(
                        "$nlpResults.topics.value",
                        Accumulators.avg("averageScore", "$nlpResults.topics.numericScore"),
                        Accumulators.sum("totalScore", "$nlpResults.topics.numericScore")
                ),
                Aggregates.sort(new Document("averageScore", -1))
        );


        // Named Entities Aggregation
        List<Bson> namedEntitiesByTypePipeline = Arrays.asList(
                Aggregates.match(new Document("nlpResults.namedEntities", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.namedEntities"),
                Aggregates.group("$nlpResults.namedEntities.type",
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        List<Bson> namedEntitiesByTextPipeline = Arrays.asList(
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
                Aggregates.match(new Document("nlpResults.tokens", new Document("$exists", true))),
                Aggregates.unwind("$nlpResults.tokens"),
                Aggregates.match(new Document("nlpResults.tokens.pos", new Document("$ne", null))),
                Aggregates.group("$nlpResults.tokens.pos",
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        // Sentiment-Aggregation – rundet Werte auf zwei Nachkommastellen und zählt Häufigkeiten
        List<Bson> sentimentPipeline = Arrays.asList(
                Aggregates.match(new Document("nlpResults.sentiment", new Document("$exists", true))),
                // Entferne das 0. Element aus dem Array und behalte nur den Rest
                Aggregates.addFields(new Field<>("filteredSentiment",
                        new Document("$slice", Arrays.asList("$nlpResults.sentiment", 1, 1000)) // Skip 1. Element, nehme den Rest
                )),
                Aggregates.unwind("$filteredSentiment"),
                // Konvertiere den Wert `sentiment.sentiment` sicher in Double
                Aggregates.addFields(new Field<>("numericSentiment", new Document("$toDouble", "$filteredSentiment.sentiment"))),
                // Runde auf zwei Nachkommastellen
                Aggregates.project(
                        new Document("roundedSentiment",
                                new Document("$round", Arrays.asList("$numericSentiment", 2)))
                ),
                // Zähle, wie oft jeder gerundete Wert vorkommt
                Aggregates.group("$roundedSentiment", Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("_id", 1))
        );


        // Pipeline
        List<Bson> pipeline = Arrays.asList(
                Aggregates.facet(
                        new Facet("topics", topicsPipeline),
                        new Facet("namedEntitiesByType", namedEntitiesByTypePipeline),
                        new Facet("namedEntitiesByText", namedEntitiesByTextPipeline),
                        new Facet("pos_tags", posTagsPipeline),
                        new Facet("sentiment", sentimentPipeline)
                ),
                Aggregates.project(new Document("type", "all")
                        .append("value", "all speeches")
                        .append("nlpAggregation", new Document()
                                .append("topics", "$topics")
                                .append("namedEntitiesByType", "$namedEntitiesByType")
                                .append("namedEntitiesByText", "$namedEntitiesByText")
                                .append("sentiment", "$sentiment")
                                .append("pos_tags", "$pos_tags"))
                )
        );

        System.out.println("[AllSpeechAggregation] Führe Aggregation in MongoDB aus...");

        AggregateIterable<Document> result = dbHandler.aggregateDocuments("rede", pipeline);
        List<Document> aggregatedDocs = new ArrayList<>();

        for (Document doc : result) {
            doc.remove("_id");
            aggregatedDocs.add(doc);
        }

        System.out.println("[AllSpeechAggregation] Aggregation abgeschlossen. Speichere " + aggregatedDocs.size() + " Dokumente...");

        if (!aggregatedDocs.isEmpty()) {
            dbHandler.insertDocuments("aggregated_data", aggregatedDocs);
            System.out.println("[AllSpeechAggregation] Daten erfolgreich gespeichert!");
        } else {
            System.out.println("[AllSpeechAggregation] Keine Daten zum Speichern gefunden.");
        }
    }
}
