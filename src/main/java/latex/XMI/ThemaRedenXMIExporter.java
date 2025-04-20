package latex.XMI;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import java.util.*;

/**
 * Klasse zum Exportieren von Reden zu einem bestimmten Thema im XMI-Format.
 * Filtert Reden nach einem gegebenen Thema basierend auf NLP-Analyseergebnissen und
 * erstellt eine strukturierte XMI-Datei mit thematischen Gruppierungen und Annotationen.
 *
 * @author Maik Kitzmann
 */
public class ThemaRedenXMIExporter {
    private final String thema;
    private final List<Document> reden;
    private final MongoDatabaseHandler mongoHandler;
    private final List<Document> relevantReden;

    /**
     * Konstruktor für ThemaRedenXMIExporter.
     * Initialisiert den Exporter und filtert die übergebenen Reden nach Relevanz zum Thema.
     *
     * @param thema Das Thema, zu dem relevante Reden exportiert werden sollen
     * @param reden Liste aller zu durchsuchenden Reden (ungefiltert)
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public ThemaRedenXMIExporter(String thema, List<Document> reden, MongoDatabaseHandler mongoHandler) {
        this.thema = thema;
        this.reden = reden;
        this.mongoHandler = mongoHandler;

        // Zuerst Reden mit leerem textContent und Duplikate filtern
        List<Document> validReden = filterValidSpeeches(reden);
        System.out.println("Nach Basisfilterung: " + validReden.size() + " von " + reden.size() + " Reden behalten.");

        // Dann relevante Reden nach Thema filtern
        this.relevantReden = filterRelevantSpeeches(validReden);
    }

    /**
     * Filtert gültige Reden aus der übergebenen Liste.
     * Entfernt Reden ohne Textinhalt und wählt bei Duplikaten (Reden aus demselben Protokoll)
     * die Version mit dem umfangreichsten Textinhalt.
     *
     * @param speeches Liste der zu filternden Rede-Dokumente
     * @return Gefilterte Liste mit nur gültigen, eindeutigen Reden
     * @author Maik Kitzmann
     */
    private List<Document> filterValidSpeeches(List<Document> speeches) {
        List<Document> validSpeeches = new ArrayList<>();

        // Erster Filter: Reden müssen textContent haben wegen Duplikaten
        for (Document rede : speeches) {
            List<Document> textContent = (List<Document>) rede.get("textContent");
            if (textContent != null && !textContent.isEmpty()) {
                validSpeeches.add(rede);
            }
        }

        System.out.println("Reden mit Textinhalt: " + validSpeeches.size());

        // Zweiter Filter Wähle die Rede mit meisten textContent wegen Duplikaten
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

        System.out.println("Eindeutige Protokolle nach Filterung: " + bestRedePerProtokoll.size());

        return new ArrayList<>(bestRedePerProtokoll.values());
    }

    /**
     * Filtert Reden, die für das angegebene Thema relevant sind.
     * Analysiert das topics-Feld in nlpResults und wählt Reden aus,
     * bei denen das angegebene Thema als relevant erkannt wurde.
     *
     * @param validSpeeches Liste der validen Reden (bereits vorgefiltert)
     * @return Liste der Reden, die für das Thema relevant sind
     * @author Maik Kitzmann
     */
    private List<Document> filterRelevantSpeeches(List<Document> validSpeeches) {
        List<Document> thematicReden = new ArrayList<>();

        for (Document rede : validSpeeches) {
            if (isRelevantToTheme(rede, thema)) {
                thematicReden.add(rede);
            }
        }

        System.out.println("Found " + thematicReden.size() + " speeches relevant to theme \"" + thema + "\"");
        return thematicReden;
    }

