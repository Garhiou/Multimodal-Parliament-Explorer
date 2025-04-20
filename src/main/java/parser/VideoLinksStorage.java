package parser;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import database.MongoDatabaseHandler;
import org.bson.Document;
import java.util.HashMap;
import java.util.Map;

/**
 * Die Klasse speichert Video-Links in einer MongoDB-Datenbank.
 *
 * @author Ibrahim Garhiou
 */
public class VideoLinksStorage {
    private final MongoDatabaseHandler mongoHandler;
    private static final String COLLECTION_NAME = "VideoLinks";
    private static final String VIDEO_URL_TEMPLATE = "https://cldf-od.r53.cdn.tv1.eu/1000153copo/ondemand/app144277506/145293313/%s/%s_h264_1920_1080_8000kb_baseline_de_8000.mp4";

    private static final Map<String, String[]> VIDEO_METADATA = new HashMap<>() {{
        put("7629306", new String[]{"Dr. Ingrid Nestle", "ID21111004119-67346906351500"});
        put("7629305", new String[]{"Bärbel Bas", null}); // Keine Angabe für Rede-ID
        put("7629175", new String[]{"Andreas Jung", "ID21111003780-67346918701400"});
        put("7629176", new String[]{"Dr. Nina Scheer", "ID21111004396-67346932610000"});
        put("7629177", new String[]{"Michael Kruse", "ID21111005117-67346943798900"});
        put("7629179", new String[]{"Marc Bernhard", "ID21111004669-67347006171000"});
        put("7629180", new String[]{"Markus Hümpfer", "ID21111005090-67347018594199"});
        put("7629181", new String[]{"Dr. Andreas Lenz", "ID21111004339-67347030318599"});
        put("7629182", new String[]{"Ralph Lenkert", "ID21111004091-67347056886800"});
        put("7629183", new String[]{"Andreas Mehltretter", "ID21111005147-67347070113400"});
        put("7629184", new String[]{"Karsten Hilse", "ID21111004752-67347083172800"});
        put("7629185", new String[]{"Andreas Mehltretter", "ID21111005147-67347095950300"});
        put("7629187", new String[]{"Bärbel Bas", null}); // Keine Angabe für Rede-ID
    }};

    /**
     * Konstruktor zur Initialisierung mit einer MongoDB-Handler-Instanz.
     *
     * @author Ibrahim Garhiou
     * @param handler MongoDatabaseHandler Instanz zur Datenbankverwaltung.
     */
    public VideoLinksStorage(MongoDatabaseHandler handler) {
        this.mongoHandler = handler;
    }

    /**
     * Speichert die vordefinierten Video-Links in der MongoDB-Datenbank.
     */
    public void storeVideoLinks() {
        MongoDatabase db = mongoHandler.getDatabase();
        MongoCollection<Document> collection = db.getCollection(COLLECTION_NAME);

        for (Map.Entry<String, String[]> entry : VIDEO_METADATA.entrySet()) {
            String videoId = entry.getKey();
            String speakerName = entry.getValue()[0];
            String speechId = entry.getValue()[1]; // Kann auch null sein
            String videoPath = String.format(VIDEO_URL_TEMPLATE, videoId, videoId);

            Document videoDoc = new Document("videoId", videoId)
                    .append("speakerName", speakerName)
                    .append("RedeId", speechId != null ? speechId : "Keine Angabe")
                    .append("videoPath", videoPath)
                    .append("mimeType", "video/mp4");

            collection.insertOne(videoDoc);
            System.out.println("Gespeichert: " + videoDoc.toJson());
        }
    }

    /**
     * Hauptmethode zur Ausführung des Programms.
     * Erstellt eine Verbindung zur Datenbank und speichert die Video-Links.
     *
     * @author Ibrahim Garhiou
     * @param args Kommandozeilenargumente (nicht verwendet).
     */
    public static void main(String[] args) {
        try {
            MongoDatabaseHandler mongoHandler = new MongoDatabaseHandler("mongodb.properties");
            VideoLinksStorage storage = new VideoLinksStorage(mongoHandler);
            storage.storeVideoLinks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
