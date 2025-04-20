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
 * Parser für Plenarprotokolle aus der MongoDB
 *
 * @author Delia Maniliuc
 */
public class PlenarprotokollParser {
    private MongoDatabaseHandler mongoHandler;

    public PlenarprotokollParser(MongoDatabaseHandler handler) {
        this.mongoHandler = handler;
    }

    /**
     * Holt alle Protokolle aus der Datenbank und parst sie.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */
    public void parseAndStoreAllProtokolle() {
        FindIterable<Document> protokolleIterable = mongoHandler.findDocuments("protokolle", new Document());
        List<Document> protokolle = new ArrayList<>();

        for (Document doc : protokolleIterable) {
            protokolle.add(doc);
        }

        System.out.println("Anzahl der geladenen Protokolle: " + protokolle.size());

        for (Document protokoll : protokolle) {
            String xmlContent = protokoll.getString("content");
            if (xmlContent != null) {
                parseProtokoll(xmlContent);
            }
        }
    }

    /**
     * Holt den Text eines Elements sicher und verhindert NullPointerException.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */
    private String getElementText(Element parent, String tag) {
        Element element = parent.selectFirst(tag);
        return (element != null) ? element.text() : "kein";
    }

    /**
     * Parst ein XML-Protokoll und speichert relevante Daten in die Collection 'rede'.
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */
    private void parseProtokoll(String xmlContent) {
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser());

            // Sitzungs-Infos erfassen
            Element kopf = doc.selectFirst("kopfdaten");
            String sitzungNr = (kopf != null) ? getElementText(kopf, "sitzungsnr") : "unknown-" + System.nanoTime();
            String datum = getElementText(kopf, "datum");
            String wahlperiode = getElementText(kopf, "wahlperiode");
            String ort = getElementText(kopf, "ort");


            System.out.println("Parsing Sitzung Nr. " + sitzungNr + " (" + datum + ")");

            // Tagesordnungspunkte erfassen
            Elements topElements = doc.select("ivz-block");
            List<Document> agendaList = new ArrayList<>();
            for (Element top : topElements) {
                String topIndex = getElementText(top, "ivz-block-titel");
                String topTitle = getElementText(top.selectFirst("ivz-eintrag-inhalt"), "ivz-eintrag-inhalt");
                agendaList.add(new Document("index", topIndex).append("title", topTitle));
            }

            // Reden erfassen
            Elements redeElements = doc.select("rede");
            for (Element rede : redeElements) {
                Element rednerElement = rede.selectFirst("redner");
                if (rednerElement == null) {
                    System.out.println("Kein <redner>-Tag in der Rede gefunden!");
                    continue;
                }

                String rednerID = rednerElement.attr("id");
                String name = getElementText(rednerElement, "vorname") + " " + getElementText(rednerElement, "nachname");
                String fraktion = getElementText(rednerElement, "fraktion");
                String redeID = "ID" + sitzungNr +  rednerID;

                // Rede-Text extrahieren
                Elements textAbschnitte = rede.select("p, kommentar");
                List<Document> textContent = new ArrayList<>();
                StringBuilder fullText = new StringBuilder();

                for (Element abschnitt : textAbschnitte) {
                    String text = abschnitt.text();
                    fullText.append(text).append(" ");
                    String type = abschnitt.tagName().equals("kommentar") ? "comment" : "text";
                    Document textEntry = new Document("id", redeID + "--" + System.nanoTime())
                            .append("speaker", name)
                            .append("text", text)
                            .append("type", type);
                    textContent.add(textEntry);
                }

                // Falls keine Inhalte gefunden wurden, Platzhalter hinzufügen
                if (textContent.isEmpty()) {
                    fullText.append("Keine Redeinhalte gefunden");
                }

                Document redeDoc = new Document("_id", redeID)
                        .append("text", fullText.toString().trim())
                        .append("speaker", name)
                        .append("protocol", new Document("date", datum)
                                //.append("starttime", starttime)
                                //.append("endtime", endtime)
                                .append("index", sitzungNr)
                                .append("title", "Plenarprotokoll " + wahlperiode + "/" + sitzungNr)
                                .append("place", ort)
                                .append("wp", wahlperiode))
                        .append("textContent", textContent)
                        .append("agenda", agendaList);

                mongoHandler.insertDocument("rede", redeDoc);
                System.out.println("Rede gespeichert für Abgeordneten " + name);
            }
        } catch (Exception e) {
            System.out.println("Fehler beim Parsen der XML-Datei!");
            e.printStackTrace();
        }
    }

    /**
     * Hauptmethode für den manuellen Test.
     *
     * @author Delia Maniliuc
     * @modifiedBy Ibrahim Garhiou
     */

    public static void main(String[] args) {
        try {
            MongoDatabaseHandler mongoHandler = new MongoDatabaseHandler("mongodb.properties");
            PlenarprotokollParser parser = new PlenarprotokollParser(mongoHandler);

            // Starte das Parsing nur für gespeicherte Protokolle
            parser.parseAndStoreAllProtokolle();

            System.out.println("Test abgeschlossen!");

        } catch (Exception e) {
            System.out.println("Fehler beim Testen des Parsers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
