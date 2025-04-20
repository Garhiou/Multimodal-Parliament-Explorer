package database;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.*;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Diese Klasse stellt eine Verbindung zu einer MongoDB-Datenbank her und ermöglicht grundlegende
 * CRUD-Operationen (Erstellen, Lesen, Updaten, Löschen, Zählen) für verschiedene Collections.
 *
 * @author Delia Maniliuc
 * @modified by Luana Schäfer (Modifiziert)
 */

public class MongoDatabaseHandler {

    private MongoDBConfig config;
    private MongoClient client;
    private MongoDatabase database;

    /**
     * Konstruktor, der die Konfiguration lädt und die Verbindung zur MongoDB initialisiert.
     *
     * @author Delia Maniliuc
     * @param configPath Der Pfad zur Konfigurationsdatei, die die Zugangsdaten enthält.
     * @throws Exception Falls ein Fehler beim Laden der Konfiguration auftritt.
     */

    public MongoDatabaseHandler(String configPath) throws Exception {
        this.config = new MongoDBConfig(configPath);
        init();
    }

    /**
     * Initialisiert die MongoDB-Verbindung mit den konfigurierten Zugangsdaten.
     *
     * @author Delia Maniliuc
     */
    private void init() {
        ServerAddress address = new ServerAddress(config.getMongoHostname(), config.getMongoPort());
        MongoCredential credential = MongoCredential.createCredential(
                config.getMongoUsername(), config.getMongoDatabase(), config.getMongoPassword().toCharArray());

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(List.of(address)))
                .applyToSocketSettings(builder -> builder.connectTimeout(5, TimeUnit.SECONDS))
                .credential(credential)
                .build();

        client = MongoClients.create(settings);
        database = client.getDatabase(config.getMongoDatabase());
    }

    /**
     * Gibt die aktuell verbundene MongoDB-Datenbank zurück.
     *
     * @author Delia Maniliuc
     * @return Die verbundene MongoDatabase-Instanz.
     */
    public MongoDatabase getDatabase() {
        return this.database;
    }

    /**
     * Fügt ein Dokument in die angegebene Collection ein.
     *
     * @author Delia Maniliuc
     * @param collectionName Name der Collection, in die das Dokument eingefügt wird.
     * @param document       Das einzufügende Dokument.
     */

    public void insertDocument(String collectionName, Document document) {
        database.getCollection(collectionName).insertOne(document);
    }

    /**
     * Sucht Dokumente in einer bestimmten Collection anhand eines Filters.
     *
     * @author Delia Maniliuc
     * @param collectionName Name der Collection, in der gesucht wird.
     * @param filter         Der Suchfilter.
     * @return Eine Iterable-Liste der gefundenen Dokumente.
     */
    public FindIterable<Document> findDocuments(String collectionName, Bson filter) {
        return database.getCollection(collectionName).find(filter);
    }

    /**
     * Aktualisiert ein einzelnes Dokument in der angegebenen Collection.
     *
     * @param collectionName Name der Collection, in der das Dokument aktualisiert wird.
     * @param filter         Der Filter, um das zu aktualisierende Dokument zu finden.
     * @param update         Die durchzuführende Aktualisierung.
     * @return Ein UpdateResult-Objekt mit den Details der Aktualisierung.
     */
    public UpdateResult updateDocument(String collectionName, Bson filter, Bson update) {
        return database.getCollection(collectionName).updateOne(filter, update);
    }

    /**
     * Aktualisiert ein einzelnes Dokument in der angegebenen Collection.
     *
     * @author Delia Maniliuc
     * @param collectionName Name der Collection, in der das Dokument aktualisiert wird.
     * @param filter         Der Filter, um das zu aktualisierende Dokument zu finden.
     * @return Ein UpdateResult-Objekt mit den Details der Aktualisierung.
     */
    public void deleteDocument(String collectionName, Bson filter) {
        database.getCollection(collectionName).deleteOne(filter);
    }

    /**
     * Führt eine Aggregation in der angegebenen Collection durch.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     * @param collectionName Name der Collection
     * @param pipeline Liste von Aggregationsstufen
     * @return Ein `FindIterable<Document>` mit den Aggregationsergebnissen
     */
    public AggregateIterable<Document> aggregateDocuments(String collectionName, List<Bson> pipeline) {
        return database.getCollection(collectionName).aggregate(pipeline);
    }


    /**
     * Zählt die Anzahl der Dokumente in einer Collection, die einem bestimmten Filter entsprechen.
     *
     * @author Delia Maniliuc
     * @param collectionName Name der Collection.
     * @param filter         Der Filter zur Auswahl der zu zählenden Dokumente.
     * @return Die Anzahl der gefundenen Dokumente.
     */
    public long countDocuments(String collectionName, Bson filter) {
        return database.getCollection(collectionName).countDocuments(filter);
    }

    /**
     * Schließt die Verbindung zur MongoDB-Datenbank.
     *
     * @author Delia Maniliuc
     */
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Prüft, ob ein Dokument mit einem bestimmten Feldwert in der Collection existiert.
     *
     * @author Delia Maniliuc
     * @param collectionName Name der Collection.
     * @param field Feld, das geprüft werden soll.
     * @param value Wert des Feldes.
     * @return true, wenn das Dokument existiert, sonst false.
     */
    public boolean documentExists(String collectionName, String field, String value) {
        Document found = database.getCollection(collectionName).find(new Document(field, value)).first();
        return found != null;
    }

    /**
     * Fügt mehrere Dokumente in die angegebene Collection ein.
     *
     * @param collectionName Name der Collection.
     * @param documents Die einzufügenden Dokumente als Liste.
     *
     * @author Luana Schäfer
     */
    public void insertDocuments(String collectionName, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        database.getCollection(collectionName).insertMany(documents);
    }

    /**
     * Holt eine Collection aus der DB.
     *
     * @param collectionName Name der Collection.
     *
     * @author Luana Schäfer
     */
    public MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName);
    }

}
