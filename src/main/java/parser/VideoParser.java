package parser;


import database.MongoDatabaseHandler;
import org.bson.Document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;

/**
 * VideoParser erstellt eine Collection "videos" in der DB und
 * speichert zu jeder Rede (mit redeID) den zugehörigen Videolink.
 * OPTIONAL: Es kann auch das Video heruntergeladen und lokal gespeichert werden.
 *
 * @author Delia Maniliuc
 * @modifiedBy Ibrahim Garhiou
 */
public class VideoParser {

    private final MongoDatabaseHandler mongoHandler;
    private static final String COLLECTION_NAME = "videos";

    // Leg fest, wo Videos gespeichert werden sollen (falls du sie wirklich downloaden willst).
    // Wenn du nur die URL speichern willst, kannst du diesen Teil weglassen.
    private static final String DOWNLOAD_FOLDER = "videos/";

    /**
     * Konstruktor: Erwartet eine bereits initialisierte MongoDatabaseHandler-Instanz.
     *
     *  @author Delia Maniliuc
     *  @modifiedBy Ibrahim Garhiou
     */
    public VideoParser(MongoDatabaseHandler mongoHandler) {
        this.mongoHandler = mongoHandler;
    }

    /**
     * Speichert ein Video zu einer Rede (z.B. "redeID") in der DB.
     * Wenn "download" true ist, wird das Video zusätzlich heruntergeladen.
     * "videoUrl" ist der direkte .mp4-Link.
     *
     *  @author Delia Maniliuc
     *  @modifiedBy Ibrahim Garhiou
     */
    public void storeVideoForRede(String redeID, String videoUrl, boolean download) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            System.out.println("Kein gültiger Video-Link übergeben.");
            return;
        }

        // 1) OPTIONAL: Video herunterladen, wenn gewünscht
        String localPath = null;
        if (download) {
            localPath = downloadVideo(videoUrl);
        }

        // 2) Video-Metadaten in eigener Collection speichern
        Document videoDoc = new Document("redeID", redeID)
                .append("videoURL", videoUrl)
                .append("timestamp", Date.from(Instant.now()));

        // Falls wir das Video lokal gespeichert haben, Pfad hinzufügen
        if (localPath != null) {
            videoDoc.append("localPath", localPath);
        }

        mongoHandler.insertDocument(COLLECTION_NAME, videoDoc);
        System.out.println("Video-Eintrag für Rede " + redeID + " gespeichert.");
    }

    /**
     * Einfacher Download einer .mp4-Datei. Speichert sie im DOWNLOAD_FOLDER.
     * Gibt den lokalen Dateinamen zurück.
     *
     *  @author Delia Maniliuc
     *  @modifiedBy Ibrahim Garhiou
     */
    private String downloadVideo(String videoUrl) {
        // Dateinamen aus dem letzten Abschnitt der URL erstellen
        String fileName = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
        if (fileName.isEmpty()) {
            // Fallback, falls die URL keinen Dateinamen hat
            fileName = "video_" + System.nanoTime() + ".mp4";
        }
        String localPath = DOWNLOAD_FOLDER + fileName;

        // Download durchführen
        try (InputStream in = new URL(videoUrl).openStream()) {
            Files.createDirectories(Paths.get(DOWNLOAD_FOLDER));
            Files.copy(in, Paths.get(localPath));
            System.out.println("Video heruntergeladen: " + localPath);
            return localPath;
        } catch (IOException e) {
            System.out.println("Fehler beim Download des Videos: " + videoUrl);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Beispiel-Main-Methode, die zeigt, wie man diese Klasse nutzen könnte.
     *
     *  @author Delia Maniliuc
     *  @modifiedBy Ibrahim Garhiou
     */
    public static void main(String[] args) {
        try {
            // 1) MongoDB-Handler laden (nutzt mongodb.properties für Connection-Details)
            MongoDatabaseHandler mongoHandler = new MongoDatabaseHandler("mongodb.properties");

            // 2) VideoParser instanzieren
            VideoParser parser = new VideoParser(mongoHandler);

            // 3) Beispiel: Wir speichern EIN einzelnes Video, verknüpft mit Rede "rede123"
            // Du kannst "download" true oder false setzen.
            String redeID = "rede123";
            String videoUrl = "https://cldf-od.r53.cdn.tv1.eu/.../example.mp4?fdl=1";
            parser.storeVideoForRede(redeID, videoUrl, true);

            System.out.println("Fertig!");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Fehler: " + e.getMessage());
        }
    }
}
