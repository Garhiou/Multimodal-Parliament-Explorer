package latex.XMI;

import latex.impl.LaTeXComponent;
import org.bson.Document;
import com.mongodb.client.FindIterable;
import database.MongoDatabaseHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Erweiterte Klasse zum Exportieren von NLP-Ergebnissen im XMI-Format.
 * Ermöglicht die Erstellung von XMI-Dateien für einzelne Reden oder
 * komplette Protokolle mit allen enthaltenen Reden und NLP-Annotationen.
 *
 * @author Maik Kitzmann
 */
public class XMIExporter {
    private final Document rede;
    private final Document nlpData;
    private final List<Document> redenList;
    private final MongoDatabaseHandler mongoHandler;

    /**
     * Konstruktor für den Export eines Protokolls als XMI.
     * Initialisiert den Exporter mit einer Liste von Reden aus einem Protokoll.
     *
     * @param redenList Liste der Reden im Protokoll
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public XMIExporter(List<Document> redenList, MongoDatabaseHandler mongoHandler) {
        this.rede = null;
        this.nlpData = null;
        this.redenList = redenList;
        this.mongoHandler = mongoHandler;
    }

    /**
     * Methode zum Erstellen eines XMIExporters für ein Protokoll anhand seiner ID.
     * Sucht alle Reden für das angegebene Protokoll und erstellt einen entsprechenden Exporter.
     *
     * @param protokollId ID des zu exportierenden Protokolls
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @return Ein neuer XMIExporter für das angegebene Protokoll
     * @author Maik Kitzmann
     */
    public static XMIExporter createForProtokoll(String protokollId, MongoDatabaseHandler mongoHandler) {
        System.out.println("Erstelle XMI-Exporter für Protokoll mit ID: " + protokollId);

        // Suche alle Reden für dieses Protokoll
        Document filter = new Document("protocol.index", protokollId);
        FindIterable<Document> redenDocs = mongoHandler.findDocuments("rede", filter);

        List<Document> redenList = new ArrayList<>();
        redenDocs.forEach(redenList::add);

        System.out.println("Gefunden: " + redenList.size() + " Reden für Protokoll " + protokollId);

        return new XMIExporter(redenList, mongoHandler);
    }

    /**
     * Generiert XMI-Inhalt aus NLP-Daten einer einzelnen Rede oder eines Protokolls.
     * Wählt basierend auf den verfügbaren Daten automatisch den richtigen Exportmodus.
     *
     * @return Der generierte XMI-Inhalt als String
     * @author Maik Kitzmann
     */
    public String generateXMI() {
        if (rede != null && nlpData != null) {
            return generateSingleSpeechXMI();
        } else if (redenList != null && !redenList.isEmpty()) {
            return generateProtocolXMI();
        } else {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\"><error>No data available</error></xmi:XMI>";
        }
    }

    /**
     * Generiert XMI für eine einzelne Rede.
     * Erstellt eine XMI-Datei mit dem Redetext und allen NLP-Annotationen
     * wie Named Entities und POS-Tags.
     *
     * @return Der generierte XMI-Inhalt für eine einzelne Rede
     * @author Maik Kitzmann
     */
    private String generateSingleSpeechXMI() {
        System.out.println("Generiere XMI für einzelne Rede");
        StringBuilder xmi = new StringBuilder();

        // XMI-Header
        xmi.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmi.append("<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\" xmlns:cas=\"http:///uima/cas.ecore\" xmlns:type=\"http:///de/tudarmstadt/ukp/dkpro/core/api/ner/type.ecore\">\n");

        // Redetext ermitteln
        String redeText = extractSpeechText(rede);
        System.out.println("  Redetext geladen, Länge: " + redeText.length() + " Zeichen");

        xmi.append("  <cas:Sofa xmi:id=\"_1\" sofaNum=\"1\" sofaID=\"_InitialView\" sofaString=\"" + LaTeXComponent.Utils.escapeXML(redeText) + "\"/>\n");
        xmi.append("  <cas:View sofa=\"1\" members=\"\"/>\n");

        // Named Entities
        int itemId = 2; // Start-ID für Annotationen weil 1 vergeben
        itemId = addNamedEntities(xmi, nlpData, itemId);

        // POS-Tags
        addPOSTags(xmi, nlpData, itemId);

        // XMI-Footer
        xmi.append("</xmi:XMI>");
        System.out.println("XMI für einzelne Rede generiert, Länge: " + xmi.length() + " Zeichen");

        return xmi.toString();
    }

