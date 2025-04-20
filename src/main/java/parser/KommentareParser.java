package parser;

import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import database.MongoDatabaseHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser für Kommentare aus Plenarprotokollen
 *
 * @author Ibrahim Garhiou
 */
public class KommentareParser {
    private MongoDatabaseHandler mongoHandler;

    public KommentareParser(MongoDatabaseHandler handler) {
        this.mongoHandler = handler;
    }

    /**
     * Holt alle Protokolle aus der Datenbank und parst die Kommentare.
     *
     * @author Ibrahim Garhiou
     */
    public void parseAndStoreAllKommentare() {
        FindIterable<Document> protokolleIterable = mongoHandler.findDocuments("protokolle", new Document());
        List<Document> protokolle = new ArrayList<>();

        for (Document doc : protokolleIterable) {
            protokolle.add(doc);
        }

        System.out.println("Anzahl der geladenen Protokolle: " + protokolle.size());

        for (Document protokoll : protokolle) {
            String xmlContent = protokoll.getString("content");
            if (xmlContent != null) {
                parseKommentare(xmlContent);
            }
        }
    }

    /**
     * Holt den Text eines Elements sicher und verhindert NullPointerException.
     *
     * @author Ibrahim Garhiou
     */
    private String getElementText(Element parent, String tag) {
        Element element = parent.selectFirst(tag);
        return (element != null) ? element.text() : "keines";
    }

    /**
     * Parst die Kommentare aus einem XML-Protokoll und speichert sie in die Collection 'kommentare'.
     *
     * @author Ibrahim Garhiou
     */
    private void parseKommentare(String xmlContent) {
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser());

            // Sitzungs-Infos erfassen
            Element kopf = doc.selectFirst("kopfdaten");
            String sitzungNr = (kopf != null) ? getElementText(kopf, "sitzungsnr") : "unknown-" + System.nanoTime();

            System.out.println("Parsing Kommentare für Sitzung Nr. " + sitzungNr);

            // Reden erfassen und ihre Kommentare
            Elements redeElements = doc.select("rede");
            for (Element rede : redeElements) {
                Element rednerElement = rede.selectFirst("redner");
                if (rednerElement == null) {
                    System.out.println("Kein <redner>-Tag in der Rede gefunden!");
                    continue;
                }

                String rednerID = rednerElement.attr("id");
                String redeID = "ID" + sitzungNr.replaceAll("[^a-zA-Z0-9]", "") + "-" + rednerID.replaceAll("[^a-zA-Z0-9]", "");

                // Kommentare in der aktuellen Rede erfassen
                Elements kommentarElements = rede.select("kommentar");
                for (Element kommentar : kommentarElements) {
                    String kommentarText = kommentar.text();
                    String kommentarID = "ID" + System.nanoTime();

                    Document kommentarDoc = new Document("_id", kommentarID)
                            .append("text", kommentarText)
                            .append("speakerID", rednerID)
                            .append("redeID", redeID);

                    mongoHandler.insertDocument("kommentare", kommentarDoc);
                    System.out.println("Kommentar gespeichert: " + kommentarText);
                }
            }
        } catch (Exception e) {
            System.out.println("Fehler beim Parsen der Kommentare!");
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
            KommentareParser parser = new KommentareParser(mongoHandler);

            // Starte das Parsing nur für gespeicherte Protokolle
            parser.parseAndStoreAllKommentare();

            System.out.println("Test abgeschlossen!");

        } catch (Exception e) {
            System.out.println("Fehler beim Testen des Parsers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

