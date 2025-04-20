package parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.MongoDatabaseHandler;
import org.bson.Document;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Diese Klasse parst die Bildinformationen aus der JSON-Datei und speichert sie in einer MongoDB-Collection.
 *
 * @author Ibrahim Garhiou
 */
public class BilderParser {
    private static final String JSON_FILE_PATH = "src/main/resources/Bilder/mpPictures.json";
    private final MongoDatabaseHandler mongoHandler;

    public BilderParser(MongoDatabaseHandler handler) {
        this.mongoHandler = handler;
    }

    /**
     * Liest die JSON-Datei mit Bildinformationen und speichert sie in MongoDB.
     *
     * @author Ibrahim Garhiou
     */
    public void parseBilder() {
        try {
            File jsonFile = Paths.get(JSON_FILE_PATH).toFile();
            if (!jsonFile.exists()) {
                System.err.println("JSON-Datei nicht gefunden: " + JSON_FILE_PATH);
                return;
            }

            System.out.println("Lese JSON-Datei: " + JSON_FILE_PATH);
            ObjectMapper objectMapper = new ObjectMapper();

            // JSON-Datei als Liste von Objekten einlesen
            List<Map<String, List<Map<String, Object>>>> rawList = objectMapper.readValue(jsonFile, new TypeReference<>() {});

            for (Map<String, List<Map<String, Object>>> entry : rawList) {
                for (Map.Entry<String, List<Map<String, Object>>> bilderEintrag : entry.entrySet()) {
                    String id = bilderEintrag.getKey(); // ID des Abgeordneten
                    List<Map<String, Object>> bilder = bilderEintrag.getValue();

                    // Dokument für MongoDB erstellen
                    Document bilderDocument = new Document("_id", id)
                            .append("pictures", bilder);

                    // Falls noch nicht vorhanden, in MongoDB speichern
                    if (!mongoHandler.documentExists("bilder", "_id", id)) {
                        mongoHandler.insertDocument("bilder", bilderDocument);
                        System.out.println("Bild für Abgeordneten gespeichert: " + id);
                    } else {
                        System.out.println("Bild für Abgeordneten bereits in DB: " + id);
                    }
                }
            }
            System.out.println("Bilder-Parsing abgeschlossen!");
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen der Bilder-JSON!");
            e.printStackTrace();
        }
    }

    /**
     * Hauptmethode für den manuellen Test.
     *
     * @author Ibrahim Garhiou
     */
    public static void main(String[] args) {
        try {
            MongoDatabaseHandler mongoHandler = new MongoDatabaseHandler("mongodb.properties");
            BilderParser parser = new BilderParser(mongoHandler);
            parser.parseBilder();
            System.out.println("Parsing abgeschlossen!");
        } catch (Exception e) {
            System.out.println("Fehler beim Starten des Parsers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

