package latex;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import com.mongodb.client.FindIterable;

import java.util.*;

/**
 * Klasse für den Export mehrerer Protokolldokumente als ein kombiniertes LaTeX-Dokument.
 * Ermöglicht die Zusammenstellung von verschiedenen Parlamentsprotokollen in einer
 * strukturierten Dokumentsammlung. Kann entweder mit einer spezifischen Liste von Protokoll-IDs
 * oder für alle verfügbaren Protokolle verwendet werden.
 *
 * @author Maik Kitzmann
 */
public class MultiProtokollDocument extends LaTeXDocument {
    private final List<String> protokollIds;
    private final MongoDatabaseHandler mongoHandler;

    /**
     * Erstellt ein neues MultiProtokollDocument mit den angegebenen Protokoll-IDs.
     * Verarbeitet automatisch alle angegebenen Protokolle und fügt sie dem Dokument hinzu.
     *
     * @param protokollIds Liste der zu exportierenden Protokoll-IDs
     * @param mongoHandler Handler für den Zugriff auf die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public MultiProtokollDocument(List<String> protokollIds, MongoDatabaseHandler mongoHandler) {
        super();
        this.protokollIds = protokollIds;
        this.mongoHandler = mongoHandler;

        // Alle Protokolle verarbeiten und zum Dokument hinzufügen
        processProtocols();
    }

    /**
     * Generiert den Titelabschnitt für die Protokollsammlung.
     * Enthält Titel, Autor (Deutscher Bundestag) und das aktuelle Datum.
     *
     * @return Der formatierte Titelabschnitt als LaTeX-Code
     * @author Maik Kitzmann
     */
    @Override
    protected String generateTitleSection() {
        StringBuilder titleSection = new StringBuilder();
        titleSection.append("\\title{\\LARGE Protokollsammlung Deutscher Bundestag}\n");
        titleSection.append("\\author{Deutscher Bundestag}\n");
        titleSection.append("\\date{\\today}\n");
        titleSection.append("\\maketitle");
        return titleSection.toString();
    }

    /**
     * Verarbeitet alle Protokolle in der protokollIds-Liste.
     * Sortiert die Protokolle nach ihrer numerischen ID und
     * verarbeitet jedes Protokoll einzeln.
     *
     * @author Maik Kitzmann
     */
    private void processProtocols() {
        // Protokoll-IDs nach Index sortieren
        protokollIds.sort((id1, id2) -> {
            try {
                int index1 = Integer.parseInt(id1);
                int index2 = Integer.parseInt(id2);
                return Integer.compare(index1, index2);
            } catch (NumberFormatException e) {
                // Fallback auf String Vergleich wenn Parsing fehlschlägt
                return id1.compareTo(id2);
            }
        });

        // Jedes Protokoll verarbeiten
        for (String protokollId : protokollIds) {
            processProtokoll(protokollId);
        }
    }

    /**
     * Verarbeitet ein einzelnes Protokoll und fügt es dem Dokument hinzu.
     * Extrahiert Metadaten, Tagesordnung und Reden aus dem Protokoll und
     * formatiert sie als LaTeX-Abschnitte.
     *
     * @param protokollId Die ID des zu verarbeitenden Protokolls
     * @author Maik Kitzmann
     */
    private void processProtokoll(String protokollId) {
        System.out.println("  Verarbeite Protokoll mit ID: " + protokollId);

        // Alle Reden für dieses Protokoll finden
        Document filter = new Document("protocol.index", protokollId);
        FindIterable<Document> redenForProtokoll = mongoHandler.findDocuments("rede", filter);

        List<Document> redenList = new ArrayList<>();
        redenForProtokoll.forEach(redenList::add);

        if (redenList.isEmpty()) {
            System.out.println("  Keine Reden für Protokoll " + protokollId + " gefunden");
            addToBody(() -> "\\section{Protokoll " + LaTeXComponent.Utils.escapeTeX(protokollId) + "}\n\n" +
                    "Keine Reden für dieses Protokoll gefunden.\n");
            return;
        }

        System.out.println("  Gefunden: " + redenList.size() + " Reden für Protokoll " + protokollId);

        // Protokollinformationen aus der ersten Rede extrahieren weil alle aus Protokoll gleiche Protokoll-Daten
        Document ersteRede = redenList.get(0);
        Document protokollInfo = (Document) ersteRede.get("protocol");

        String titel = "Protokoll " + protokollId;
        String datum = "Unbekanntes Datum";

        // Protokoll-Metadaten extrahieren
        if (protokollInfo != null) {
            String protocolTitle = protokollInfo.getString("title");
            String protocolDate = protokollInfo.getString("date");

            if (protocolTitle != null && !protocolTitle.isEmpty()) {
                titel = protocolTitle;
            }

            if (protocolDate != null && !protocolDate.isEmpty()) {
                datum = protocolDate;
            }
        }

        // Protokollabschnitt mit Index und Datum erstellen (für Sortierung im Inhaltsverzeichnis)
        final String sectionTitle = titel;
        final String sectionDate = datum;

        addToBody(() -> {
            StringBuilder latex = new StringBuilder();
            // Abschnitt mit Protokolltitel, Index und Datum
            latex.append("\\section{" + LaTeXComponent.Utils.escapeTeX(sectionTitle) + " (Protokoll " +
                    LaTeXComponent.Utils.escapeTeX(protokollId) + ", " +
                    LaTeXComponent.Utils.escapeTeX(sectionDate) + ")}\n");
            return latex.toString();
        });

        // Tagesordnung aus der ersten Rede extrahieren
        Set<String> agendaItems = new HashSet<>();
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

        // Tagesordnungsunterpunkt hinzufügen
        addToBody(() -> {
            StringBuilder latex = new StringBuilder();
            latex.append("\\subsection{Tagesordnung}\n");

            if (!agendaItems.isEmpty()) {
                latex.append("\\begin{itemize}\n");
                for (String item : agendaItems) {
                    latex.append("  \\item " + LaTeXComponent.Utils.escapeTeX(item) + "\n");
                }
                latex.append("\\end{itemize}\n\n");
            } else {
                latex.append("Keine Tagesordnung verfügbar.\n\n");
            }

            return latex.toString();
        });

        // Redenunterpunkt hinzufügen
        addToBody(() -> "\\subsection{Reden}\n\n");

        // Reden nach Redner sortieren
        redenList.sort(Comparator.comparing(doc -> doc.getString("speaker")));

        // Jede Rede hinzufügen
        for (Document rede : redenList) {
            String rednerName = rede.getString("speaker");

            // Redekomponente erstellen
            final Document finalRede = rede;
            addToBody(() -> {
                StringBuilder latex = new StringBuilder();
                latex.append("\\subsubsection{Rede von " + LaTeXComponent.Utils.escapeTeX(rednerName) + "}\n\n");

                // Redeinhalt ohne Tagesordnungspunkte hinzufügen (showAgendaItems=false)
                SpeechComponent speechComponent = new SpeechComponent(finalRede, null, mongoHandler, false);
                latex.append(speechComponent.toTex());

                latex.append("\\newpage\n\n");
                return latex.toString();
            });
        }
    }
}