    /**
     * Prüft, ob eine Rede für das angegebene Thema relevant ist.
     * Analysiert die NLP-Ergebnisse und prüft, ob das Thema als
     * eines der Hauptthemen der Rede identifiziert wurde.
     *
     * @param rede Das zu prüfende Rede-Dokument
     * @param thema Das Thema, auf das geprüft werden soll
     * @return true, wenn die Rede für das Thema relevant ist, sonst false
     * @author Maik Kitzmann
     */
    private boolean isRelevantToTheme(Document rede, String thema) {
        Document nlpResults = (Document) rede.get("nlpResults");
        if (nlpResults == null) {
            return false;
        }

        List<Document> topics = (List<Document>) nlpResults.get("topics");
        if (topics == null || topics.isEmpty()) {
            return false;
        }

        // Finde das Thema mit dem höchsten Score in Rede
        Document topTopic = topics.stream()
                .max(Comparator.comparingDouble(doc -> doc.getDouble("score") != null ? doc.getDouble("score") : 0.0))
                .orElse(null);

        if (topTopic == null) {
            return false;
        }

        String topValue = topTopic.getString("value");
        // Prüfe, ob der größte Topic-Wert mit dem Thema übereinstimmt
        return topValue != null && topValue.equalsIgnoreCase(thema);
    }

