package latex;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import java.nio.file.Path;
import java.util.*;

/**
 * Klasse für die thematische Zusammenstellung von Reden zu einem bestimmten Thema.
 * Erstellt ein LaTeX-Dokument mit allen Reden zu einem spezifischen Thema,
 * gruppiert nach Rednern und mit statistischer Auswertung nach Fraktionen.
 *
 * @author Maik Kitzmann
 */
public class ThemaRedenDocument extends LaTeXDocument {
    private final String thema;
    private final List<Document> reden;
    private final Path exportDir;
    private final MongoDatabaseHandler mongoHandler;
    private final List<Document> filteredReden;

    /**
     * Erstellt ein neues ThemaRedenDocument für ein bestimmtes Thema.
     * Filtert die übergebenen Reden, um Duplikate zu entfernen und
     * initialisiert das Dokument mit den relevanten Reden.
     *
     * @param thema Das Thema, zu dem Reden zusammengestellt werden sollen
     * @param reden Liste aller Reden zu diesem Thema (ungefiltert)
     * @param exportDir Verzeichnis für exportierte Ressourcen (z.B. Bilder)
     * @param mongoHandler Handler für den Datenbankzugriff
     * @author Maik Kitzmann
     */
    public ThemaRedenDocument(String thema, List<Document> reden, Path exportDir, MongoDatabaseHandler mongoHandler) {
        super();
        this.thema = thema;
        this.reden = reden;
        this.exportDir = exportDir;
        this.mongoHandler = mongoHandler;

        // Filtere die Reden, um nur gültige zu behalten
        this.filteredReden = filterValidSpeeches(reden);
        System.out.println("Nach Filterung: " + filteredReden.size() + " von " + reden.size() + " Reden behalten.");

        // Dokument initialisieren
        initializeDocument();
    }

    /**
     * Generiert den Titelabschnitt für das Themendokument.
     * Fügt das Thema in den Titel ein und setzt den Deutschen Bundestag als Autor.
     *
     * @return Der formatierte Titelabschnitt als LaTeX-Code
     * @author Maik Kitzmann
     */
    @Override
    protected String generateTitleSection() {
        StringBuilder titleSection = new StringBuilder();
        titleSection.append("\\title{\\LARGE Reden zum Thema: " + LaTeXComponent.Utils.escapeTeX(thema) + "}\n");
        titleSection.append("\\author{Deutscher Bundestag}\n");
        titleSection.append("\\date{\\today}\n");
        titleSection.append("\\maketitle");

        return titleSection.toString();
    }

    /**
     * Filtert die Reden, um Duplikate zu entfernen und nur gültige Reden zu behalten.
     * Entfernt Reden ohne Textinhalt und wählt bei mehreren Reden desselben Redners
     * in demselben Protokoll die Version mit dem meisten Textinhalt.
     *
     * @param speeches Liste aller Reden zum Thema
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

        // Zweiter Filter: Wähle die Rede mit meisten textContent wegen Duplikaten
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
     * Initialisiert das Dokument mit allen Reden zum Thema.
     * Gruppiert die Reden nach Sprechern, fügt sie dem Dokument hinzu
     * und erstellt eine thematische Analyse am Ende.
     *
     * @author Maik Kitzmann
     */
    private void initializeDocument() {
        // Gruppiere die Reden nach Sprechern für bessere Übersicht
        Map<String, List<Document>> redenByRedner = groupSpeechesBySpeaker();

        // Reden nach Redner gruppiert ausgeben
        for (Map.Entry<String, List<Document>> entry : redenByRedner.entrySet()) {
            String rednerName = entry.getKey();
            List<Document> rednerReden = entry.getValue();

            final String finalRednerName = rednerName;
            addToBody(() -> {
                StringBuilder latex = new StringBuilder();
                latex.append("\\section{Reden von " + LaTeXComponent.Utils.escapeTeX(finalRednerName) + "}\n\n");

                // Partei aus der ersten Rede ermitteln, falls vorhanden
                if (!rednerReden.isEmpty()) {
                    String partei = rednerReden.get(0).getString("party");
                    if (partei != null && !partei.isEmpty()) {
                        latex.append("\\textbf{Fraktion:} " + LaTeXComponent.Utils.escapeTeX(partei) + "\n\n");
                    }
                }

                return latex.toString();
            });

            // Alle Reden dieses Redners ausgeben
            for (Document rede : rednerReden) {
                Document protokollInfo = (Document) rede.get("protocol");
                String protokollTitle = "Unbekanntes Protokoll";
                String datum = "Unbekanntes Datum";

                if (protokollInfo != null) {
                    String title = protokollInfo.getString("title");
                    String date = protokollInfo.getString("date");

                    if (title != null && !title.isEmpty()) {
                        protokollTitle = title;
                    }

                    if (date != null && !date.isEmpty()) {
                        datum = date;
                    }
                }

                final String finalProtokollTitle = protokollTitle;
                final String finalDatum = datum;
                final Document finalRede = rede;

                addToBody(() -> {
                    StringBuilder latex = new StringBuilder();
                    latex.append("\\subsection{Rede aus " + LaTeXComponent.Utils.escapeTeX(finalProtokollTitle) +
                            " vom " + LaTeXComponent.Utils.escapeTeX(finalDatum) + "}\n\n");

                    // SpeechComponent mit Tagesordnungspunkten verwenden
                    SpeechComponent speechComponent = new SpeechComponent(finalRede, exportDir, mongoHandler, true);
                    latex.append(speechComponent.toTex());

                    latex.append("\\newpage\n\n");
                    return latex.toString();
                });
            }
        }

        // Themenanalyse hinzufügen für Statistik
        addThemeAnalysis();
    }

