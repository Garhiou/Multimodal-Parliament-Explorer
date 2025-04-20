package scraper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * VideosScraper lädt Bundestag-Mediathek-Videos automatisch herunter.
 * Es generiert die MP4-URLs direkt aus der bekannten Struktur und speichert sie unter src/main/resources/Videos/.
 *
 * @author Ibrahim Garhiou
 */
public class VideosScraper {

    private static final String DOWNLOAD_FOLDER = "src/main/resources/Videos/";

    // Liste der Video-IDs Redner
    private static final List<String> TARGET_VIDEO_IDS = Arrays.asList(
            "7629306", // Dr. Ingrid Nestle
            "7629305", // Bärbel Bas
            "7629175", // Andreas Jung
            "7629176", // Dr. Nina Scheer
            "7629177", // Michael Kruse
            "7629179", // Marc Bernhard
            "7629180", // Markus Hümpfer
            "7629181", // Dr. Andreas Lenz
            "7629182", // Ralph Lenkert
            "7629183", // Andreas Mehltretter
            "7629184", // Karsten Hilse
            "7629185", // Andreas Mehltretter
            "7629187"  // Bärbel Bas
    );

    // MP4-URL Muster
    private static final String VIDEO_URL_TEMPLATE =
            "https://cldf-od.r53.cdn.tv1.eu/1000153copo/ondemand/app144277506/145293313/%s/%s_h264_1920_1080_8000kb_baseline_de_8000.mp4";

    public static void main(String[] args) {
        for (String videoId : TARGET_VIDEO_IDS) {
            String videoUrl = String.format(VIDEO_URL_TEMPLATE, videoId, videoId);
            downloadVideo(videoUrl, videoId);
        }
    }

    /**
     * Lädt eine .mp4-Datei herunter und speichert sie unter resources/Videos/.
     *
     * @author Ibrahim Garhiou
     */
    private static void downloadVideo(String videoUrl, String videoId) {
        String fileName = DOWNLOAD_FOLDER + videoId + ".mp4";
        try (InputStream in = new URL(videoUrl).openStream()) {
            Files.createDirectories(Paths.get(DOWNLOAD_FOLDER)); // Erstellt den Ordner
            Files.copy(in, Paths.get(fileName));
            System.out.println(" Heruntergeladen: " + fileName);
        } catch (IOException e) {
            System.out.println(" Fehler beim Download von: " + videoUrl);
            e.printStackTrace();
        }
    }
}