    /**
     * Generiert XMI für ein komplettes Protokoll mit mehreren Reden.
     * Erstellt eine strukturierte XMI-Datei mit Protokoll-Metadaten,
     * allen Reden und deren NLP-Annotationen.
     *
     * @return Der generierte XMI-Inhalt für ein komplettes Protokoll
     * @author Maik Kitzmann
     */
    private String generateProtocolXMI() {
        System.out.println("Generiere XMI für komplettes Protokoll mit " + redenList.size() + " Reden");
        StringBuilder xmi = new StringBuilder();

        // XMI-Header
        xmi.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmi.append("<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\" ");
        xmi.append("xmlns:cas=\"http:///uima/cas.ecore\" ");
        xmi.append("xmlns:type=\"http:///de/tudarmstadt/ukp/dkpro/core/api/ner/type.ecore\" ");
        xmi.append("xmlns:protocol=\"http:///de/parliament/protocol.ecore\" ");
        xmi.append("xmlns:speech=\"http:///de/parliament/speech.ecore\">\n");

        // Protokoll-Metadaten aus der ersten Rede extrahieren
        Document firstSpeech = redenList.get(0);
        Document protocolInfo = (Document) firstSpeech.get("protocol");
        String protocolTitle = protocolInfo != null ? protocolInfo.getString("title") : "Unbekanntes Protokoll";
        String protocolDate = protocolInfo != null ? protocolInfo.getString("date") : "Unbekanntes Datum";
        String protocolIndex = protocolInfo != null ? protocolInfo.getString("index") : "Unbekannter Index";

        // Protokoll-Eintrag
        xmi.append("  <protocol:Protocol xmi:id=\"_protocol1\" title=\"" +
                LaTeXComponent.Utils.escapeXML(protocolTitle) + "\" date=\"" +
                LaTeXComponent.Utils.escapeXML(protocolDate) + "\" index=\"" +
                LaTeXComponent.Utils.escapeXML(protocolIndex) + "\">\n");

        int globalSofaCounter = 1;
        int globalEntityCounter = 1;

        // Für jede Rede einen eigenen View erstellen
        for (int redeIndex = 0; redeIndex < redenList.size(); redeIndex++) {
            Document currentSpeech = redenList.get(redeIndex);
            String redeId = currentSpeech.getString("_id") != null ? currentSpeech.getString("_id") : "speech" + redeIndex;
            String partei = "Unbekannte Partei";
            String rednerName = currentSpeech.getString("speaker");
            if (rednerName != null) {
                Document rednerDoc = utils.SpeechUtils.getRednerBySpeakerName(rednerName, mongoHandler);
                if (rednerDoc != null) {
                    // Extrahiere Partei aus dem Redner-Dokument
                    String rednerPartei = rednerDoc.getString("party");
                    if (rednerPartei != null && !rednerPartei.isEmpty()) {
                        partei = rednerPartei;
                    }
                }
            }

            System.out.println("  Verarbeite Rede " + (redeIndex + 1) + "/" + redenList.size() +
                    " von " + rednerName + " (" + partei + ")");

            // Rede-Eintrag im Protokoll
            xmi.append("    <speech:Speech xmi:id=\"_speech" + (redeIndex + 1) + "\" ");
            xmi.append("sofa=\"_sofa" + globalSofaCounter + "\" ");
            xmi.append("speaker=\"" + LaTeXComponent.Utils.escapeXML(rednerName) + "\" ");
            xmi.append("party=\"" + LaTeXComponent.Utils.escapeXML(partei) + "\" ");
            xmi.append("id=\"" + LaTeXComponent.Utils.escapeXML(redeId) + "\" ");
            xmi.append("protocol=\"_protocol1\"/>\n");

            // Redetext ermitteln
            String redeText = extractSpeechText(currentSpeech);

            // Sofa für diese Rede
            xmi.append("    <cas:Sofa xmi:id=\"_sofa" + globalSofaCounter + "\" ");
            xmi.append("sofaNum=\"" + globalSofaCounter + "\" ");
            xmi.append("sofaID=\"_View" + (redeIndex + 1) + "\" ");
            xmi.append("sofaString=\"" + LaTeXComponent.Utils.escapeXML(redeText) + "\"/>\n");

            // Erstelle View für diese Rede
            xmi.append("    <cas:View sofa=\"_sofa" + globalSofaCounter + "\" members=\"\"/>\n");

            // NLP-Daten für diese Rede verarbeiten
            Document speechNlpData = (Document) currentSpeech.get("nlpResults");
            if (speechNlpData != null) {
                // Named Entities
                List<Document> namedEntities = (List<Document>) speechNlpData.get("namedEntities");
                if (namedEntities != null && !namedEntities.isEmpty()) {
                    System.out.println("    Verarbeite " + namedEntities.size() + " Named Entities");
                    for (int i = 0; i < namedEntities.size(); i++) {
                        Document entity = namedEntities.get(i);
                        Integer begin = entity.getInteger("begin");
                        Integer end = entity.getInteger("end");
                        String type = entity.getString("type");
                        String text_value = entity.getString("text");

                        xmi.append("    <type:NamedEntity xmi:id=\"_entity" + globalEntityCounter + "\" ");
                        xmi.append("sofa=\"_sofa" + globalSofaCounter + "\" ");
                        xmi.append("begin=\"" + begin + "\" ");
                        xmi.append("end=\"" + end + "\" ");
                        xmi.append("type=\"" + type + "\" ");
                        xmi.append("value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");

                        globalEntityCounter++;
                    }
                }

                // POS-Tags aus tokens extrahieren
                List<Document> tokens = (List<Document>) speechNlpData.get("tokens");
                if (tokens != null && !tokens.isEmpty()) {
                    System.out.println("    Verarbeite " + tokens.size() + " Tokens für POS-Tags");
                    for (int i = 0; i < tokens.size(); i++) {
                        Document token = tokens.get(i);
                        Integer begin = token.getInteger("begin");
                        Integer end = token.getInteger("end");
                        String posValue = token.getString("pos");
                        String text_value = token.getString("text");

                        if (begin != null && end != null && posValue != null && text_value != null) {
                            xmi.append("    <type:POS xmi:id=\"_pos" + globalEntityCounter + "\" ");
                            xmi.append("sofa=\"_sofa" + globalSofaCounter + "\" ");
                            xmi.append("begin=\"" + begin + "\" ");
                            xmi.append("end=\"" + end + "\" ");
                            xmi.append("posValue=\"" + posValue + "\" ");
                            xmi.append("value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");

                            globalEntityCounter++;
                        }
                    }
                }
            }

            globalSofaCounter++;
        }

        // Schließe Protokoll-Element
        xmi.append("  </protocol:Protocol>\n");

        // XMI-Footer
        xmi.append("</xmi:XMI>");
        System.out.println("XMI für komplettes Protokoll generiert, Länge: " + xmi.length() + " Zeichen");

        return xmi.toString();
    }

