package aggregation;

import database.MongoDatabaseHandler;

// Implementiert von Luana Schäfer

/**
 * Hauptklasse zum Ausführen verschiedener NLP-Aggregationen über die MongoDB-Datenbank.
 * Je nach aktivierten Methoden können hier verschiedene Aggregationsklassen aufgerufen werden:
 *
 * @author Luana Schäfer
 */
public class AggregationTest {
    public static void main(String[] args) {
        try {
            MongoDatabaseHandler dbHandler = new MongoDatabaseHandler("mongodb.properties");
            SessionAggregation sessionAggregation = new SessionAggregation(dbHandler);
            TopicAggregation topicAggregation = new TopicAggregation(dbHandler);


            //System.out.println("Starte Aggregation für alle Reden");
            //new AllSpeechAggregation(dbHandler).aggregate();
            //System.out.println("Aggregation für alle Reden abgeschlossen!");

            //System.out.println("Starte Aggregation für ALLE Sitzungen");
            //sessionAggregation.aggregateAllSessions();
            //System.out.println("Alle Sitzungen wurden gespeichert!");

            //System.out.println("Starte Aggregation für ALLE Redner");
            //new SpeakerAggregation(dbHandler).aggregateAllSpeakers();
            //System.out.println("Aggregation für ALLE Redner abgeschlossen!");

            System.out.println("Starte Aggregation für alle Topics");
            topicAggregation.aggregateAllTopics();
            System.out.println("Aggregation für alle Topics abgeschlossen!");


            dbHandler.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
