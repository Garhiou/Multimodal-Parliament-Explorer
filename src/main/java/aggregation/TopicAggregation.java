package aggregation;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.model.Facet;
import database.MongoDatabaseHandler;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

// Implementiert von Luana Schäfer


/**
 * Diese Klasse aggregiert NLP-Daten basierend auf dem dominantesten Topic jeder Rede.
 * Für jedes gefundene Topic werden die zugehörigen Reden analysiert und in aggregierter Form gespeichert.
 * Die Reden selbst werden dabei nicht verändert.
 *
 * @author Luana Schäfer
 */
public class TopicAggregation {
    private final MongoDatabaseHandler dbHandler;

    // Map: Topic → Liste der Rede-IDs, bei denen das Topic dominant ist
    private final Map<String, List<Object>> topicToSpeechIds = new HashMap<>();

    public TopicAggregation(MongoDatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /**
     * Analysiert alle Reden, bestimmt das höchste Topic und sortiert die Rede-IDs nach Topic.
     * @author Luana Schäfer
     */
    private void prepareTopicAssignments() {
        System.out.println("Beginne Vorbereitung: Bestimme höchste Topics für alle Reden...");
        int counter = 0;
        MongoCursor<Document> cursor = dbHandler.getCollection("rede").find().iterator();

        while (cursor.hasNext()) {
            Document speech = cursor.next();
            String highestTopic = determineHighestTopic(speech);

            if (highestTopic != null) {
                topicToSpeechIds
                        .computeIfAbsent(highestTopic, k -> new ArrayList<>())
                        .add(speech.get("_id"));
            }

            counter++;
            if (counter % 1000 == 0) {
                System.out.println("⏳ Bearbeitet: " + counter + " Reden...");
            }
        }

        cursor.close();
        System.out.println("Vorbereitung abgeschlossen. Gefundene Topics: " + topicToSpeechIds.keySet().size());
    }

    /**
     * Bestimmt das höchste Topic für eine Rede basierend auf dem durchschnittlichen Score pro Topic-Wert.
     * @author Luana Schäfer
     */
    private String determineHighestTopic(Document speech) {
        Object topicsObj = speech.get("nlpResults.topics");
        List<Document> topics;

        if (topicsObj instanceof List) {
            topics = (List<Document>) topicsObj;
        } else {
            Document nlpResults = (Document) speech.get("nlpResults");
            if (nlpResults == null) return null;
            topics = (List<Document>) nlpResults.get("topics");
        }

        if (topics == null || topics.isEmpty()) return null;

        Map<String, List<Double>> topicScores = new HashMap<>();

        for (Document topic : topics) {
            String value = topic.getString("value");
            double score = 0.0;

            try {
                score = Double.parseDouble(topic.getString("score"));
            } catch (NumberFormatException e) {
                System.err.println("Fehler bei Score-Konvertierung für Topic '" + value + "': " + topic.getString("score"));
                continue;
            }

            topicScores.computeIfAbsent(value, k -> new ArrayList<>()).add(score);
        }

        String highestTopic = null;
        double highestAvgScore = 0.0;

        for (Map.Entry<String, List<Double>> entry : topicScores.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            if (avg > highestAvgScore) {
                highestAvgScore = avg;
                highestTopic = entry.getKey();
            }
        }

        return highestTopic;
    }

    /**
     * Führt die Aggregation für alle vorbereiteten Topics durch.
     * @author Luana Schäfer
     */
    public void aggregateAllTopics() {
        prepareTopicAssignments();

        for (Map.Entry<String, List<Object>> entry : topicToSpeechIds.entrySet()) {
            String topic = entry.getKey();
            List<Object> speechIds = entry.getValue();

            System.out.println("▶Aggregiere Topic: " + topic + " (Reden: " + speechIds.size() + ")");
            aggregateSingleTopic(topic, speechIds);
        }

        System.out.println("Alle Topic-Aggregationen abgeschlossen.");
    }

    /**
     * Führt die Aggregation für ein Topic anhand der zugehörigen Rede-IDs durch.
     * @author Luana Schäfer
     */
    private void aggregateSingleTopic(String topicName, List<Object> speechIds) {
        if (speechIds.isEmpty()) return;

        Bson matchRelevantDocuments = Aggregates.match(new Document("_id", new Document("$in", speechIds)));

        List<Bson> topicsPipeline = Arrays.asList(
                matchRelevantDocuments,
                Aggregates.unwind("$nlpResults.topics"),
                Aggregates.addFields(new Field<>("nlpResults.topics.numericScore",
                        new Document("$toDouble", "$nlpResults.topics.score"))),
                Aggregates.group("$nlpResults.topics.value",
                        Accumulators.avg("averageScore", "$nlpResults.topics.numericScore"),
                        Accumulators.sum("totalScore", "$nlpResults.topics.numericScore"),
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("averageScore", -1))
        );

        List<Bson> namedEntitiesByTypePipeline = Arrays.asList(
                matchRelevantDocuments,
                Aggregates.unwind("$nlpResults.namedEntities"),
                Aggregates.group("$nlpResults.namedEntities.type", Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        List<Bson> namedEntitiesByTextPipeline = Arrays.asList(
                matchRelevantDocuments,
                Aggregates.unwind("$nlpResults.namedEntities"),
                Aggregates.group(
                        new Document("type", "$nlpResults.namedEntities.type")
                                .append("text", "$nlpResults.namedEntities.text"),
                        Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1)),
                Aggregates.limit(100)
        );

        List<Bson> posTagsPipeline = Arrays.asList(
                matchRelevantDocuments,
                Aggregates.unwind("$nlpResults.tokens"),
                Aggregates.match(new Document("nlpResults.tokens.pos", new Document("$ne", null))),
                Aggregates.group("$nlpResults.tokens.pos", Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("count", -1))
        );

        List<Bson> sentimentPipeline = Arrays.asList(
                matchRelevantDocuments,
                Aggregates.unwind("$nlpResults.sentiment"),
                Aggregates.addFields(new Field<>("numericSentiment",
                        new Document("$toDouble", "$nlpResults.sentiment.sentiment"))),
                Aggregates.group("$numericSentiment", Accumulators.sum("count", 1)),
                Aggregates.sort(new Document("_id", 1))
        );

        List<Bson> speechCountPipeline = Arrays.asList(
                matchRelevantDocuments,
                Aggregates.count("speechCount")
        );

        List<Bson> pipeline = Arrays.asList(
                Aggregates.facet(
                        new Facet("topics", topicsPipeline),
                        new Facet("namedEntitiesByType", namedEntitiesByTypePipeline),
                        new Facet("namedEntitiesByText", namedEntitiesByTextPipeline),
                        new Facet("pos_tags", posTagsPipeline),
                        new Facet("sentiment", sentimentPipeline),
                        new Facet("speechCount", speechCountPipeline)
                ),
                Aggregates.project(new Document("type", "topics")
                        .append("value", topicName)
                        .append("speechCount", new Document("$arrayElemAt", Arrays.asList("$speechCount.speechCount", 0)))
                        .append("nlpAggregation", new Document()
                                .append("topics", "$topics")
                                .append("namedEntitiesByType", "$namedEntitiesByType")
                                .append("namedEntitiesByText", "$namedEntitiesByText")
                                .append("sentiment", "$sentiment")
                                .append("pos_tags", "$pos_tags"))
                )
        );

        AggregateIterable<Document> result = dbHandler.aggregateDocuments("rede", pipeline);

        for (Document doc : result) {
            doc.remove("_id");
            dbHandler.insertDocument("aggregated_data", doc);
            System.out.println("Gespeichert: Topic \"" + topicName + "\" – Reden: " + speechIds.size());
        }
    }
}

