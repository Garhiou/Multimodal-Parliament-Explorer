package latex;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;

import java.nio.file.Path;
import java.util.*;

/**
 * Komponente zur Darstellung einer einzelnen Rede mit allen zugehörigen Daten.
 * Formatiert eine Parlamentsrede mit Rednerinformationen, Tagesordnungspunkten,
 * dem Redetext und der NLP-Analyse. Hebt Kommentare im Text hervor und sorgt
 * für eine strukturierte Darstellung der Inhalte.
 *
 * @author Maik Kitzmann
 */
public class SpeechComponent implements LaTeXComponent {
    private final Document rede;
    private final Path exportDir;
    private final MongoDatabaseHandler mongoHandler;
    private final boolean showAgendaItems;

    /**
     * Erstellt eine neue SpeechComponent für eine Rede.
     *
     * @param rede Das MongoDB-Dokument mit den Daten der Rede
     * @param exportDir Das Verzeichnis für exportierte Ressourcen (z.B. Bilder)
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @param showAgendaItems Flag, ob Tagesordnungspunkte angezeigt werden sollen
     * @author Maik Kitzmann
     */
    public SpeechComponent(Document rede, Path exportDir, MongoDatabaseHandler mongoHandler, boolean showAgendaItems) {
        this.rede = rede;
        this.exportDir = exportDir;
        this.mongoHandler = mongoHandler;
        this.showAgendaItems = showAgendaItems;
    }

