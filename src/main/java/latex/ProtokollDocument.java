package latex;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import java.nio.file.Path;
import java.util.*;

/**
 * Klasse für die Darstellung eines einzelnen Parlamentsprotokolls als LaTeX-Dokument.
 * Formatiert ein Protokoll mit Titelseite, Tagesordnung und allen zugehörigen Reden
 * in einem strukturierten Format für den PDF-Export.
 *
 * @author Maik Kitzmann
 */
public class ProtokollDocument extends LaTeXDocument {
    private final String titel;
    private final String datum;
    private final List<Document> reden;
    private final Path exportDir;
    private final MongoDatabaseHandler mongoHandler;

    /**
     * Erstellt ein neues ProtokollDocument mit den angegebenen Parametern.
     * Organisiert automatisch die Reden und fügt sie dem Dokument hinzu.
     *
     * @param titel Der Titel des Protokolls
     * @param datum Das Datum des Protokolls
     * @param reden Liste der zugehörigen Reden als MongoDB-Dokumente
     * @param exportDir Das Verzeichnis für exportierte Ressourcen (z.B. Bilder)
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public ProtokollDocument(String titel, String datum, List<Document> reden, Path exportDir, MongoDatabaseHandler mongoHandler) {
        super();
        this.titel = titel;
        this.datum = datum;
        this.reden = reden;
        this.exportDir = exportDir;
        this.mongoHandler = mongoHandler;

        organizeAndAddSpeeches();
    }

    /**
     * Generiert den Titelabschnitt für das Protokolldokument.
     * Erstellt eine Titelseite mit dem Protokolltitel, "Deutscher Bundestag" als Autor
     * und dem Datum des Protokolls.
     *
     * @return Der formatierte Titelabschnitt als LaTeX-Code
     * @author Maik Kitzmann
     */
    @Override
    protected String generateTitleSection() {
        // Titelseite für das Protokoll erstellen
        StringBuilder titleSection = new StringBuilder();
        titleSection.append("\\title{\\LARGE " + LaTeXComponent.Utils.escapeTeX(titel) + "}\n");
        titleSection.append("\\author{Deutscher Bundestag}\n");
        titleSection.append("\\date{" + LaTeXComponent.Utils.escapeTeX(datum) + "}\n");
        titleSection.append("\\maketitle");
        return titleSection.toString();
    }

    /**
     * Organisiert und fügt die Reden zum Dokument hinzu.
     * Extrahiert Tagesordnungspunkte, fügt einen Tagesordnungsabschnitt hinzu
     * und formatiert jede Rede als separaten Unterabschnitt.
     *
     * @author Maik Kitzmann
     */
    private void organizeAndAddSpeeches() {
        // Tagesordnungspunkte aus Reden
        Set<String> agendaItems = extractAgendaItems();

        // Tagesordnungsabschnitt hinzufügen
        addToBody(() -> {
            StringBuilder latex = new StringBuilder();
            latex.append("\\section{Tagesordnung}\n");

            if (!agendaItems.isEmpty()) {
                latex.append("\\begin{itemize}\n");
                for (String item : agendaItems) {
                    latex.append("  \\item " + LaTeXComponent.Utils.escapeTeX(item) + "\n");
                }
                latex.append("\\end{itemize}\n");
            } else {
                latex.append("Keine Tagesordnung verfügbar.\n");
            }

            return latex.toString();
        });

        // Redenabschnitt hinzufügen
        addToBody(() -> "\\section{Reden}\n");

        // Reden nach Sprechern sortieren
        reden.sort(Comparator.comparing(doc -> doc.getString("speaker")));

        // Jede Rede als Unterabschnitt hinzufügen
        for (Document rede : reden) {
            String rednerName = rede.getString("speaker");

            // SpeechComponent mit showAgendaItems=false weil wir haben gleiche agenda für ganze Protokoll
            SpeechComponent speechComponent = new SpeechComponent(rede, exportDir, mongoHandler, false);

            // Unterabschnitt mit Rede hinzufügen
            addToBody(() -> "\\subsection{Rede von " + LaTeXComponent.Utils.escapeTeX(rednerName) + "}\n\n" +
                    speechComponent.toTex() +
                    "\\newpage\n");
        }
    }

    /**
     * Sammelt alle Tagesordnungspunkte aus dem Protokoll.
     * Extrahiert die Tagesordnungspunkte aus der ersten Rede, da in einem Protokoll
     * alle Reden dieselben Tagesordnungspunkte haben.
     *
     * @return Set mit allen einzigartigen Tagesordnungspunkten
     * @author Maik Kitzmann
     */
    private Set<String> extractAgendaItems() {
        // In einem Protokoll haben alle Reden dieselben Tagesordnungspunkte also nehmen von erster Rede
        Set<String> agendaItems = new HashSet<>();

        if (!reden.isEmpty()) {
            Document ersteRede = reden.get(0);
            List<Object> agenda = (List<Object>) ersteRede.get("agenda");
            if (agenda != null) {
                for (Object item : agenda) {
                    if (item instanceof Document) {
                        String title = ((Document) item).getString("title");
                        if (title != null && !title.isEmpty()) {
                            agendaItems.add(title);
                        }
                    } else if (item instanceof String) {
                        agendaItems.add((String) item);
                    }
                }
            }
        }

        return agendaItems;
    }
}