    /**
     * Gruppiert die gefilterten Reden nach Sprechern.
     * Erstellt eine Map mit Rednernamen als Schlüssel und Listen von Reden als Werte.
     *
     * @return Map mit nach Rednern gruppierten Reden
     * @author Maik Kitzmann
     */
    private Map<String, List<Document>> groupSpeechesBySpeaker() {
        Map<String, List<Document>> redenByRedner = new HashMap<>();

        for (Document rede : filteredReden) {  // Verwende gefilterte Reden statt aller Reden
            String redner = rede.getString("speaker");
            if (redner == null || redner.isEmpty()) {
                redner = "Unbekannter Redner";
            }

            // Jedem Redner seine Reden zuordnen
            redenByRedner.computeIfAbsent(redner, k -> new ArrayList<>()).add(rede);
        }

        return redenByRedner;
    }

    /**
     * Fügt eine statistische Analyse der Redebeiträge zum Thema hinzu.
     * Erstellt eine Visualisierung der Häufigkeit des Themas nach Fraktionen
     * als Balkendiagramm mit TikZ.
     *
     * @author Maik Kitzmann
     */
    private void addThemeAnalysis() {
        addToBody(() -> {
            StringBuilder latex = new StringBuilder();

            latex.append("\\section{Themenanalyse}\n\n");
            latex.append("\\subsection{Häufigkeit des Themas nach Fraktionen}\n\n");

            // Reden nach Fraktionen gruppieren
            Map<String, Integer> redenProFraktion = new HashMap<>();
            for (Document rede : filteredReden) {  // Verwende gefilterte Reden statt aller Reden
                String fraktion = rede.getString("party");
                if (fraktion != null && !fraktion.isEmpty()) {
                    redenProFraktion.put(fraktion, redenProFraktion.getOrDefault(fraktion, 0) + 1);
                }
            }

            // Wenn weniger als 2 Fraktionen vorhanden sind, Text statt Visualisierung verwenden
            if (redenProFraktion.size() < 2) {
                if (redenProFraktion.isEmpty()) {
                    latex.append("Keine Fraktionsinformationen für die Reden verfügbar.\n\n");
                } else {
                    // Nur eine Fraktion vorhanden
                    Map.Entry<String, Integer> entry = redenProFraktion.entrySet().iterator().next();
                    latex.append("Alle Reden zum Thema stammen von der Fraktion: " +
                            LaTeXComponent.Utils.escapeTeX(entry.getKey()) + " (" + entry.getValue() + " Reden)\n\n");
                }
                return latex.toString();
            }

            latex.append("\\begin{center}\n"); // Zentrierung beginnen

            // Visualisierung als TikZ-Grafik
            latex.append("\\begin{tikzpicture}\n");
            latex.append("\\begin{axis}[\n");
            latex.append("    title={Redebeiträge zum Thema '" + LaTeXComponent.Utils.escapeTeX(thema) + "' nach Fraktion},\n");
            latex.append("    xlabel={Fraktion},\n");
            latex.append("    ylabel={Anzahl},\n");
            latex.append("    ybar,\n");
            latex.append("    bar width=1cm,\n");
            latex.append("    xtick=data,\n");
            latex.append("    nodes near coords,\n");
            latex.append("    nodes near coords align={vertical},\n");
            latex.append("    ymin=0,\n");
            latex.append("    width=12cm,\n");
            latex.append("    height=8cm,\n");
            latex.append("]\n");

            // Plot mit escapten Labels generieren
            latex.append("\\addplot coordinates {");
            for (Map.Entry<String, Integer> entry : redenProFraktion.entrySet()) {
                latex.append("(" + LaTeXComponent.Utils.escapeTikzLabel(entry.getKey()) + "," + entry.getValue() + ") ");
            }
            latex.append("};\n");

            latex.append("\\end{axis}\n");
            latex.append("\\end{tikzpicture}\n");
            latex.append("\\end{center}\n\n"); // Zentrierung beenden

            return latex.toString();
        });
    }
}
