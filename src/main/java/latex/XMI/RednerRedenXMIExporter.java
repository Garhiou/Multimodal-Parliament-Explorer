package latex.XMI;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import com.mongodb.client.FindIterable;
import java.util.*;

/**
 * Klasse zum Exportieren aller Reden eines bestimmten Redners im XMI-Format.
 * Ermöglicht das Erstellen von XMI-Dateien, die Redner-Metadaten, Reden und
 * NLP-Annotationen wie Named Entities und POS-Tags enthalten.
 *
 * @author Maik Kitzmann
 */
public class RednerRedenXMIExporter {
    private final Document redner;
    private final List<Document> reden;
    private final MongoDatabaseHandler mongoHandler;

    /**
     * Konstruktor für RednerRedenXMIExporter mit allen erforderlichen Daten.
     *
     * @param redner MongoDB-Dokument mit den Redner-Informationen
     * @param reden Liste von MongoDB-Dokumenten mit den Reden des Redners
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public RednerRedenXMIExporter(Document redner, List<Document> reden, MongoDatabaseHandler mongoHandler) {
        this.redner = redner;
        this.reden = reden;
        this.mongoHandler = mongoHandler;
    }


    /**
     * Generiert eine XMI-Repräsentation aller Reden des Redners.
     * Filtert zunächst die gültigen Reden und erstellt dann eine XMI-Datei mit
     * Rednerinformationen, Reden-Metadaten und NLP-Annotationen.
     *
     * @return String mit dem generierten XMI-Inhalt
     * @author Maik Kitzmann
     */
    public String generateXMI() {
        System.out.println("Generiere XMI für " + reden.size() + " Reden von " + redner.getString("name"));

        List<Document> filteredReden = filterValidSpeeches(reden);
        System.out.println("Nach Filterung: " + filteredReden.size() + " gültige Reden");

        if (filteredReden.isEmpty()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\"><e>Keine Reden gefunden</e></xmi:XMI>";
        }

        // Hole Redner-Informationen
        String rednerVorname = redner.getString("vorname") != null ? redner.getString("vorname") : "";
        String rednerNachname = redner.getString("name") != null ? redner.getString("name") : "";
        String rednerFullName = rednerVorname + " " + rednerNachname;
        rednerFullName = rednerFullName.trim();
        String partei = redner.getString("party") != null ? redner.getString("party") : "Unbekannte Partei";
        String role = redner.getString("role") != null ? redner.getString("role") : "";
        String rednerId = redner.getObjectId("_id").toString();
        StringBuilder xmi = new StringBuilder();

        // XMI-Header
        xmi.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmi.append("<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\" ");
        xmi.append("xmlns:cas=\"http:///uima/cas.ecore\" ");
        xmi.append("xmlns:type=\"http:///de/tudarmstadt/ukp/dkpro/core/api/ner/type.ecore\" ");
        xmi.append("xmlns:speaker=\"http:///de/parliament/speaker.ecore\" ");
        xmi.append("xmlns:speech=\"http:///de/parliament/speech.ecore\">\n");

        // Redner-Informationen
        xmi.append("  <speaker:Speaker xmi:id=\"_speaker1\" name=\"" +
                LaTeXComponent.Utils.escapeXML(rednerFullName) + "\" id=\"" +
                LaTeXComponent.Utils.escapeXML(rednerId) + "\" ");

        xmi.append("party=\"" + LaTeXComponent.Utils.escapeXML(partei) + "\" ");

        if (!role.isEmpty()) {
            xmi.append("role=\"" + LaTeXComponent.Utils.escapeXML(role) + "\" ");
        }

        xmi.append(">\n");

        // Verarbeite jede Rede
        int sofaCounter = 1;
        int entityCounter = 1;

        for (Document rede : filteredReden) {
            System.out.println("  Verarbeite Rede " + sofaCounter + "/" + filteredReden.size());

            // Hole Redetext
            String redeText = extractSpeechText(rede);

            // Hole Rede-Metadaten
            Document protocol = (Document) rede.get("protocol");
            String protocolIndex = "unknown";
            String protocolTitle = "Unknown Protocol";
            String protocolDate = "";

            if (protocol != null) {
                if (protocol.getString("index") != null) {
                    protocolIndex = protocol.getString("index");
                }
                if (protocol.getString("title") != null) {
                    protocolTitle = protocol.getString("title");
                }
                if (protocol.getString("date") != null) {
                    protocolDate = protocol.getString("date");
                }
            }

            xmi.append("    <cas:Sofa xmi:id=\"_sofa" + sofaCounter + "\" ");
            xmi.append("sofaNum=\"" + sofaCounter + "\" ");
            xmi.append("sofaID=\"_View" + sofaCounter + "\" ");
            xmi.append("sofaString=\"" + LaTeXComponent.Utils.escapeXML(redeText) + "\"/>\n");

            // Füge View hinzu
            xmi.append("    <cas:View sofa=\"_sofa" + sofaCounter + "\" members=\"\"/>\n");

            // Füge Rede-Informationen hinzu
            xmi.append("    <speech:Speech xmi:id=\"_speech" + sofaCounter + "\" ");
            xmi.append("sofa=\"_sofa" + sofaCounter + "\" ");
            xmi.append("speaker=\"_speaker1\" ");
            xmi.append("protocol=\"" + LaTeXComponent.Utils.escapeXML(protocolIndex) + "\" ");
            xmi.append("protocolTitle=\"" + LaTeXComponent.Utils.escapeXML(protocolTitle) + "\" ");

            if (!protocolDate.isEmpty()) {
                xmi.append("date=\"" + LaTeXComponent.Utils.escapeXML(protocolDate) + "\" ");
            }

            xmi.append("id=\"" + LaTeXComponent.Utils.escapeXML(rede.getObjectId("_id").toString()) + "\"/>\n");

            // Füge NLP-Annotationen hinzu
            Document nlpData = (Document) rede.get("nlpResults");
            if (nlpData != null) {
                // Named Entities
                List<Document> namedEntities = (List<Document>) nlpData.get("namedEntities");
                if (namedEntities != null && !namedEntities.isEmpty()) {
                    System.out.println("    Verarbeite " + namedEntities.size() + " Named Entities");
                    for (Document entity : namedEntities) {
                        Integer begin = entity.getInteger("begin");
                        Integer end = entity.getInteger("end");
                        String type = entity.getString("type");
                        String text_value = entity.getString("text");

                        if (begin != null && end != null && type != null && text_value != null) {
                            xmi.append("    <type:NamedEntity xmi:id=\"_entity" + entityCounter + "\" ");
                            xmi.append("sofa=\"_sofa" + sofaCounter + "\" ");
                            xmi.append("begin=\"" + begin + "\" ");
                            xmi.append("end=\"" + end + "\" ");
                            xmi.append("type=\"" + LaTeXComponent.Utils.escapeXML(type) + "\" ");
                            xmi.append("value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");

                            entityCounter++;
                        }
                    }
                }

                List<Document> tokens = (List<Document>) nlpData.get("tokens");
                if (tokens != null && !tokens.isEmpty()) {
                    System.out.println("    Verarbeite " + tokens.size() + " Tokens für POS-Tags");
                    for (Document token : tokens) {
                        Integer begin = token.getInteger("begin");
                        Integer end = token.getInteger("end");
                        String posValue = token.getString("pos");
                        String text_value = token.getString("text");

                        if (begin != null && end != null && posValue != null && text_value != null) {
                            xmi.append("    <type:POS xmi:id=\"_pos" + entityCounter + "\" ");
                            xmi.append("sofa=\"_sofa" + sofaCounter + "\" ");
                            xmi.append("begin=\"" + begin + "\" ");
                            xmi.append("end=\"" + end + "\" ");
                            xmi.append("posValue=\"" + LaTeXComponent.Utils.escapeXML(posValue) + "\" ");
                            xmi.append("value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");

                            entityCounter++;
                        }
                    }
                }
            }

            sofaCounter++;
        }

        // Speaker-Tag beenden
        xmi.append("  </speaker:Speaker>\n");

        // XMI-Dokument beenden
        xmi.append("</xmi:XMI>");

        System.out.println("XMI-Generierung abgeschlossen für " + filteredReden.size() + " Reden, Länge: " + xmi.length() + " Zeichen");

        return xmi.toString();
    }

