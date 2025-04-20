package latex;

import latex.impl.LaTeXComponent;
import database.MongoDatabaseHandler;
import org.bson.Document;
import utils.SpeechUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Komponente zur Generierung von LaTeX-Code für die Anzeige der Metadaten eines Abgeordneten.
 * Erstellt eine strukturierte Darstellung mit persönlichen Informationen und Bild des Abgeordneten,
 * sofern verfügbar. Lädt notwendige Informationen aus der Datenbank und ggf. Bilder aus dem Internet.
 *
 * @author Maik Kitzmann
 */
class AbgeordnetenInfoComponent implements LaTeXComponent {
    private final String rednerName;
    private final Path exportDir;
    private final MongoDatabaseHandler mongoHandler;

    /**
     * Konstruktor für die AbgeordnetenInfoComponent.
     *
     * @param rednerName Der Name des Abgeordneten/Redners, für den Informationen angezeigt werden sollen
     * @param exportDir Das Verzeichnis, in dem Ressourcen (z.B. Bilder) gespeichert werden
     * @param mongoHandler Der Handler für den Zugriff auf die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public AbgeordnetenInfoComponent(String rednerName, Path exportDir, MongoDatabaseHandler mongoHandler) {
        this.rednerName = rednerName;
        this.exportDir = exportDir;
        this.mongoHandler = mongoHandler;
    }

    /**
     * Generiert LaTeX-Code für die Anzeige der Abgeordneteninformationen.
     * Sucht die Daten des Abgeordneten in der Datenbank, formatiert diese und
     * lädt bei Verfügbarkeit auch ein Bild des Abgeordneten herunter.
     *
     * @return String mit LaTeX-Code zur Darstellung der Abgeordneteninformationen
     * @author Maik Kitzmann
     */
    @Override
    public String toTex() {
        StringBuilder latex = new StringBuilder();

        System.out.println("      Generiere Abgeordneteninformationen für: " + rednerName);

        // Suche Abgeordneten in der Datenbank
        Document rednerDoc = SpeechUtils.getRednerBySpeakerName(rednerName, mongoHandler);

        // Fallback-Suche nach Nachnamen
        if (rednerDoc == null) {
            String[] nameParts = rednerName.split(" ");
            if (nameParts.length > 1) {
                String lastName = nameParts[nameParts.length - 1];
                Document filter = new Document("name", lastName);
                rednerDoc = mongoHandler.findDocuments("abgeordnete", filter).first();
            }
        }

        latex.append("\\begin{center}\n");
        latex.append("\\fbox{\\begin{minipage}{0.9\\textwidth}\n");
        latex.append("\\section*{Abgeordneteninformation}\n");

        if (rednerDoc != null) {
            System.out.println("      Abgeordneter gefunden mit ID: " + rednerDoc.getString("_id"));

            // Daten aus dem Dokument extrahieren
            String vorname = rednerDoc.getString("vorname");
            String nachname = rednerDoc.getString("name");
            String partei = rednerDoc.getString("party");
            String titel = rednerDoc.getString("title");
            String akademischerTitel = rednerDoc.getString("akademischerTitel");
            String geburtsdatum = rednerDoc.getString("geburtsdatum");
            String beruf = rednerDoc.getString("beruf");

            // Name mit Titeln formatieren
            latex.append("\\textbf{Name:} ");
            if (titel != null && !titel.isEmpty()) {
                latex.append(LaTeXComponent.Utils.escapeTeX(titel)).append(" ");
            }
            if (akademischerTitel != null && !akademischerTitel.isEmpty()) {
                latex.append(LaTeXComponent.Utils.escapeTeX(akademischerTitel)).append(" ");
            }
            latex.append(LaTeXComponent.Utils.escapeTeX(vorname)).append(" ").append(LaTeXComponent.Utils.escapeTeX(nachname)).append("\n\n");

            // Weitere Infos hinzufügen
            if (partei != null && !partei.isEmpty()) {
                latex.append("\\textbf{Fraktion:} ").append(LaTeXComponent.Utils.escapeTeX(partei)).append("\n\n");
            }

            if (geburtsdatum != null && !geburtsdatum.isEmpty()) {
                latex.append("\\textbf{Geburtsdatum:} ").append(LaTeXComponent.Utils.escapeTeX(geburtsdatum)).append("\n\n");
            }

            if (beruf != null && !beruf.isEmpty()) {
                latex.append("\\textbf{Beruf:} ").append(LaTeXComponent.Utils.escapeTeX(beruf)).append("\n\n");
            }

            // Bild einbinden falls vorhanden
            String bildUrl = SpeechUtils.getBildUrlForRedner(rednerDoc, mongoHandler);
            if (bildUrl != null && !bildUrl.isEmpty()) {
                System.out.println("      Bild-URL gefunden: " + bildUrl);

                String localImagePath = downloadAndSaveImage(bildUrl, exportDir);

                if (localImagePath != null) {
                    latex.append("\\begin{center}\n");
                    latex.append("\\fbox{\\begin{minipage}{5.2cm}\\centering\n");
                    latex.append("\\includegraphics[width=5cm]{").append(LaTeXComponent.Utils.escapeTeX(localImagePath)).append("}\n");
                    latex.append("\\end{minipage}}\n");
                    latex.append("\\end{center}\n\n");
                } else {
                    latex.append("\\begin{center}\n");
                    latex.append("\\textit{Bild nicht verfügbar}\n");
                    latex.append("\\end{center}\n\n");
                }
            } else {
                System.out.println("      Kein Bild gefunden für Abgeordneten");
            }
        } else {
            // Fallback wenn kein Abgeordneter gefunden wurde
            System.out.println("      Abgeordneter nicht in der Datenbank gefunden");
            latex.append("\\textbf{Name:} ").append(LaTeXComponent.Utils.escapeTeX(rednerName)).append("\n\n");
            latex.append("\\textit{Keine weiteren Informationen verfügbar}\n\n");
        }

        latex.append("\\end{minipage}}\\par\n");
        latex.append("\\end{center}\n\n");

        return latex.toString();
    }

    /**
     * Lädt ein Bild von einer URL herunter und speichert es im Exportverzeichnis.
     * Generiert einen eindeutigen Dateinamen und stellt eine robuste Verbindung zum Webserver her.
     *
     * @param imageUrl Die URL des herunterzuladenden Bildes
     * @param exportDir Das Verzeichnis, in dem das Bild gespeichert werden soll
     * @return Der lokale Dateipfad des gespeicherten Bildes oder null bei Fehlern
     * @author Maik Kitzmann
     */
    private String downloadAndSaveImage(String imageUrl, Path exportDir) {
        try {
            if (exportDir == null) {
                System.err.println("WARNUNG: exportDir ist null! Bild kann nicht gespeichert werden.");
                return null;
            }

            System.out.println("      Versuche Bild herunterzuladen: " + imageUrl);

            String fileName = "img_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            Path localImageFile = exportDir.resolve(fileName);

            java.net.URL url = new java.net.URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(localImageFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("      Bild erfolgreich heruntergeladen: " + localImageFile);
            return fileName;

        } catch (Exception e) {
            System.err.println("      Fehler beim Herunterladen des Bildes: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
