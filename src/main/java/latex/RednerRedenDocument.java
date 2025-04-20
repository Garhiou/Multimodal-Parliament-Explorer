package latex;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import java.nio.file.Path;
import java.util.*;

/**
 * Klasse für die Zusammenstellung aller Reden eines bestimmten Redners/Abgeordneten.
 * Erstellt ein strukturiertes LaTeX-Dokument mit Informationen zum Redner und
 * seinen Reden, gruppiert nach Protokollen.
 *
 * @author Maik Kitzmann
 */
public class RednerRedenDocument extends LaTeXDocument {
    private final Document redner;
    private final List<Document> reden;
    private final Path exportDir;
    private final MongoDatabaseHandler mongoHandler;

    /**
     * Erstellt ein neues RednerRedenDocument mit den angegebenen Parametern.
     * Initialisiert das Dokument automatisch mit Informationen zum Redner
     * und seinen Reden.
     *
     * @param redner MongoDB-Dokument mit den Informationen zum Redner
     * @param reden Liste der Reden des Redners als MongoDB-Dokumente
     * @param exportDir Verzeichnis für exportierte Ressourcen (z.B. Bilder)
     * @param mongoHandler Handler für den Datenbankzugriff
     * @author Maik Kitzmann
     */
    public RednerRedenDocument(Document redner, List<Document> reden, Path exportDir, MongoDatabaseHandler mongoHandler) {
        super();
        this.redner = redner;
        this.reden = reden;
        this.exportDir = exportDir;
        this.mongoHandler = mongoHandler;

        initializeDocument();
    }

    /**
     * Generiert den Titelabschnitt für das Dokument.
     * Fügt Redner-Namen, optional die Partei/Fraktion und das aktuelle Datum ein.
     *
     * @return Der formatierte Titelabschnitt als LaTeX-Code
     * @author Maik Kitzmann
     */
    @Override
    protected String generateTitleSection() {
        String rednerName = redner.getString("name");
        String partei = redner.getString("party");

        // Titelseite für das Dokument erzeugen
        StringBuilder titleSection = new StringBuilder();
        titleSection.append("\\title{\\LARGE Reden von " + LaTeXComponent.Utils.escapeTeX(rednerName));
        if (partei != null && !partei.isEmpty()) {
            titleSection.append(" (" + LaTeXComponent.Utils.escapeTeX(partei) + ")");
        }
        titleSection.append("}\n");
        titleSection.append("\\author{Deutscher Bundestag}\n");
        titleSection.append("\\date{\\today}\n");
        titleSection.append("\\maketitle");

        return titleSection.toString();
    }

    /**
     * Initialisiert das Dokument mit allen benötigten Inhalten.
     * Fügt Rednerinformationen hinzu, filtert und gruppiert die Reden und
     * erstellt eine strukturierte Darstellung nach Protokollen.
     *
     * @author Maik Kitzmann
     */
    private void initializeDocument() {
        String rednerName = redner.getString("name");

        // Infos zum Redner mit Bild einfügen
        addSpeakerInfo();

        // Nur Reden mit textContent
        List<Document> filteredReden = filterValidSpeeches(reden);

        // Reden nach Protokollen gruppieren für bessere Strukturierung
        Map<String, List<Document>> redenByProtokoll = groupSpeechesByProtocol(filteredReden);

        // Jeden Protokollabschnitt durchgehen und Reden einfügen
        for (Map.Entry<String, List<Document>> entry : redenByProtokoll.entrySet()) {
            String protokollTitle = entry.getKey();
            List<Document> protokollReden = entry.getValue();

            final String finalProtokollTitle = protokollTitle;
            addToBody(() -> "\\section{" + LaTeXComponent.Utils.escapeTeX(finalProtokollTitle) + "}\n\n");

            // Jede Rede aus dem Protokoll einfügen
            for (Document rede : protokollReden) {
                Document protokollInfo = (Document) rede.get("protocol");
                String datum = "Unbekanntes Datum";

                if (protokollInfo != null) {
                    String date = protokollInfo.getString("date");
                    if (date != null && !date.isEmpty()) {
                        datum = date;
                    }
                }

                final String finalDatum = datum;
                final Document finalRede = rede;

                addToBody(() -> {
                    StringBuilder latex = new StringBuilder();
                    latex.append("\\subsection{Rede vom " + LaTeXComponent.Utils.escapeTeX(finalDatum) + "}\n\n");

                    // SpeechComponent für die einzelne Rede verwenden
                    SpeechComponent speechComponent = new SpeechComponent(finalRede, exportDir, mongoHandler, true);
                    latex.append(speechComponent.toTex());

                    latex.append("\\newpage\n\n");
                    return latex.toString();
                });
            }
        }
    }