    /**
     * Extrahiert den Textinhalt aus einem Rede-Dokument.
     * Versucht zunächst, den strukturierten textContent zu verwenden,
     * und greift bei Bedarf auf das einfache text-Feld zurück.
     *
     * @param speech Das Rede-Dokument, aus dem der Text extrahiert werden soll
     * @return Der extrahierte Redetext als String
     * @author Maik Kitzmann
     */
    private String extractSpeechText(Document speech) {
        String speechText = "";

        // Zuerst prüfen, ob es textContent gibt
        List<Document> textContent = (List<Document>) speech.get("textContent");
        if (textContent != null && !textContent.isEmpty()) {
            StringBuilder textBuilder = new StringBuilder();
            for (Document block : textContent) {
                String text = block.getString("text");
                if (text != null && !text.isEmpty()) {
                    textBuilder.append(text).append(" ");
                }
            }
            speechText = textBuilder.toString().trim();
        } else {
            // Fallback auf das text-Feld
            speechText = speech.getString("text");
        }

        if (speechText == null) {
            speechText = "";
        }

        return speechText;
    }

    /**
     * Fügt Named Entities zum XMI hinzu.
     * Extrahiert alle Named Entities aus den NLP-Ergebnissen und
     * formatiert sie als XMI-Elemente.
     *
     * @param xmi Der StringBuilder mit dem XMI-Inhalt
     * @param nlpData Die NLP-Daten mit Named Entities
     * @param startId Die erste zu verwendende ID für Named Entity-Elemente
     * @return Die nächste verfügbare ID nach dem Hinzufügen aller Named Entities
     * @author Maik Kitzmann
     */
    private int addNamedEntities(StringBuilder xmi, Document nlpData, int startId) {
        int nextId = startId;
        List<Document> namedEntities = (List<Document>) nlpData.get("namedEntities");

        if (namedEntities != null && !namedEntities.isEmpty()) {
            System.out.println("  Verarbeite " + namedEntities.size() + " Named Entities");
            for (int i = 0; i < namedEntities.size(); i++) {
                Document entity = namedEntities.get(i);
                Integer begin = entity.getInteger("begin");
                Integer end = entity.getInteger("end");
                String type = entity.getString("type");
                String text_value = entity.getString("text");

                xmi.append("  <type:NamedEntity xmi:id=\"_" + nextId + "\" sofa=\"1\" begin=\"" + begin + "\" end=\"" + end + "\" type=\"" + type + "\" value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");
                nextId++;
            }
        } else {
            System.out.println("  Keine Named Entities gefunden");
        }
        // Gibt die nächste verfügbare eindeutige ID zurück als Zähler
        return nextId;
    }