    /**
     * Generiert die XMI-Darstellung für alle themenbezogenen Reden.
     * Erstellt eine strukturierte XMI-Datei mit Themen-Informationen,
     * Reden gruppiert nach Rednern und NLP-Annotationen.
     *
     * @return Der generierte XMI-Inhalt als String
     * @author Maik Kitzmann
     */
    public String generateXMI() {
        System.out.println("Generating XMI for " + relevantReden.size() + " speeches related to theme: " + thema);

        if (relevantReden.isEmpty()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\"><error>No speeches found for theme: " +
                    LaTeXComponent.Utils.escapeXML(thema) + "</error></xmi:XMI>";
        }

        StringBuilder xmi = new StringBuilder();

        // XMI-Header
        xmi.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmi.append("<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\" ");
        xmi.append("xmlns:cas=\"http:///uima/cas.ecore\" ");
        xmi.append("xmlns:type=\"http:///de/tudarmstadt/ukp/dkpro/core/api/ner/type.ecore\" ");
        xmi.append("xmlns:topic=\"http:///de/parliament/topic.ecore\" ");
        xmi.append("xmlns:speech=\"http:///de/parliament/speech.ecore\">\n");

        xmi.append("  <topic:Topic xmi:id=\"_topic1\" name=\"" +
                LaTeXComponent.Utils.escapeXML(thema) + "\">\n");

        // Reden nach Redner gruppieren
        Map<String, List<Document>> redenBySpeaker = groupSpeechesBySpeaker(relevantReden);
        int sofaCounter = 1;
        int entityCounter = 1;

        // Reden nach Redner hinzufügen
        for (Map.Entry<String, List<Document>> entry : redenBySpeaker.entrySet()) {
            String speakerName = entry.getKey();
            List<Document> speakerReden = entry.getValue();

            if (!speakerName.equals("Unbekannter Redner")) {
                xmi.append("    <topic:SpeakerReference xmi:id=\"_speaker" + sofaCounter + "\" name=\"" +
                        LaTeXComponent.Utils.escapeXML(speakerName) + "\">\n");
            }

            // Jede Rede verarbeiten
            for (Document rede : speakerReden) {
                String redeText = extractSpeechText(rede);

                // Rede-Metadaten abrufen
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

                String party = rede.getString("party");
                if (party == null) party = "";

                // Basis für NLP hinzufügen (Sofa)
                xmi.append("      <cas:Sofa xmi:id=\"_sofa" + sofaCounter + "\" ");
                xmi.append("sofaNum=\"" + sofaCounter + "\" ");
                xmi.append("sofaID=\"_View" + sofaCounter + "\" ");
                xmi.append("sofaString=\"" + LaTeXComponent.Utils.escapeXML(redeText) + "\"/>\n");

                // View hinzufügen
                xmi.append("      <cas:View sofa=\"_sofa" + sofaCounter + "\" members=\"\"/>\n");

                // Rede-Informationen hinzufügen
                xmi.append("      <speech:Speech xmi:id=\"_speech" + sofaCounter + "\" ");
                xmi.append("sofa=\"_sofa" + sofaCounter + "\" ");
                xmi.append("speaker=\"" + LaTeXComponent.Utils.escapeXML(speakerName) + "\" ");

                if (!party.isEmpty()) {
                    xmi.append("party=\"" + LaTeXComponent.Utils.escapeXML(party) + "\" ");
                }

                xmi.append("protocol=\"" + LaTeXComponent.Utils.escapeXML(protocolIndex) + "\" ");
                xmi.append("protocolTitle=\"" + LaTeXComponent.Utils.escapeXML(protocolTitle) + "\" ");

                if (!protocolDate.isEmpty()) {
                    xmi.append("date=\"" + LaTeXComponent.Utils.escapeXML(protocolDate) + "\" ");
                }

                xmi.append("id=\"" + LaTeXComponent.Utils.escapeXML(rede.getObjectId("_id").toString()) + "\" ");
                xmi.append("topic=\"_topic1\"/>\n");

                // Topic aus nlpResults hinzufügen
                addTopicDetails(xmi, rede, sofaCounter);

                // NLP-Annotationen hinzufügen, wenn vorhanden
                Document nlpData = (Document) rede.get("nlpResults");
                if (nlpData != null) {
                    // Named Entities
                    List<Document> namedEntities = (List<Document>) nlpData.get("namedEntities");
                    if (namedEntities != null && !namedEntities.isEmpty()) {
                        System.out.println("    Processing " + namedEntities.size() + " Named Entities");
                        for (Document entity : namedEntities) {
                            Integer begin = entity.getInteger("begin");
                            Integer end = entity.getInteger("end");
                            String type = entity.getString("type");
                            String text_value = entity.getString("text");

                            if (begin != null && end != null && type != null && text_value != null) {
                                xmi.append("      <type:NamedEntity xmi:id=\"_entity" + entityCounter + "\" ");
                                xmi.append("sofa=\"_sofa" + sofaCounter + "\" ");
                                xmi.append("begin=\"" + begin + "\" ");
                                xmi.append("end=\"" + end + "\" ");
                                xmi.append("type=\"" + LaTeXComponent.Utils.escapeXML(type) + "\" ");
                                xmi.append("value=\"" + LaTeXComponent.Utils.escapeXML(text_value) + "\"/>\n");

                                entityCounter++;
                            }
                        }
                    }

                    // POS-Tags
                    List<Document> tokens = (List<Document>) nlpData.get("tokens");
                    if (tokens != null && !tokens.isEmpty()) {
                        System.out.println("    Processing " + tokens.size() + " Tokens for POS-Tags");
                        for (Document token : tokens) {
                            Integer begin = token.getInteger("begin");
                            Integer end = token.getInteger("end");
                            String posValue = token.getString("pos");
                            String text_value = token.getString("text");

                            if (begin != null && end != null && posValue != null && text_value != null) {
                                xmi.append("      <type:POS xmi:id=\"_pos" + entityCounter + "\" ");
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

            // Redner-Referenz schließen wenn nicht leer
            if (!speakerName.equals("Unbekannter Redner")) {
                xmi.append("    </topic:SpeakerReference>\n");
            }
        }

        // Thema-Tag beenden
        xmi.append("  </topic:Topic>\n");

        // Themenanalyse-Abschnitt hinzufügen
        addThemeAnalysis(xmi);

        xmi.append("</xmi:XMI>");

        System.out.println("XMI generation complete for theme " + thema + ", length: " + xmi.length() + " characters");

        return xmi.toString();
    }

    /**
     * Fügt Details zum Topic aus den NLP-Ergebnissen hinzu.
     * Extrahiert Topic-Metadaten wie Score-Werte und fügt sie
     * dem XMI als TopicMetadata-Elemente hinzu.
     *
     * @param xmi Der StringBuilder mit dem XMI-Inhalt
     * @param rede Das aktuelle Rede-Dokument
     * @param sofaCounter Der aktuelle Sofa-Zähler für die XMI-IDs
     * @author Maik Kitzmann
     */
    private void addTopicDetails(StringBuilder xmi, Document rede, int sofaCounter) {
        Document nlpResults = (Document) rede.get("nlpResults");
        if (nlpResults == null) {
            return;
        }

        List<Document> topics = (List<Document>) nlpResults.get("topics");
        if (topics == null || topics.isEmpty()) {
            return;
        }

        // Das Thema filtern, das dem Thema-Namen entspricht
        Optional<Document> matchingTopic = topics.stream()
                .filter(doc -> {
                    String value = doc.getString("value");
                    return value != null && value.equalsIgnoreCase(thema);
                })
                .findFirst();

        if (matchingTopic.isPresent()) {
            Document topic = matchingTopic.get();
            Double score = topic.getDouble("score");

            xmi.append("      <topic:TopicMetadata xmi:id=\"_topicmeta" + sofaCounter + "\" ");
            xmi.append("sofa=\"_sofa" + sofaCounter + "\" ");
            xmi.append("topic=\"_topic1\" ");

            if (score != null) {
                xmi.append("score=\"" + score + "\" ");
            }

            xmi.append("/>\n");
        }
    }

    /**
     * Fügt einen Themenanalyse-Abschnitt zum XMI hinzu.
     * Erstellt Statistiken über die Verteilung der Reden nach Parteien und Protokollen.
     *
     * @param xmi Der StringBuilder mit dem XMI-Inhalt
     * @author Maik Kitzmann
     */
    private void addThemeAnalysis(StringBuilder xmi) {
        xmi.append("  <topic:ThemeAnalysis xmi:id=\"_analysis1\" theme=\"_topic1\">\n");

        // Reden nach Partei zählen
        Map<String, Integer> speechesByParty = new HashMap<>();

        for (Document rede : relevantReden) {
            String party = rede.getString("party");
            if (party == null || party.isEmpty()) {
                party = "Unknown";
            }

            speechesByParty.put(party, speechesByParty.getOrDefault(party, 0) + 1);
        }

        // Partei-Statistiken hinzufügen
        for (Map.Entry<String, Integer> entry : speechesByParty.entrySet()) {
            xmi.append("    <topic:PartyStatistic xmi:id=\"_partystat" + entry.getKey().hashCode() + "\" ");
            xmi.append("party=\"" + LaTeXComponent.Utils.escapeXML(entry.getKey()) + "\" ");
            xmi.append("count=\"" + entry.getValue() + "\"/>\n");
        }

        // Reden nach Protokoll zählen
        Map<String, Integer> speechesByProtocol = new HashMap<>();

        for (Document rede : relevantReden) {
            Document protocol = (Document) rede.get("protocol");
            if (protocol == null || protocol.getString("index") == null) {
                continue;
            }

            String protocolId = protocol.getString("index");
            speechesByProtocol.put(protocolId, speechesByProtocol.getOrDefault(protocolId, 0) + 1);
        }

        // Protokoll-Statistiken hinzufügen
        for (Map.Entry<String, Integer> entry : speechesByProtocol.entrySet()) {
            xmi.append("    <topic:ProtocolStatistic xmi:id=\"_protocolstat" + entry.getKey().hashCode() + "\" ");
            xmi.append("protocol=\"" + LaTeXComponent.Utils.escapeXML(entry.getKey()) + "\" ");
            xmi.append("count=\"" + entry.getValue() + "\"/>\n");
        }

        xmi.append("  </topic:ThemeAnalysis>\n");
    }

    /**
     * Gruppiert Reden nach ihren Rednern.
     * Erstellt eine Map mit Rednernamen als Schlüssel und Listen von Reden als Werte.
     *
     * @param speeches Die zu gruppierenden Reden
     * @return Map mit Reden gruppiert nach Rednernamen
     * @author Maik Kitzmann
     */
    private Map<String, List<Document>> groupSpeechesBySpeaker(List<Document> speeches) {
        Map<String, List<Document>> redenBySpeaker = new HashMap<>();

        for (Document rede : speeches) {
            String speaker = rede.getString("speaker");
            if (speaker == null || speaker.isEmpty()) {
                speaker = "Unbekannter Redner";
            }

            if (!redenBySpeaker.containsKey(speaker)) {
                redenBySpeaker.put(speaker, new ArrayList<>());
            }

            redenBySpeaker.get(speaker).add(rede);
        }

        return redenBySpeaker;
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