    /**
     * Generiert LaTeX-Code für die Darstellung der Rede.
     * Fügt Rednerinformationen, Datum, ggf. Tagesordnungspunkte,
     * den Redetext mit Formatierung und die NLP-Analyse der Rede ein.
     *
     * @return Der formatierte LaTeX-Code für die Rede
     * @author Maik Kitzmann
     */
    @Override
    public String toTex() {
        StringBuilder latex = new StringBuilder();

        String redeId = rede.getString("_id");
        String rednerName = rede.getString("speaker");
        String partei = rede.getString("party");
        Document protokollInfo = (Document) rede.get("protocol");
        String datum = "Unbekanntes Datum";

        if (protokollInfo != null) {
            String date = protokollInfo.getString("date");
            if (date != null && !date.isEmpty()) {
                datum = date;
            }
        }

        System.out.println("    Generiere LaTeX für Rede von " + rednerName + " (" + partei + ")");

        // Infos zum Abgeordneten einfügen
        AbgeordnetenInfoComponent abgeordnetenInfo = new AbgeordnetenInfoComponent(rednerName, exportDir, mongoHandler);
        latex.append(abgeordnetenInfo.toTex());

        latex.append("\\vspace{1.0cm}\n");
        latex.append("\\textbf{Datum der Rede:} " + LaTeXComponent.Utils.escapeTeX(datum) + "\n\n");
        latex.append("\\vspace{1.0cm}\n");

        // Tagesordnungspunkte anzeigen wenn true
        if (showAgendaItems) {
            Object agendaObj = rede.get("agenda");
            if (agendaObj instanceof List && !((List<?>) agendaObj).isEmpty()) {
                List<?> agendaList = (List<?>) agendaObj;
                List<Document> filteredAgendaItems = new ArrayList<>();

                for (Object item : agendaList) {
                    if (item instanceof Document) {
                        Document agendaItem = (Document) item;
                        String index = agendaItem.getString("index");
                        if (index != null &&
                                (index.startsWith("Tagesordnungspunkt") || index.startsWith("Anlage"))) {
                            filteredAgendaItems.add(agendaItem); //Wegen DB Duplikate in agenda versucht zu Filtern
                        }
                    }
                }

                if (!filteredAgendaItems.isEmpty()) {
                    latex.append("\\textbf{Tagesordnungspunkte:}\n");
                    latex.append("\\begin{itemize}\n");

                    for (Document agendaItem : filteredAgendaItems) {
                        String index = agendaItem.getString("index");
                        String title = agendaItem.getString("title");

                        if (title != null && !title.isEmpty()) {
                            latex.append("  \\item ");

                            if (index != null && !index.isEmpty()) {
                                latex.append("\\textbf{" + LaTeXComponent.Utils.escapeTeX(index) + "} ");
                            }

                            latex.append(LaTeXComponent.Utils.escapeTeX(title) + "\n");
                        }
                    }

                    latex.append("\\end{itemize}\n\n");
                    latex.append("\\vspace{0.5cm}\n");
                }
            }
        }

        // Redetext einfügen
        latex.append("\\textbf{\\large Redetext:}\n\n");
        latex.append("\\vspace{0.5cm}\n");
        latex.append("\\begin{spacing}{1.2}\n");

        List<Document> textContent = (List<Document>) rede.get("textContent");
        if (textContent != null && !textContent.isEmpty()) {
            System.out.println("      Verarbeite " + textContent.size() + " Textblöcke");

            // Text verarbeiten und formatieren
            StringBuilder paragraph = new StringBuilder();
            int commentCount = 0;

            for (Document block : textContent) {
                String text = block.getString("text");
                String type = block.getString("type");

                if (text == null || text.trim().isEmpty()) {
                    continue;
                }

                // Kommentare hervorheben
                if (type != null && (type.equalsIgnoreCase("comment"))) {
                    commentCount++;
                    if (paragraph.length() > 0) {
                        latex.append(LaTeXComponent.Utils.escapeTeX(paragraph.toString().trim())).append("\n\n");
                        paragraph = new StringBuilder();
                    }

                    latex.append("\\begin{quote}\n");
                    latex.append("\\textcolor{gray}{\\textit{");
                    latex.append(LaTeXComponent.Utils.escapeTeX(text.trim()));
                    latex.append("}}\n");
                    latex.append("\\end{quote}\n\n");
                } else {
                    // Normalen Text zum Absatz hinzufügen
                    if (paragraph.length() > 0 &&
                            !text.startsWith("(") && !text.startsWith("[") &&
                            !text.startsWith(",") && !text.startsWith(".")) {
                        paragraph.append(" ");
                    }
                    paragraph.append(text.trim());

                    // Bei Satzende Absatz ausgeben
                    if (text.trim().endsWith(".") || text.trim().endsWith("!") || text.trim().endsWith("?")) {
                        latex.append(LaTeXComponent.Utils.escapeTeX(paragraph.toString().trim())).append("\n\n");
                        paragraph = new StringBuilder();
                    }
                }
            }

            // Letzten Absatz auch wenn kein Satzende (.!?) hat
            if (paragraph.length() > 0) {
                latex.append(LaTeXComponent.Utils.escapeTeX(paragraph.toString().trim())).append("\n\n");
            }

            System.out.println("      " + commentCount + " Kommentare gefunden und verarbeitet");
        } else {
            // Fallback auf plaintext wenn keine strukturierten Daten vorhanden
            System.out.println("      Kein strukturierter Inhalt gefunden, verwende Plaintext");
            String plainText = rede.getString("text");
            if (plainText != null && !plainText.isEmpty()) {
                String[] paragraphs = plainText.split("\n");
                for (String p : paragraphs) {
                    if (!p.trim().isEmpty()) {
                        latex.append(LaTeXComponent.Utils.escapeTeX(p.trim())).append("\n\n");
                    }
                }
            } else {
                latex.append("\\textit{Kein Text verfügbar}\n\n");
            }
        }

        latex.append("\\end{spacing}\n\n");

        // NLP-Analyse einfügen
        latex.append("\\vspace{1.5cm}\n\n");
        latex.append("\\textbf{\\Large Sprachanalyse}\n\n");
        latex.append("\\vspace{1.0cm}\n\n");

        Document nlpResults = (Document) rede.get("nlpResults");
        if (nlpResults != null) {
            SpeechNLPComponent nlpComponent = new SpeechNLPComponent(nlpResults);
            latex.append(nlpComponent.toTex());
        }

        return latex.toString();
    }
}
