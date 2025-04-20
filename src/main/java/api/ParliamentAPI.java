package api;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinFreemarker;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.bson.Document;
import database.MongoDatabaseHandler;
import com.mongodb.client.FindIterable;
import utils.SpeechUtils;

import java.util.*;


/**
 * REST API zur Verwaltung von Parlamentsdebatten.
 *
 * @author Delia Maniliuc
 * @modifiedBy Luana Schäfer
 * @modifiedBy Maik Kitzmann
 */
@OpenAPIDefinition(info = @Info(title = "Multimodal Parliament Explorer API", version = "1.0", description = "REST API zur Verwaltung von Parlamentsdebatten"))
@Tag(name = "Parliament API", description = "REST-Schnittstelle für Bundestagsdebatten")
public class ParliamentAPI {
    private static MongoDatabaseHandler mongoHandler;
    private static ExportAPI exportAPI;


    /**
     * Startet die REST-API.
     *
     * @param args Kommandozeilenargumente (nicht verwendet).
     */
    public static void main(String[] args) {
        try {
            mongoHandler = new MongoDatabaseHandler("mongodb.properties");
            exportAPI = new ExportAPI(mongoHandler);

        } catch (Exception e) {
            System.err.println("Fehler beim Laden der Datenbankverbindung");
            e.printStackTrace();
            return;
        }

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors ->
                    cors.addRule(it -> {
                        it.anyHost();
                    })
            );

            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/static";
                staticFiles.directory = "static";
                staticFiles.location = Location.CLASSPATH;
            });

            // FreeMarker konfigurieren
            Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_31);
            freemarkerConfig.setClassForTemplateLoading(ParliamentAPI.class, "/templates");
            freemarkerConfig.setDefaultEncoding("UTF-8");
            freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

            config.fileRenderer(new JavalinFreemarker(freemarkerConfig));
        }).start(7070);


        // Startseite
        app.get("/", ctx -> ctx.render("startseite.ftl"));

        //Redenseite
        app.get("/rede/{id}", ctx -> {ctx.render("rede.ftl");});

        //Analyse
        app.get("/analyse", ctx -> ctx.render("analyse.ftl"));


        // API-Endpunkte
        app.get("/reden", ParliamentAPI::getAllReden);
        app.get("/kommentare/{id}", ParliamentAPI::getKommentareByRedeId);
        app.get("/protokolle", ParliamentAPI::getAllProtokolle);
        app.get("/abgeordnete", ParliamentAPI::getAllAbgeordnete);
        app.get("/bilder/{id}", ParliamentAPI::getBildById);
        app.get("/api/search", ParliamentAPI::searchSpeeches);
        app.get("/api/rede/{id}", ParliamentAPI::getRedeById);
        app.get("/api/aggregation", ParliamentAPI::getAggregatedData);
        app.get("/api/sessions", ParliamentAPI::getAvailableSessions);
        app.get("/api/topics", ParliamentAPI::getAvailableTopics);
        app.get("/api/speaker-suggestions", ParliamentAPI::getSpeakerSuggestions);

        // Export-API Routen registrieren
        exportAPI.registerRoutes(app);

        System.out.println("REST-API läuft auf Port 7070");
    }

    /**
     * Holt alle gespeicherten Reden mit Paginierung.
     *
     * @param ctx Javalin Context-Objekt.
     * @author Delia Maniliuc
     */
    @Operation(summary = "Holt alle Reden", description = "Gibt eine Liste aller gespeicherten Reden zurück, mit Paginierung")
    private static void getAllReden(Context ctx) {
        try {
            // Standardwerte setzen, falls keine Parameter angegeben wurden
            int limit = Integer.parseInt(Objects.requireNonNull(ctx.queryParam("limit"), "50"));
            int skip = Integer.parseInt(Objects.requireNonNull(ctx.queryParam("skip"), "0"));

            // Sicherstellen, dass limit und skip nicht negativ sind
            if (limit <= 0 || limit > 100) limit = 50; // Maximal 100 Einträge pro Anfrage
            if (skip < 0) skip = 0;

            System.out.println("Lade Reden mit Limit: " + limit + ", Skip: " + skip);

            // MongoDB-Abfrage mit Paginierung
            FindIterable<Document> reden = mongoHandler.findDocuments("rede", new Document())
                    .skip(skip)
                    .limit(limit);

            List<Document> redenList = new ArrayList<>();
            reden.forEach(redenList::add);

            ctx.json(redenList);
        } catch (Exception e) {
            ctx.status(500).result("Fehler beim Laden der Reden");
            e.printStackTrace();
        }
    }

    /**
     * Holt alle Kommentare zu einer bestimmten Rede.
     *
     * @param ctx Javalin Context-Objekt.
     * @author Delia Maniliuc
     */
    @Operation(summary = "Holt alle Kommentare zu einer Rede", description = "Gibt eine Liste von Kommentaren basierend auf einer Rede-ID zurück")
    private static void getKommentareByRedeId(Context ctx) {
        String id = ctx.pathParam("id");
        FindIterable<Document> kommentare = mongoHandler.findDocuments("kommentare", new Document("redeID", id));
        List<Document> kommentarList = new ArrayList<>();
        kommentare.forEach(kommentarList::add);
        ctx.json(kommentarList);
    }

    /**
     * Holt alle gespeicherten Parlamentsprotokolle mit Paginierung.
     *
     * @param ctx Javalin Context-Objekt.
     * @author Delia Maniliuc
     */
    @Operation(summary = "Holt alle Protokolle", description = "Gibt eine Liste aller gespeicherten Parlamentsprotokolle zurück, mit Paginierung")
    private static void getAllProtokolle(Context ctx) {
        try {
            // Standardwerte setzen, falls keine Parameter angegeben wurden
            int limit = Integer.parseInt(Objects.requireNonNull(ctx.queryParam("limit"), "50"));
            int skip = Integer.parseInt(Objects.requireNonNull(ctx.queryParam("skip"), "0"));

            // Sicherstellen, dass limit und skip nicht negativ sind
            if (limit <= 0 || limit > 100) limit = 50; // Maximal 100 Einträge pro Anfrage
            if (skip < 0) skip = 0;

            System.out.println("Lade Protokolle mit Limit: " + limit + ", Skip: " + skip);

            // MongoDB-Abfrage mit Paginierung
            FindIterable<Document> protokolle = mongoHandler.findDocuments("protokolle", new Document())
                    .skip(skip)
                    .limit(limit);

            List<Document> protokollList = new ArrayList<>();
            protokolle.forEach(protokollList::add);

            ctx.json(protokollList);
        } catch (Exception e) {
            ctx.status(500).result("Fehler beim Laden der Protokolle");
            e.printStackTrace();
        }
    }


    /**
     * Holt alle Abgeordneten.
     *
     * @param ctx Javalin Context-Objekt.
     * @author Delia Maniliuc
     */
    @Operation(summary = "Holt alle Abgeordneten", description = "Gibt eine Liste aller gespeicherten Abgeordneten zurück")
    private static void getAllAbgeordnete(Context ctx) {
        FindIterable<Document> abgeordnete = mongoHandler.findDocuments("abgeordnete", new Document());
        List<Document> abgeordneteList = new ArrayList<>();
        abgeordnete.forEach(abgeordneteList::add);
        ctx.json(abgeordneteList);
    }

    /**
     * Holt ein Bild eines Abgeordneten anhand seiner ID.
     *
     * @param ctx Javalin Context-Objekt.
     * @author Delia Maniliuc
     */
    @Operation(summary = "Holt ein Bild eines Abgeordneten", description = "Gibt ein Bild basierend auf einer ID zurück")
    private static void getBildById(Context ctx) {
        String id = ctx.pathParam("id");
        Document bild = mongoHandler.findDocuments("bilder", new Document("_id", id)).first();
        if (bild != null) {
            ctx.json(bild);
        } else {
            ctx.status(404).result("Bild nicht gefunden");
        }
    }

    /**
     * Sucht Reden nach einem Stichwort.
     *
     * @param ctx Javalin Context-Objekt
     * @author Luana Schäfer
     */
    @Operation(summary = "Sucht Reden nach Stichwort", description = "Gibt eine Liste von Reden zurück, die das Stichwort enthalten")
    private static void searchSpeeches(Context ctx) {
        String searchQuery = ctx.queryParam("text");
        int page = SpeechUtils.parsePage(ctx.queryParam("page"));
        int limit = 5;

        if (!SpeechUtils.isValidSearchQuery(searchQuery)) {
            ctx.status(400).result("Bitte geben Sie ein gültiges Suchwort mit mindestens 2 Zeichen ein.");
            return;
        }

        Document filter = SpeechUtils.buildSearchFilter(searchQuery);

        try {
            List<Document> results = new ArrayList<>();
            FindIterable<Document> searchResults = mongoHandler.findDocuments("rede", filter)
                    .skip((page - 1) * limit)
                    .limit(limit);

            searchResults.forEach(results::add);

            // Redner- und Sitzungsinformationen ergänzen
            for (Document result : results) {
                SpeechUtils.enrichSpeechWithMetadata(result, mongoHandler);
            }

            boolean hasMore = results.size() == limit;
            Integer nextPage = hasMore ? page + 1 : null;

            Map<String, Object> response = new HashMap<>();
            response.put("results", results.isEmpty() ? List.of() : results);
            response.put("hasMore", hasMore);
            if (nextPage != null) response.put("nextPage", nextPage);

            ctx.json(response);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Fehler bei der Suche: " + e.getMessage());
        }
    }

    /**
     * Lädt alle Sitzungsnummern.
     *
     * @param ctx Javalin Context-Objekt
     * @author Luana Schäfer
     */
    @Operation(summary = "Lädt verfügbare Sitzungen", description = "Gibt eine Liste aller in der Datenbank enthaltenen Sitzungsnummern zurück")
    private static void getAvailableSessions(Context ctx) {
        try {
            List<String> sessions = SpeechUtils.getUniqueSessions(mongoHandler);
            ctx.json(sessions);
        } catch (Exception e) {
            ctx.status(500).result("Fehler beim Abrufen der Sitzungen");
            e.printStackTrace();
        }
    }

    /**
     * Lädt alle verfügbaren NLP-Topics.
     *
     * @param ctx Javalin Context-Objekt
     * @author Luana Schäfer
     */
    @Operation(summary = "Lädt verfügbare Themen", description = "Gibt eine Liste aller in der Datenbank enthaltenen NLP-Topics zurück")
    private static void getAvailableTopics(Context ctx) {
        try {
            List<String> topics = SpeechUtils.getUniqueTopics(mongoHandler);
            ctx.json(topics);
        } catch (Exception e) {
            ctx.status(500).result("Fehler beim Abrufen der Themen");
            e.printStackTrace();
        }
    }

    /**
     * Gibt eine vollständige Rede zurück, inklusive Redner-, Bild- und Metadaten.
     *
     * @param ctx Javalin Context-Objekt
     * @author Luana Schäfer
     */
    @Operation(summary = "Holt eine bestimmte Rede mit allen zugehörigen Daten", description = "Gibt eine Rede anhand ihrer ID mit Redner-, Bild- und Video-Informationen zurück")
    private static void getRedeById(Context ctx) {
        String id = ctx.pathParam("id");

        // Rede aus der Datenbank abrufen
        Document rede = mongoHandler.findDocuments("rede", new Document("_id", id)).first();
        if (rede == null) {
            ctx.status(404).result("Rede nicht gefunden");
            return;
        }

        // Redner-Informationen abrufen
        String speakerName = rede.getString("speaker");
        Document redner = (speakerName != null) ? SpeechUtils.getRednerBySpeakerName(speakerName, mongoHandler) : null;

        // Bild-URL abrufen
        String imageUrl = (redner != null) ? SpeechUtils.getBildUrlForRedner(redner, mongoHandler) : null;

        // Sitzungsnummer
        String sessionTitle = SpeechUtils.extractSessionTitle(rede);

        // Video-URL abrufen
        String videoUrl = SpeechUtils.getVideoUrlForRede(id, mongoHandler);

        // Rede-Text formatieren
        List<Map<String, String>> speechText = SpeechUtils.formatSpeechText(rede);

        // Named Entities holen
        List<Map<String, Object>> namedEntities = SpeechUtils.extractNamedEntities(rede);

        // POS-Tags abrufen
        List<Map<String, Object>> posTags = SpeechUtils.extractPOSTags(rede);

        // Sentiments abrufen
        List<Map<String, Object>> sentiments = SpeechUtils.extractSentiments(rede);

        // Topics aus der Rede extrahieren
        List<Document> topics = SpeechUtils.getTopicsFromSpeech(rede);


        // Antwort als JSON zusammenstellen
        Map<String, Object> response = new HashMap<>();
        response.put("rede", rede);
        response.put("sessionTitle", sessionTitle);
        response.put("speaker", speakerName);
        response.put("party", (redner != null) ? redner.getString("party") : "Unbekannt");
        response.put("imageUrl", imageUrl);
        response.put("videoUrl", videoUrl);
        response.put("textContent", speechText);
        response.put("namedEntities", namedEntities);
        response.put("posTags", posTags);
        response.put("sentiments", sentiments);
        response.put("topics", topics);

        ctx.json(response);
    }

    /**
     * Holt vorberechnete NLP-Aggregationen nach Filtertyp
     *
     * @param ctx Javalin Context-Objekt
     * @author Luana Schäfer
     */
    @Operation(summary = "Holt vorberechnete aggregierte NLP-Daten", description = "Gibt aggregierte Ergebnisse basierend auf Sitzung, Redner oder Topic zurück")
    private static void getAggregatedData(Context ctx) {
        String type = ctx.queryParam("type");
        String value = ctx.queryParam("value");

        if (type == null || value == null) {
            ctx.status(400).result("Bitte geben Sie sowohl 'type' als auch 'value' an.");
            return;
        }

        try {
            Document filter = new Document("type", type).append("value", value);
            Document aggregationResult = mongoHandler.findDocuments("aggregated_data", filter).first();

            if (aggregationResult == null) {
                ctx.status(404).result("Keine aggregierten Daten gefunden.");
                return;
            }

            ctx.json(aggregationResult);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Fehler beim Abrufen der aggregierten Daten.");
        }
    }


    /**
     * Gibt Redner-Vorschläge anhand einer Teilzeichenfolge zurück.
     *
     * @param ctx Javalin Context-Objekt
     * @author Luana Schäfer
     */
    @Operation(summary = "Holt Redner-Vorschläge mit NLP-Daten", description = "Gibt Rednernamen aus der Aggregation zurück")
    private static void getSpeakerSuggestions(Context ctx) {
        String query = ctx.queryParam("query");
        if (query == null || query.length() < 2) {
            ctx.json(List.of());
            return;
        }

        Document filter = new Document("type", "speakers")
                .append("value", new Document("$regex", ".*" + query + ".*").append("$options", "i"));

        List<Document> results = mongoHandler.findDocuments("aggregated_data", filter)
                .limit(10)
                .into(new ArrayList<>());

        List<String> suggestions = new ArrayList<>();
        for (Document doc : results) {
            suggestions.add(doc.getString("value"));
        }
        ctx.json(suggestions);
    }
}