    /**
     * Filtert gültige Reden aus einer Liste von Rede-Dokumenten.
     * Entfernt Reden ohne Textinhalt und wählt bei Duplikaten die Version
     * mit dem umfangreichsten Textinhalt.
     *
     * @param speeches Liste der zu filternden Rede-Dokumente
     * @return Gefilterte Liste mit gültigen, eindeutigen Reden
     * @author Maik Kitzmann
     */
    private List<Document> filterValidSpeeches(List<Document> speeches) {
        List<Document> validSpeeches = new ArrayList<>();

        // Erster Filter: Reden müssen textContent haben
        for (Document rede : speeches) {
            List<Document> textContent = (List<Document>) rede.get("textContent");
            if (textContent != null && !textContent.isEmpty()) {
                validSpeeches.add(rede);
            }
        }

        // Zweiter Filter: Wähle die Rede mit meisten textContent
        Map<String, Document> bestRedePerProtokoll = new HashMap<>();

        for (Document rede : validSpeeches) {
            Document protocol = (Document) rede.get("protocol");
            if (protocol == null || protocol.getString("index") == null) continue;

            String protocolIndex = protocol.getString("index");
            List<Document> textContent = (List<Document>) rede.get("textContent");
            int textSize = (textContent != null) ? textContent.size() : 0;

            Document bestRede = bestRedePerProtokoll.get(protocolIndex);
            if (bestRede == null) {
                bestRedePerProtokoll.put(protocolIndex, rede);
            } else {
                List<Document> bestTextContent = (List<Document>) bestRede.get("textContent");
                int bestSize = (bestTextContent != null) ? bestTextContent.size() : 0;

                if (textSize > bestSize) {
                    bestRedePerProtokoll.put(protocolIndex, rede);
                }
            }
        }

        return new ArrayList<>(bestRedePerProtokoll.values());
    }

    /**
     * Extrahiert den Textinhalt aus einem Rede-Dokument.
     * Kombiniert alle Textblöcke aus dem textContent-Array zu einem
     * zusammenhängenden String.
     *
     * @param speech Das Rede-Dokument, aus dem der Text extrahiert werden soll
     * @return Der extrahierte Redetext als String
     * @author Maik Kitzmann
     */
    private String extractSpeechText(Document speech) {
        String speechText = "";

        // Text aus dem textContent-Array holen
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
        }
        if (speechText == null) {
            speechText = "";
        }
        return speechText;
    }
}
