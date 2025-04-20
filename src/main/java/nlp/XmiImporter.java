package nlp;

import database.MongoDatabaseHandler;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.fit.factory.CasFactory;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.util.FileUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.time.Instant;

/**
 * Klasse zur Verarbeitung und Speicherung von XMI-Daten in MongoDB.
 *
 * @author Delia Maniliuc
 */
public class XmiImporter {
    private static final Logger logger = LoggerFactory.getLogger(XmiImporter.class);
    private MongoDatabaseHandler dbHandler;
    private static final String XMI_FOLDER_PATH = "/path/to/xmi";
    private static final String EXTRACTED_XMI_PATH = "/path/to/xmi_extracted";
    private static final String COLLECTION_NAME = "rede_nlp";

    /**
     * Konstruktor zur Initialisierung mit einer MongoDB-Datenbankverbindung.
     *
     * @param dbHandler Datenbank-Handler für MongoDB.
     */
    public XmiImporter(MongoDatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /**
     * Entpackt eine GZIP-Datei und speichert sie als XMI-Datei.
     *
     * @param gzFile Die GZIP-Datei.
     * @return Die entpackte XMI-Datei.
     * @throws IOException Falls ein Fehler beim Entpacken auftritt.
     */
    private File decompressGzipFile(File gzFile) throws IOException {
        File extractedFolder = new File(EXTRACTED_XMI_PATH);
        if (!extractedFolder.exists()) {
            extractedFolder.mkdirs();
        }

        String outputFileName = EXTRACTED_XMI_PATH + "/" + gzFile.getName().replace(".gz", "");
        File outputFile = new File(outputFileName);

        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzFile));
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        return outputFile;
    }

    /**
     * Importiert alle XMI-Dateien aus einem Verzeichnis.
     */
    public void importXmiFiles() {
        File folder = new File(XMI_FOLDER_PATH);
        if (!folder.exists() || !folder.isDirectory()) {
            logger.error("XMI-Verzeichnis nicht gefunden: " + XMI_FOLDER_PATH);
            return;
        }

        for (File file : folder.listFiles()) {
            if (file.getName().endsWith(".xmi.gz")) {
                try {
                    File xmiFile = decompressGzipFile(file);
                    importXmiFile(xmiFile);
                    logger.info("Importiert: " + xmiFile.getName());
                } catch (Exception e) {
                    logger.error("Fehler beim Entpacken/Import von: " + file.getName(), e);
                }
            }
        }
    }

    /**
     * Importiert eine einzelne XMI-Datei in die Datenbank.
     *
     * @param xmiFile Die XMI-Datei.
     * @throws Exception Falls ein Fehler beim Parsen auftritt.
     */
    private void importXmiFile(File xmiFile) throws Exception {
        CAS cas = CasFactory.createCas();
        XmiCasDeserializer.deserialize(new FileInputStream(xmiFile), cas, true);

        String fileName = xmiFile.getName().replace(".xmi", "");
        String uniqueId = fileName + "-" + Instant.now().toEpochMilli();

        Document speechDoc = new Document()
                .append("_id", uniqueId)
                .append("text", cas.getDocumentText())
                .append("tokens", extractAnnotations(cas, "Token"))
                .append("sentences", extractAnnotations(cas, "Sentence"))
                .append("pos", extractAnnotations(cas, "POS"))
                .append("dependency", extractAnnotations(cas, "Dependency"))
                .append("named_entities", extractAnnotations(cas, "NamedEntity"))
                .append("lemma", extractAnnotations(cas, "Lemma"))
                .append("topic", extractTopicAnnotations(cas))
                .append("sentiment", extractSentimentAnnotationsWithIds(cas));

        dbHandler.insertDocument(COLLECTION_NAME, speechDoc);
    }

    /**
     * Extrahiert Annotationen aus dem CAS.
     *
     * @param cas CAS-Objekt.
     * @param annotationType Der Typ der Annotation.
     * @return Liste der extrahierten Annotationen.
     */
    private List<String> extractAnnotations(CAS cas, String annotationType) {
        List<String> annotations = new ArrayList<>();
        try {
            AnnotationIndex<AnnotationFS> index = cas.getAnnotationIndex(cas.getTypeSystem().getType(annotationType));
            for (AnnotationFS annotation : index) {
                annotations.add(annotation.getCoveredText());
            }
        } catch (Exception e) {
            logger.error("Fehler beim Extrahieren der Annotationen: " + annotationType, e);
        }
        return annotations;
    }

    /**
     * Extrahiert Themen-Annotationen.
     *
     * @param cas CAS-Objekt.
     * @return Liste der extrahierten Themen-Annotationen.
     */
    private List<Document> extractTopicAnnotations(CAS cas) {
        List<Document> topics = new ArrayList<>();
        return topics;
    }

    /**
     * Extrahiert Sentiment-Annotationen.
     *
     * @param cas CAS-Objekt.
     * @return Liste der extrahierten Sentiment-Annotationen.
     */
    private List<Document> extractSentimentAnnotationsWithIds(CAS cas) {
        List<Document> sentiments = new ArrayList<>();
        return sentiments;
    }

    /**
     * Hauptmethode zum Starten des Importprozesses.
     *
     * @param args Kommandozeilenargumente.
     * @throws Exception Falls ein Fehler auftritt.
     */
    public static void main(String[] args) throws Exception {
        MongoDatabaseHandler dbHandler = new MongoDatabaseHandler("mongodb.properties");
        XmiImporter importer = new XmiImporter(dbHandler);

        System.out.println("Starte Import der annotierten XMI-Reden in `rede_nlp`");
        importer.importXmiFiles();
        System.out.println("Import abgeschlossen!");
    }
}