    /**
     * Fügt POS-Tags zum XMI hinzu.
     * Extrahiert alle POS-Tags aus den Tokens in den NLP-Ergebnissen
     * und formatiert sie als XMI-Elemente.
     *
     * @param xmi Der StringBuilder mit dem XMI-Inhalt
     * @param nlpData Die NLP-Daten mit Tokens und POS-Tags
     * @param startId Die erste zu verwendende ID für POS-Tag-Elemente
     * @author Maik Kitzmann
     */
    private void addPOSTags(StringBuilder xmi, Document nlpData, int startId) {
        int nextId = startId;
        List<Document> tokens = (List<Document>) nlpData.get("tokens");

        if (tokens != null && !tokens.isEmpty()) {
            System.out.println("  Verarbeite " + tokens.size() + " Tokens für POS-Tags");
            for (int i = 0; i < tokens.size(); i++) {
                Document token = tokens.get(i);
                Integer begin = token.getInteger("begin");
                Integer end = token.getInteger("end");
                String posValue = token.getString("pos");
                String text_value = token.getString("text");

                if (begin != null && end != null && posValue != null && text_value != null) {
                    xmi.append("  <type:POS xmi:id=\"_" + nextId + "\" sofa=\"1\" begin=\"" + begin + "\" end=\"" + end + "\" posValue=\"" + posValue + "\" value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");
                    nextId++;
                }
            }
        } else {
            System.out.println("  Keine POS-Tags gefunden");
        }
    }
}
