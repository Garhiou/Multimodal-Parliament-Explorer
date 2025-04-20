package nlp;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import database.MongoDatabaseHandler;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.XmlCasSerializer;
import org.bson.Document;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * Diese Klasse verarbeitet Bundestagsreden mit NLP-Techniken und speichert die Ergebnisse in MongoDB.
 *
 * @author Ibrahim Garhiou
 */
public class RedenNLP {
    private MongoDatabaseHandler mongoHandler;
    private DUUIComposer composer;

    /**
     * Konstruktor: Initialisiert den MongoDatabaseHandler und den DUUI-Composer.
     *
     * @param handler Datenbank-Handler für MongoDB.
     * @throws Exception Falls ein Fehler bei der Initialisierung auftritt.
     */
    public RedenNLP(MongoDatabaseHandler handler) throws Exception {
        this.mongoHandler = handler;

        int iWorkers = 1;
        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();
        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(ctx)
                .withWorkers(iWorkers);

        DUUIRemoteDriver remoteDriver = new DUUIRemoteDriver();
        composer.addDriver(remoteDriver);

        // NLP-Komponenten hinzufügen
        composer.add(new DUUIRemoteDriver.Component("http://spacy.lehre.texttechnologylab.org")
                .withScale(iWorkers)
                .withParameter("selection", "text")
                .build());

        composer.add(new DUUIRemoteDriver.Component("http://gervader.lehre.texttechnologylab.org")
                .withScale(iWorkers)
                .withParameter("selection", "text")
                .withParameter("lang", "de")
                .build());
    }

    /**
     * Ruft alle Reden aus der MongoDB ab.
     *
     * @return Liste der Reden-Dokumente.
     */
    public List<Document> getAllSpeeches() {
        List<Document> speeches = new ArrayList<>();
        MongoCollection<Document> collection = mongoHandler.getDatabase().getCollection("rede");
        FindIterable<Document> docs = collection.find();
        for (Document doc : docs) {
            speeches.add(doc);
        }
        return speeches;
    }

    /**
     * Erstellt ein JCas-Objekt aus einem Speech-Dokument.
     *
     * @param speechDoc MongoDB-Dokument einer Rede.
     * @return JCas-Objekt mit Redeinhalt.
     */
    public JCas createJCasFromSpeech(Document speechDoc) {
        try {
            JCas jcas = JCasFactory.createJCas();
            jcas.setDocumentText(speechDoc.getString("text"));
            jcas.setDocumentLanguage("de");
            return jcas;
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen des JCas für Rede " + speechDoc.getString("_id"));
            e.printStackTrace();
            return null;
        }
    }

    /**
     * NLP-Verarbeitung für eine Rede durchführen.
     *
     * @param jcas JCas-Objekt der Rede.
     */
    public void processJCas(JCas jcas) {
        try {
            composer.run(jcas);
        } catch (Exception e) {
            System.err.println("Fehler in der NLP-Verarbeitung: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Speichert die NLP-Ergebnisse in die MongoDB.
     *
     * @param speechId ID der Rede in MongoDB.
     * @param jcas JCas-Objekt mit NLP-Ergebnissen.
     */
    public void saveResults(String speechId, JCas jcas) {
        try {
            MongoCollection<Document> collection = mongoHandler.getDatabase().getCollection("rede");
            Document update = new Document("$set", new Document("nlpResults", serializeCasToXMI(jcas)));
            collection.updateOne(Filters.eq("_id", speechId), update);
        } catch (Exception e) {
            System.err.println("Fehler beim Speichern der NLP-Ergebnisse: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Serialisiert ein JCas-Objekt in einen XMI-String.
     *
     * @param jcas JCas-Objekt.
     * @return XMI-String.
     */
    private String serializeCasToXMI(JCas jcas) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            XmlCasSerializer.serialize(jcas.getCas(), outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Fehler beim Serialisieren des CAS: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verarbeitet alle Reden aus der Datenbank.
     */
    public void processAllSpeeches() {
        List<Document> speeches = getAllSpeeches();
        for (Document speechDoc : speeches) {
            JCas jcas = createJCasFromSpeech(speechDoc);
            processJCas(jcas);
            saveResults(speechDoc.getString("_id"), jcas);
        }
    }

    /**
     * Hauptmethode zur Verarbeitung aller Reden.
     *
     * @param args Kommandozeilenargumente.
     */
    public static void main(String[] args) {
        try {
            MongoDatabaseHandler mongoHandler = new MongoDatabaseHandler("mongodb.properties");
            RedenNLP redeNLP = new RedenNLP(mongoHandler);
            redeNLP.processAllSpeeches();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
