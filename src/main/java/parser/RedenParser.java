package parser;

import database.MongoDatabaseHandler;
import org.bson.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.List;

/**
 * Der {@code RedenParser} ist verantwortlich für das Parsen von Reden aus XML-Dokumenten
 * und das Speichern der extrahierten Informationen in einer MongoDB-Datenbank.
 *
 * <p>Die Reden enthalten Informationen über den Redner, das Sitzungsdatum,
 * die Sitzungsnummer, den Sitzungsort, die Agenda und den eigentlichen Redetext.</p>
 *
 * <p>Die extrahierten Daten werden als {@link Document} Objekte gespeichert und
 * in der MongoDB-Datenbank persistiert.</p>
 *
 * @author Ibrahim Garhiou
 */
public class RedenParser {
    private MongoDatabaseHandler mongoHandler;

    /**
     * Erstellt eine neue Instanz des {@code RedenParser} mit einem angegebenen Datenbank-Handler.
     *
     * @param handler Eine Instanz von {@link MongoDatabaseHandler} zum Speichern der Reden.
     */
    public RedenParser(MongoDatabaseHandler handler) {
        this.mongoHandler = handler;
    }

    /**
     * Parst die Reden aus dem angegebenen XML-Dokument und speichert sie in der MongoDB-Datenbank.
     *
     * @param doc Ein {@link Element}-Objekt, das die XML-Struktur der Sitzung enthält.
     */
    public void parseReden(Element doc) {
        Elements redeElements = doc.select("rede");
        for (Element rede : redeElements) {
            Element rednerElement = rede.selectFirst("redner");
            if (rednerElement == null) {
                System.out.println(" Kein <redner>-Tag in der Rede gefunden!");
                continue;
            }
            String rednerID = rednerElement.attr("id");
            String redeText = getElementText(rede, "p");
            String sitzungNr = getElementText(doc, "sitzung > nummer");
            String sitzungsTitel = getElementText(doc, "sitzung > titel");
            String datum = getElementText(doc, "sitzung > datum");
            String ort = getElementText(doc, "sitzung > ort");

            // Agenda erfassen
            Element agendaElement = doc.selectFirst("tagesordnungspunkt");
            Document agenda = new Document()
                    .append("index", agendaElement != null ? agendaElement.attr("top") : "N/A")
                    .append("id", agendaElement != null ? agendaElement.attr("id") : "N/A")
                    .append("title", agendaElement != null ? agendaElement.text() : "N/A");

            // Protokoll-Informationen erfassen
            Document protocol = new Document()
                    .append("date", datum)
                    .append("index", sitzungNr)
                    .append("title", sitzungsTitel)
                    .append("place", ort)
                    .append("wp", 20); // Wahlperiode

            // TextContent erfassen
            List<Document> textContent = new ArrayList<>();
            Elements textAbschnitte = rede.select("p");
            int index = 0;
            for (Element abschnitt : textAbschnitte) {
                textContent.add(new Document()
                        .append("id", "ID" + sitzungNr + "--" + index)
                        .append("speaker", rednerID)
                        .append("text", abschnitt.text())
                        .append("type", "text"));
                index++;
            }

            // Finales Speech-Dokument
            Document speech = new Document("_id", "ID" + sitzungNr)
                    .append("text", redeText)
                    .append("speaker", rednerID)
                    .append("protocol", protocol)
                    .append("textContent", textContent)
                    .append("agenda", agenda);

            // In MongoDB speichern
            mongoHandler.insertDocument("speeches", speech);
            System.out.println(" Rede gespeichert für Abgeordneten ID " + rednerID);
        }
    }

    /**
     * Hilfsmethode zur sicheren Extraktion des Textinhalts eines Elements.
     * Falls das Element nicht existiert, wird "N/A" zurückgegeben.
     *
     * @param parent Das übergeordnete Element, in dem das gesuchte Element enthalten sein könnte.
     * @param tag Der Tag-Name des gesuchten Elements.
     * @return Der extrahierte Text des Elements oder "N/A", falls das Element nicht vorhanden ist.
     */
    private String getElementText(Element parent, String tag) {
        Element element = parent.selectFirst(tag);
        return (element != null) ? element.text() : "N/A";
    }
}
