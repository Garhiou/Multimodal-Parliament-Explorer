package parser;

import database.MongoDatabaseHandler;
import org.bson.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.nio.file.Paths;

/**
 * Diese Klasse parst die Abgeordnetendaten aus der XML-Datei und speichert sie in die MongoDB.
 *
 * @author Ibrahim Garhiou
 */
public class AbgeordneteParser {
    private MongoDatabaseHandler mongoHandler;
    private static final String XML_FILE_PATH = "src/main/resources/MdB-Stammdaten/MDB_STAMMDATEN.XML"; // XML-Pfad

    public AbgeordneteParser(MongoDatabaseHandler handler) {
        this.mongoHandler = handler;
    }

    /**
     * Liest und parst die XML-Datei mit Abgeordnetendaten und speichert sie in MongoDB.
     *
     * @author Ibrahim Garhiou
     */
    public void parseAbgeordnete() {
        try {
            File xmlFile = Paths.get(XML_FILE_PATH).toFile(); // path als string in richtigem Path und davon objekt erzeugen
            if (!xmlFile.exists()) {
                System.err.println("XML-Datei nicht gefunden: " + XML_FILE_PATH);
                return;
            }

            System.out.println("Lese XML-Datei: " + XML_FILE_PATH);
            org.jsoup.nodes.Document doc = Jsoup.parse(xmlFile, "UTF-8");

            Elements abgeordneteElements = doc.select("MDB"); // Haupt-Tag für Abgeordnete

            for (Element mdbElement : abgeordneteElements) {
                // IDs und Namen auslesen
                String id = getElementText(mdbElement, "ID");
                Element nameElement = mdbElement.selectFirst("NAME");
                String nachname = getElementText(nameElement, "NACHNAME");
                String vorname = getElementText(nameElement, "VORNAME");
                String titel = getElementText(nameElement, "ANREDE_TITEL");
                String akademischerTitel = getElementText(nameElement, "AKAD_TITEL");

                // Biografische Daten
                Element bio = mdbElement.selectFirst("BIOGRAFISCHE_ANGABEN");
                String geburtsdatum = getElementText(bio, "GEBURTSDATUM");
                String geburtsort = getElementText(bio, "GEBURTSORT");
                String sterbedatum = getElementText(bio, "STERBEDATUM");
                String geschlecht = getElementText(bio, "GESCHLECHT");
                String partei = getElementText(bio, "PARTEI_KURZ");
                String beruf = getElementText(bio, "BERUF");
                String religion = getElementText(bio, "RELIGION");
                String familienstand = getElementText(bio, "FAMILIENSTAND");
                String vita = getElementText(bio, "VITA_KURZ");

                // Dokument für MongoDB erstellen
                Document abgeordneter = new Document("_id", id)
                        .append("id", id)
                        .append("name", nachname)
                        .append("vorname", vorname)
                        .append("title", titel)
                        .append("akademischerTitel", akademischerTitel)
                        .append("geburtsdatum", geburtsdatum)
                        .append("geburtsort", geburtsort)
                        .append("sterbedatum", sterbedatum)
                        .append("geschlecht", geschlecht)
                        .append("beruf", beruf)
                        .append("religion", religion)
                        .append("familienstand", familienstand)
                        .append("vita", vita)
                        .append("party", partei);

                // Falls noch nicht vorhanden, in MongoDB speichern
                if (!mongoHandler.documentExists("abgeordnete", "id", id)) {
                    mongoHandler.insertDocument("abgeordnete", abgeordneter);
                    System.out.println(" Abgeordneter gespeichert: " + nachname + ", " + vorname);
                } else {
                    System.out.println("Abgeordneter bereits in DB: " + nachname + ", " + vorname);
                }
            }
        } catch (Exception e) {
            System.err.println(" Fehler beim Parsen der Abgeordneten-XML!");
            e.printStackTrace();
        }
    }

    /**
     * Hilfsmethode, um den Text eines Elements sicher zu extrahieren.
     *
     * @author Ibrahim Garhiou
     */
    private String getElementText(Element parent, String tag) {
        if (parent == null) return "Nichts";
        Element element = parent.selectFirst(tag);
        return (element != null) ? element.text() : "Nichts";
    }

    /**
     * Hauptmethode für den manuellen Test.
     *
     * @author Ibrahim Garhiou
     */
    public static void main(String[] args) {
        try {
            MongoDatabaseHandler mongoHandler = new MongoDatabaseHandler("mongodb.properties");
            AbgeordneteParser parser = new AbgeordneteParser(mongoHandler);
            parser.parseAbgeordnete();
            System.out.println("Parsing abgeschlossen!");
        } catch (Exception e) {
            System.out.println("Fehler beim Starten des Parsers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