    /**
     * Fügt Informationen zum Redner in das Dokument ein.
     * Versucht, ein Bild des Redners aus der Datenbank zu laden und einzufügen.
     *
     * @author Maik Kitzmann
     */
    private void addSpeakerInfo() {
        String bildId = redner.getString("bildId");

        addToBody(() -> {
            StringBuilder latex = new StringBuilder();

            // Wenn ein Bild verfügbar ist, dieses einfügen
            if (bildId != null && !bildId.isEmpty()) {
                Document bild = mongoHandler.findDocuments("bilder", new Document("_id", bildId)).first();
                if (bild != null) {
                    String bildUrl = bild.getString("url");
                    if (bildUrl != null && !bildUrl.isEmpty()) {
                        latex.append("\\begin{figure}[h]\n");
                        latex.append("  \\centering\n");
                        latex.append("  \\fbox{\\includegraphics[width=5cm]{" + LaTeXComponent.Utils.escapeTeX(bildUrl) + "}}\n");
                        latex.append("  \\caption{" + LaTeXComponent.Utils.escapeTeX(redner.getString("name")) + "}\n");
                        latex.append("\\end{figure}\n\n");
                    }
                }
            }

            return latex.toString();
        });
    }

    /**
     * Filtert Reden ohne Textinhalt heraus und entfernt Duplikate.
     * Bei mehreren Reden desselben Redners im selben Protokoll wird
     * die Version mit dem längsten Textinhalt beibehalten.
     *
     * @param speeches Liste aller Reden des Redners
     * @return Gefilterte Liste mit den besten Versionen jeder Rede
     * @author Maik Kitzmann
     */
    private List<Document> filterValidSpeeches(List<Document> speeches) {
        List<Document> validSpeeches = new ArrayList<>();

        // Nur Reden mit textContent behalten
        for (Document rede : speeches) {
            List<Document> textContent = (List<Document>) rede.get("textContent");
            if (textContent != null && !textContent.isEmpty()) {
                validSpeeches.add(rede);
            }
        }

        // Bei Duplikaten die vollständigste Version behalten
        Map<String, Document> bestRedePerProtokoll = new HashMap<>();

        for (Document rede : validSpeeches) {
            Document protocol = (Document) rede.get("protocol");
            if (protocol == null || protocol.getString("index") == null) continue;

            String protocolIndex = protocol.getString("index");
            List<Document> textContent = (List<Document>) rede.get("textContent");
            int textSize = (textContent != null) ? textContent.size() : 0;

            // Prüfen, ob für dieses Protokoll schon eine Rede existiert
            Document bestRede = bestRedePerProtokoll.get(protocolIndex);
            if (bestRede == null) {
                bestRedePerProtokoll.put(protocolIndex, rede);
            } else {
                // Wenn ja, dann die längere Rede nehmen
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
     * Gruppiert die Reden nach ihren Protokolltiteln.
     * Erstellt eine Map, in der Reden nach Protokollen geordnet sind,
     * um eine bessere Dokumentstruktur zu erzeugen.
     *
     * @param filteredReden Liste der bereits gefilterten Reden
     * @return Map mit Protokolltiteln als Schlüssel und Listen von Reden als Werte
     * @author Maik Kitzmann
     */
    private Map<String, List<Document>> groupSpeechesByProtocol(List<Document> filteredReden) {
        Map<String, List<Document>> redenByProtokoll = new HashMap<>();

        for (Document rede : filteredReden) {
            Document protokollInfo = (Document) rede.get("protocol");
            String protokollTitle = "Unbekanntes Protokoll";

            if (protokollInfo != null) {
                String title = protokollInfo.getString("title");
                if (title != null && !title.isEmpty()) {
                    protokollTitle = title;
                } else {
                    // Fallback auf Protokoll-Index wenn kein Titel
                    String index = protokollInfo.getString("index");
                    if (index != null && !index.isEmpty()) {
                        protokollTitle = "Protokoll " + index;
                    }
                }
            }

            // Rede zur entsprechenden Protokollgruppe hinzufügen
            redenByProtokoll.computeIfAbsent(protokollTitle, k -> new ArrayList<>()).add(rede);
        }

        return redenByProtokoll;
    }
}
