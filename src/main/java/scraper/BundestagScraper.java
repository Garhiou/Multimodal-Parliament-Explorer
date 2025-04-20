package scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import database.MongoDatabaseHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * BundestagScraper lädt alle XML-Protokolle der 20. Wahlperiode und speichert sie in MongoDB.
 *
 * @author Delia Maniliuc
 * @modifiedBy Ibrahim Garhiou
 */
public class BundestagScraper {
    private static final String BASE_AJAX_URL = "https://www.bundestag.de/ajax/filterlist/de/services/opendata/866354-866354";
    private static final int BATCH_SIZE = 10; // Anzahl der Protokolle pro Anfrage
    private static MongoDatabaseHandler mongoHandler;

    /**
     * Initialisiert den MongoDB-Handler.
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */
    public BundestagScraper(String configPath) throws Exception {
        mongoHandler = new MongoDatabaseHandler(configPath);
    }

    /**
     * Holt alle XML-Links der Bundestagsprotokolle durch Pagination.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     * @return Liste der XML-Links
     */
    public static List<String> fetchXmlLinks() {
        List<String> xmlLinks = new ArrayList<>();
        int offset = 0;

        while (true) {
            String ajaxUrl = BASE_AJAX_URL + "?limit=" + BATCH_SIZE + "&offset=" + offset + "&noFilterSet=true";
            System.out.println("🔍 Lade Seite mit Offset: " + offset);

            try {
                Document doc = Jsoup.connect(ajaxUrl)
                        .userAgent("Mozilla/5.0")
                        .ignoreContentType(true)
                        .timeout(10000)
                        .get();

                Elements links = doc.select("a[href$=.xml]");
                if (links.isEmpty()) {
                    System.out.println("Keine weiteren XML-Links gefunden. Beende die Suche.");
                    break;
                }

                for (Element link : links) {
                    String xmlUrl = link.absUrl("href");
                    if (!xmlLinks.contains(xmlUrl)) {
                        xmlLinks.add(xmlUrl);
                        System.out.println("Gefunden: " + xmlUrl);
                    }
                }

                offset += BATCH_SIZE; // Erhöhe das Offset für die nächste Anfrage
            } catch (IOException e) {
                System.out.println(" Fehler beim Abrufen der Bundestagsseite mit Offset: " + offset);
                e.printStackTrace();
                break;
            }
        }
        return xmlLinks;
    }

    /**
     * Lädt XML-Dateien und speichert sie in MongoDB, falls sie nicht vorhanden sind.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */
    public static void downloadAndStoreXmlFiles() {
        List<String> xmlLinks = fetchXmlLinks();
        for (String link : xmlLinks) {
            if (!mongoHandler.documentExists("protokolle", "url", link)) {
                try {
                    Document xmlDoc = Jsoup.connect(link).get();
                    String xmlContent = xmlDoc.outerHtml();
                    org.bson.Document mongoDoc = new org.bson.Document("url", link);
                    mongoDoc.append("content", xmlContent);
                    mongoHandler.insertDocument("protokolle", mongoDoc);
                    System.out.println(" Gespeichert: " + link);
                } catch (IOException e) {
                    System.out.println(" Fehler beim Abrufen der XML-Datei: " + link);
                }
            } else {
                System.out.println("️ Bereits vorhanden: " + link);
            }
        }
    }

    /**
     * Startet einen Timer, um regelmäßig nach neuen Protokollen zu suchen.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */
    public static void startScheduledScraper() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(BundestagScraper::downloadAndStoreXmlFiles, 0, 24, TimeUnit.HOURS);
    }

    /**
     * Hauptmethode für den manuellen Test.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */

    public static void main(String[] args) {
        try {
            BundestagScraper scraper = new BundestagScraper("mongodb.properties");
            scraper.startScheduledScraper();
        } catch (Exception e) {
            System.out.println(" Fehler bei der Initialisierung: " + e.getMessage());
        }
    }
}

