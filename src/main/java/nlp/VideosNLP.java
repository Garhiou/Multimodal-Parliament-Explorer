package nlp;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import database.MongoDatabaseHandler;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.bson.Document;
import org.hucompute.textimager.uima.type.Sentiment;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIUIMADriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Demonstrationsklasse:
 * 1) Video laden, 2) WhisperX (Remote) -> Transkript,
 * 3) ParlBERT (Remote) -> NLP-Analyse, 4) GerVader -> Sentiment,
 * 5) spaCy -> weitere Annotationen.
 *
 * @author Ibrahim Garhiou
 * @modifiedBy Delia Maniliuc
 */
public class VideosNLP {

    private static DUUIComposer composer;
    private static final int NUM_WORKERS = 1;
    private static final String COLLECTION_NAME = "transkription_nlp_";
    private final MongoDatabaseHandler dbHandler;

    // Pfad zu deinem Ordner mit Videos
    static final String VIDEOS_FOLDER_PATH = "/Users/deliamaniliuc/Desktop/PPR/Multimodal_Parliament_Explorer_7_2/src/main/resources/Videos";


    static {
        try {
            // Lua-Kontext für DUUI
            DUUILuaContext luaCtx = new DUUILuaContext().withJsonLibrary();

            // Composer erstellen (ohne DockerDriver, nur Remote + UIMA)
            composer = new DUUIComposer()
                    .withSkipVerification(true)
                    .withLuaContext(luaCtx)
                    .withWorkers(NUM_WORKERS);

            DUUIUIMADriver uimaDriver = new DUUIUIMADriver();
            DUUIRemoteDriver remoteDriver = new DUUIRemoteDriver();

            // Füge nur UIMA- und Remote-Driver hinzu
            composer.addDriver(uimaDriver, remoteDriver);

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * Konstruktor zur Initialisierung mit einer MongoDB-Datenbankverbindung.
     *
     * @param dbHandler Datenbank-Handler für MongoDB.
     */

    public VideosNLP(MongoDatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }
    /**
     * Hauptmethode zum Starten der Videoverarbeitung.
     *
     * @param args Kommandozeilenargumente.
     * @throws Exception Falls ein Fehler auftritt.
     */

    public static void main(String[] args) throws Exception {
        MongoDatabaseHandler dbHandler = new MongoDatabaseHandler("mongodb.properties");
        VideosNLP videoProcessor = new VideosNLP(dbHandler);
        videoProcessor.processAllVideos();
    }


    /**
     * Verarbeitet alle Videos in einem bestimmten Verzeichnis.
     */

    public void processAllVideos() {
        File folder = new File(VIDEOS_FOLDER_PATH);
        File[] videoFiles = folder.listFiles((dir, name) -> name.endsWith(".mp4"));

        if (videoFiles == null || videoFiles.length == 0) {
            System.out.println("Keine Videos gefunden!");
            return;
        }

        for (File videoFile : videoFiles) {
            try {
                processVideo(videoFile);
            } catch (Exception e) {
                System.err.println("Fehler bei der Verarbeitung von " + videoFile.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Führt die gesamte Remote-Pipeline für ein einzelnes Video aus:
     * 1) WhisperX -> Transkript (View "transcript")
     * 2) ParlBERT -> NLP (View "transcript")
     * 3) GerVader -> Sentiment (View "transcript")
     * 4) spaCy -> Token, POS, NER etc. (View "transcript")
     */
    private void processVideo(File videoFile) throws Exception {
        composer.resetPipeline();

        // (1) WhisperX
        composer.add(new DUUIRemoteDriver.Component("http://whisperx.lehre.texttechnologylab.org")
                .withScale(NUM_WORKERS)
                .withSourceView("video")
                .withTargetView("transcript")
                .build());

        // (2) ParlBERT
        composer.add(new DUUIRemoteDriver.Component("http://parlbert.lehre.texttechnologylab.org")
                .withScale(NUM_WORKERS)
                .withSourceView("transcript")
                .withTargetView("transcript")
                .build());

        // (3) GerVader
        composer.add(new DUUIRemoteDriver.Component("http://gervader.lehre.texttechnologylab.org")
                .withScale(NUM_WORKERS)
                .withParameter("selection", "text")
                .withSourceView("transcript")
                .withTargetView("transcript")
                .build());

        // (4) spaCy
        composer.add(new DUUIRemoteDriver.Component("http://spacy.lehre.texttechnologylab.org")
                .withScale(NUM_WORKERS)
                .withSourceView("transcript")
                .withTargetView("transcript")
                .build());

        // CAS erstellen
        JCas mainCas = createBaseCas("VideosNLP-" + videoFile.getName());

        // View "video" mit Base64-Daten
        JCas videoView = mainCas.createView("video");
        byte[] videoBytes = Files.readAllBytes(Path.of(videoFile.getAbsolutePath()));
        String encodedVideo = Base64.getEncoder().encodeToString(videoBytes);
        String mimeType = Files.probeContentType(Path.of(videoFile.getAbsolutePath()));

        videoView.setSofaDataString(encodedVideo, mimeType);
        videoView.setDocumentLanguage("de");

        // Leere View "transcript"
        JCas transcriptView = mainCas.createView("transcript");
        transcriptView.setDocumentLanguage("de");

        // Pipeline ausführen
        composer.run(mainCas);

        // Ergebnisse extrahieren
        String transcript = transcriptView.getDocumentText();
        List<String> tokens = extractAnnotations(transcriptView, "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token");
        List<String> sentences = extractAnnotations(transcriptView, "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence");
        List<String> namedEntities = extractAnnotations(transcriptView, "de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity");
        List<Document> sentiment = extractSentimentAnnotationsWithIds(mainCas.getCas());
        List<String> lemmas = extractAnnotations(transcriptView, "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma");
        List<Document> topics = extractTopicAnnotations(mainCas.getCas());

        // MongoDB-Dokument erstellen
        Document doc = new Document()
                .append("video_name", videoFile.getName())
                .append("transkript", transcript)
                .append("tokens", tokens)
                .append("sentences", sentences)
                .append("namedEntities", namedEntities)
                .append("sentiment", sentiment)
                .append("lemmas", lemmas)
                .append("topics", topics);

        // Speichern in MongoDB
        dbHandler.insertDocument(COLLECTION_NAME, doc);
        System.out.println("Verarbeitung und Speicherung von " + videoFile.getName() + " abgeschlossen.");
    }
    /**
     * Hilfsmethode zum Extrahieren von TopicAnnotationen aus Cas
     */
    private List<Document> extractTopicAnnotations(CAS cas) {
        List<Document> topics = new ArrayList<>();
        try {
            AnnotationIndex<AnnotationFS> index = cas.getAnnotationIndex(
                    cas.getTypeSystem().getType("org.hucompute.textimager.uima.type.category.CategoryCoveredTagged")
            );
            for (AnnotationFS annotation : index) {
                Document topicData = new Document()
                        .append("value", annotation.getFeatureValueAsString(
                                annotation.getType().getFeatureByBaseName("value")))
                        .append("score", annotation.getFeatureValueAsString(
                                annotation.getType().getFeatureByBaseName("score")))
                        .append("begin", annotation.getBegin())
                        .append("end", annotation.getEnd());
                topics.add(topicData);
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Extrahieren der Topic-Annotationen: " + e.getMessage());
            e.printStackTrace();
        }
        return topics;
    }

    /**
     * Hilfsmethode zum Extrahieren von Sentiments mit Id aus Cas
     */
    private List<Document> extractSentimentAnnotationsWithIds(CAS cas) {
        List<Document> sentiments = new ArrayList<>();
        try {
            // Den JCas holen:
            JCas jcas = cas.getJCas();

            // Jetzt auf dem JCas selektieren
            for (Sentiment sentiment : JCasUtil.select(jcas, Sentiment.class)) {
                Document doc = new Document();
                doc.put("sentiment", sentiment.getSentiment());
                doc.put("subjectivity", sentiment.getSubjectivity());
                doc.put("begin", sentiment.getBegin());
                doc.put("end", sentiment.getEnd());
                sentiments.add(doc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sentiments;
    }



    /**
     * Hilfsmethode zum Extrahieren von Annotationen aus JCas
     */
    private List<String> extractAnnotations(JCas jcas, String annotationType) {
        List<String> annotations = new ArrayList<>();
        try {
            Class<?> annotationClass = Class.forName(annotationType);
            for (Annotation annotation : JCasUtil.select(jcas, (Class<Annotation>) annotationClass)) {
                annotations.add(annotation.getCoveredText());
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Fehler: Annotationstyp nicht gefunden: " + annotationType);
        }
        return annotations;
    }

    /**
     * CAS mit minimalem Text in der Standard-View + Metadaten.
     */
    private static JCas createBaseCas(String docId) throws UIMAException, CASException {
        JCas jCas = JCasFactory.createText("Text für " + docId, "de");
        DocumentMetaData dmd = new DocumentMetaData(jCas);
        dmd.setDocumentId(docId);
        dmd.setDocumentTitle("VideosNLP - " + docId);
        dmd.addToIndexes();
        return jCas;
    }
}

