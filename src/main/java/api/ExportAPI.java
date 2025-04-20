package api;

import latex.MultiProtokollDocument;
import latex.ProtokollDocument;
import latex.RednerRedenDocument;
import latex.ThemaRedenDocument;
import latex.XMI.RednerRedenXMIExporter;
import latex.XMI.ThemaRedenXMIExporter;
import latex.XMI.XMIExporter;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.bson.Document;
import database.MongoDatabaseHandler;
import com.mongodb.client.FindIterable;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * API-Klasse für den Export von Parlamentsdebatten in verschiedene Formate.
 * Stellt Endpunkte für den Export von Protokollen, Reden und thematischen Inhalten
 * als PDF und XMI zur Verfügung.
 *
 * @author Maik Kitzmann
 */
@OpenAPIDefinition(info = @Info(title = "Parliament Export API", version = "1.0", description = "API zum Export von Parlamentsdebatten"))
@Tag(name = "Export API", description = "Endpoints für den Export von Parlamentsdebatten")
public class ExportAPI {
    // In ParliamentAPI aufgerufen
    private final MongoDatabaseHandler mongoHandler;


    /**
     * Konstruktor für die ExportAPI.
     *
     * @param mongoHandler Der Handler für die MongoDB-Datenbank
     * @author Maik Kitzmann
     */
    public ExportAPI(MongoDatabaseHandler mongoHandler) {
        this.mongoHandler = mongoHandler;
        System.out.println("ExportAPI wurde initialisiert.");
    }

    /**
     * Registriert alle API-Routen für den Export bei der Javalin-App.
     * Enthält Endpunkte für PDF- und XMI-Export von Protokollen, Rednerreden und thematischen Inhalten.
     *
     * @param app Die Javalin-App, bei der die Routen registriert werden sollen
     * @author Maik Kitzmann
     */
    public void registerRoutes(Javalin app) {
        app.get("/api/export/pdf/protokoll/{id}", this::exportProtokollAsPDF);
        app.get("/api/export/pdf/protokolle", this::exportProtokollePDF);
        app.get("/api/export/pdf/redner/{name}", this::exportRednerRedenPDF);
        app.get("/api/export/pdf/thema/{thema}", this::exportThemaRedenAsPDF);

        // XMI-Export-Routen
        app.get("/api/export/xmi/protokoll/{id}", this::exportProtokollAsXMI);
        app.get("/api/export/xmi/protokolle", this::exportProtokolleAsXMI);
        app.get("/api/export/xmi/redner/{name}", this::exportRednerRedenAsXMI);
        app.get("/api/export/xmi/thema/{thema}", this::exportThemaRedenAsXMI);

        System.out.println("ExportAPI-Routen wurden registriert.");
    }

    /**
     * Exportiert ein einzelnes Protokoll als PDF.
     * Extrahiert alle Reden zum angegebenen Protokoll, filtert Duplikate
     * und generiert ein LaTeX-Dokument, das als PDF ausgegeben wird.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert ein Protokoll als PDF", description = "Generiert ein PDF für ein bestimmtes Protokoll mit allen Reden und Metadaten")
    private void exportProtokollAsPDF(Context ctx) {
        String id = ctx.pathParam("id");
        System.out.println("Exportiere Protokoll mit ID: " + id);

        Path exportDir = null;

        try {
            System.out.println("Suche Reden für Protokoll mit ID: " + id);

            // Finde alle Reden für Protokoll
            Document filter = new Document("protocol.index", id);
            FindIterable<Document> reden = mongoHandler.findDocuments("rede", filter);

            String exportId = UUID.randomUUID().toString();
            exportDir = Files.createTempDirectory("latex_export_" + exportId);
            System.out.println("Temporäres Verzeichnis erstellt: " + exportDir);

            List<Document> redenList = new ArrayList<>();
            reden.forEach(redenList::add);

            // Wenn Empty Fallback
            if (redenList.isEmpty()) {
                System.out.println("Keine Reden für Protokoll mit ID " + id + " gefunden!");
                ctx.status(404).result("Keine Reden für dieses Protokoll gefunden");
                return;
            }

            System.out.println("Gefunden: " + redenList.size() + " Reden für das Protokoll");

            // Entferne doppelte Reden (behalte nur die längste Rede pro Redner weil in DB doppelt)
            System.out.println("Bereinige doppelte Reden...");
            Map<String, Document> bestRedePerRedner = new HashMap<>();

            for (Document rede : redenList) {
                String redner = rede.getString("speaker");
                if (redner == null) continue;

                List<Document> textContent = (List<Document>) rede.get("textContent");
                int textSize = (textContent != null) ? textContent.size() : 0;

                Document bestRede = bestRedePerRedner.get(redner);
                if (bestRede == null) {
                    bestRedePerRedner.put(redner, rede);
                } else {
                    List<Document> bestTextContent = (List<Document>) bestRede.get("textContent");
                    int bestSize = (bestTextContent != null) ? bestTextContent.size() : 0;

                    if (textSize > bestSize) {
                        bestRedePerRedner.put(redner, rede);
                    }
                }
            }

            redenList = new ArrayList<>(bestRedePerRedner.values());
            System.out.println("Nach Bereinigung: " + redenList.size() + " eindeutige Reden");


            // Lade Protokollinformationen
            Document ersteRede = redenList.get(0);
            Document protokollInfo = (Document) ersteRede.get("protocol");

            //Keine Infos Fallback
            if (protokollInfo == null) {
                System.out.println("Protokollinformationen fehlen in den Reden!");
                ctx.status(500).result("Protokollinformationen nicht vorhanden");
                return;
            }

            String titel = protokollInfo.getString("title");
            String datum = protokollInfo.getString("date");

            System.out.println("Protokoll gefunden: " + titel + " vom " + datum);

            // Erstelle das LaTeX-Dokument
            System.out.println("Erstelle Protokoll-Dokument...");
            ProtokollDocument protokollDocument = new ProtokollDocument(titel, datum, redenList, exportDir, mongoHandler);

            System.out.println("Generiere LaTeX-Code...");
            String latexCode = protokollDocument.toTex();
            System.out.println("LaTeX-Code generiert, Länge: " + latexCode.length() + " Zeichen");

            // Generiere PDF aus LaTeX
            System.out.println("Starte PDF-Generierung mit LaTeX...");
            byte[] pdfBytes = generatePDFFromLaTeX(latexCode, exportDir);
            System.out.println("PDF erfolgreich generiert, Größe: " + pdfBytes.length + " Bytes");

            // Sende PDF als Antwort
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "inline; filename=\"protokoll_" + id + ".pdf\"");
            ctx.result(pdfBytes);
            System.out.println("PDF-Antwort gesendet.");

        } catch (Exception e) {
            System.err.println("Fehler beim PDF-Export: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim PDF-Export: " + e.getMessage());
        } finally {
            // Temp Verzeichnis löschen
            if (exportDir != null) {
                System.out.println("Lösche temporäres Verzeichnis: " + exportDir);
                try {
                    deleteDirectory(exportDir);
                } catch (Exception e) {
                    System.err.println("Fehler beim Löschen des temporären Verzeichnisses: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Exportiert mehrere oder alle Protokolle als ein kombiniertes PDF.
     * Die zu exportierenden Protokolle können durch den Parameter 'ids' spezifiziert werden.
     * Ohne Parameter werden alle verfügbaren Protokolle exportiert.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert mehrere Protokolle als PDF", description = "Generiert ein PDF mit allen ausgewählten Protokollen")
    private void exportProtokollePDF(Context ctx) {
        String idsParam = ctx.queryParam("ids");
        System.out.println("Exportiere mehrere Protokolle. Parameter ids: " + idsParam);

        try {
            List<String> protokollIds;

            // Entweder bestimmte IDs oder alle Protokolle exportieren
            if (idsParam != null && !idsParam.isEmpty()) {
                protokollIds = Arrays.asList(idsParam.split(","));
                System.out.println("Exportiere " + protokollIds.size() + " ausgewählte Protokolle.");
            } else {
                System.out.println("Exportiere alle Protokolle...");

                // Finde alle eindeutigen Protokoll-IDs
                Set<String> uniqueProtocolIds = new HashSet<>();
                FindIterable<Document> allReden = mongoHandler.findDocuments("rede", new Document());

                // Daten protocol aus rede Einträge holen
                for (Document rede : allReden) {
                    Document protocol = (Document) rede.get("protocol");
                    if (protocol != null) {
                        String protocolIndex = protocol.getString("index");
                        if (protocolIndex != null && !protocolIndex.isEmpty()) {
                            uniqueProtocolIds.add(protocolIndex);
                        }
                    }
                }

                protokollIds = new ArrayList<>(uniqueProtocolIds);
                System.out.println("Gefunden: " + protokollIds.size() + " eindeutige Protokolle.");
            }

            if (protokollIds.isEmpty()) {
                System.out.println("Keine Protokolle gefunden!");
                ctx.status(404).result("Keine Protokolle gefunden");
                return;
            }
            // Erstelle Multi-Protokoll-Dokument
            MultiProtokollDocument multiDocument = new MultiProtokollDocument(protokollIds, mongoHandler);

            System.out.println("Generiere LaTeX-Code für " + protokollIds.size() + " Protokolle...");
            String latexCode = multiDocument.toTex();
            System.out.println("LaTeX-Code generiert, Länge: " + latexCode.length() + " Zeichen");

            // Generiere PDF
            System.out.println("Starte PDF-Generierung mit LaTeX...");
            Path exportDir = Files.createTempDirectory("multi_protokoll_" + UUID.randomUUID().toString());
            byte[] pdfBytes = generatePDFFromLaTeX(latexCode, exportDir);
            System.out.println("PDF erfolgreich generiert, Größe: " + pdfBytes.length + " Bytes");

            // Sende PDF als Antwort
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "inline; filename=\"protokolle.pdf\"");
            ctx.result(pdfBytes);
            System.out.println("PDF-Antwort gesendet.");

            deleteDirectory(exportDir);

        } catch (Exception e) {
            System.err.println("Fehler beim PDF-Export: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim PDF-Export: " + e.getMessage());
        }
    }

    /**
     * Exportiert alle Reden eines bestimmten Redners als PDF.
     * Lädt die Redner-Informationen aus der Datenbank anhand des Vor- und Nachnamens,
     * sucht alle Reden des Redners und erstellt ein PDF-Dokument mit den gefundenen Reden.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert alle Reden eines Redners als PDF", description = "Generiert ein PDF mit allen Reden eines bestimmten Abgeordneten")
    private void exportRednerRedenPDF(Context ctx) {
        try {
            // Dekodiere den URL-Parameter (vorname%20nachname)
            String encodedName = ctx.pathParam("name");
            String rednerName = URLDecoder.decode(encodedName, "UTF-8");

            System.out.println("Exportiere Reden des Redners: " + rednerName);

            // Trenne Vor- und Nachname (falls möglich)
            String vorname = "";
            String nachname = rednerName;

            if (rednerName.contains(" ")) {
                String[] nameParts = rednerName.split(" ", 2);
                vorname = nameParts[0];
                nachname = nameParts[1];
            }

            // Suche nach Redner in der Datenbank
            Document rednerFilter = new Document();

            // Wenn Vor- und Nachname vorhanden sind, suche gezielt
            if (!vorname.isEmpty()) {
                rednerFilter.append("vorname", vorname)
                        .append("name", nachname);
            } else {
                // Ansonsten prüfe nur den Nachnamen
                rednerFilter.append("name", nachname);
            }

            System.out.println("Suche Redner mit Filter: " + rednerFilter.toJson());
            Document redner = mongoHandler.findDocuments("abgeordnete", rednerFilter).first();

            if (redner == null) {
                System.out.println("Redner '" + rednerName + "' nicht gefunden!");
                ctx.status(404).result("Redner nicht gefunden");
                return;
            }

            // Bestimme vollständigen Namen für die Suche nach Reden
            String rednerVorname = redner.getString("vorname") != null ? redner.getString("vorname") : "";
            String rednerNachname = redner.getString("name") != null ? redner.getString("name") : "";
            String rednerFullName = rednerVorname + " " + rednerNachname;
            rednerFullName = rednerFullName.trim();

            System.out.println("Redner gefunden: " + rednerFullName);

            // Suche mit dem vollständigen Namen
            System.out.println("Suche Reden von " + rednerFullName + " als speaker...");
            FindIterable<Document> reden = mongoHandler.findDocuments("rede", new Document("speaker", rednerFullName));
            List<Document> redenList = new ArrayList<>();
            reden.forEach(redenList::add);

            // Fallback: Keine Reden gefunden
            if (redenList.isEmpty()) {
                System.out.println("Keine Reden für " + rednerFullName + " gefunden!");
                ctx.status(404).result("Keine Reden für diesen Redner gefunden");
                return;
            }
            System.out.println("Gefunden: " + redenList.size() + " Reden von " + rednerFullName);

            // Filtere Reden nach vorhandenem textContent und wähle nur die besten für jedes Protokoll
            List<Document> validReden = new ArrayList<>();
            Map<String, Document> bestRedePerProtokoll = new HashMap<>();

            for (Document rede : redenList) {
                List<Document> textContent = (List<Document>) rede.get("textContent");
                if (textContent == null || textContent.isEmpty()) {
                    continue;
                }

                Document protocol = (Document) rede.get("protocol");
                if (protocol == null || protocol.getString("index") == null) continue;

                String protocolIndex = protocol.getString("index");
                int textSize = textContent.size();

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

            validReden = new ArrayList<>(bestRedePerProtokoll.values());

            if (validReden.isEmpty()) {
                System.out.println("Keine gültigen Reden mit Textinhalt für " + rednerFullName + " gefunden!");
                ctx.status(404).result("Keine gültigen Reden mit Textinhalt gefunden");
                return;
            }
            System.out.println("Nach Filterung: " + validReden.size() + " gültige Reden mit Textinhalt");

            // Erstelle LaTeX-Dokument
            Path exportDir = Files.createTempDirectory("redner_reden_" + UUID.randomUUID().toString());
            RednerRedenDocument rednerDocument = new RednerRedenDocument(redner, validReden, exportDir, mongoHandler);

            System.out.println("Generiere LaTeX-Code...");
            String latexCode = rednerDocument.toTex();
            System.out.println("LaTeX-Code generiert, Länge: " + latexCode.length() + " Zeichen");

            // Generiere PDF
            System.out.println("Starte PDF-Generierung mit LaTeX...");
            byte[] pdfBytes = generatePDFFromLaTeX(latexCode, exportDir);
            System.out.println("PDF erfolgreich generiert, Größe: " + pdfBytes.length + " Bytes");

            // Sende PDF als Antwort
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "inline; filename=\"reden_" + escapeFileName(rednerFullName) + ".pdf\"");
            ctx.result(pdfBytes);
            System.out.println("PDF-Antwort gesendet.");

            deleteDirectory(exportDir);

        } catch (Exception e) {
            System.err.println("Fehler beim PDF-Export: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim PDF-Export: " + e.getMessage());
        }
    }


    /**
     * Exportiert alle Reden zu einem bestimmten Thema als PDF.
     * Führt eine Volltextsuche und eine Analyse der NLP-Topics durch,
     * um Reden zum angegebenen Thema zu finden.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert Reden zu einem Thema als PDF", description = "Generiert ein PDF mit allen Reden zu einem bestimmten Thema")
    private void exportThemaRedenAsPDF(Context ctx) {
        String thema = ctx.pathParam("thema");
        System.out.println("Exportiere Reden zum Thema: " + thema);

        try {
            System.out.println("Suche Reden zum Thema " + thema + "...");
            List<Document> redenList = new ArrayList<>();

            // Volltextsuche nach dem Thema
            Document themaFilter = new Document("$text", new Document("$search", thema));
            FindIterable<Document> textSearchResults = mongoHandler.findDocuments("rede", themaFilter);
            textSearchResults.forEach(redenList::add);
            System.out.println("Gefunden durch Text-Suche: " + redenList.size() + " Reden zum Thema " + thema);

            System.out.println("Suche ergänzend anhand der NLP-Topics...");
            FindIterable<Document> allReden = mongoHandler.findDocuments("rede", new Document());

            for (Document rede : allReden) {
                // Überspringe Reden ohne Text
                List<Document> textContent = (List<Document>) rede.get("textContent");
                if (textContent == null || textContent.isEmpty()) {
                    continue;
                }

                // Überspringe Reden die schon in der Liste sind
                boolean alreadyInList = false;
                String redeId = rede.get("_id").toString();
                for (Document existingRede : redenList) {
                    String existingId = existingRede.get("_id").toString();
                    if (existingId.equals(redeId)) {
                        alreadyInList = true;
                        break;
                    }
                }
                if (alreadyInList) {
                    continue;
                }

                // Prüfe NLP-Ergebnisse und berechne das Hauptthema der Rede
                Document nlpResults = (Document) rede.get("nlpResults");
                if (nlpResults != null) {
                    List<Document> topics = (List<Document>) nlpResults.get("topics");
                    if (topics != null && !topics.isEmpty()) {
                        // Map zur Summierung der Scores pro Thema
                        Map<String, Double> themaScores = new HashMap<>();

                        // Für jedes Topic den Score zum entsprechenden Thema addieren
                        for (Document topic : topics) {
                            String value = topic.getString("value");
                            if (value == null || value.isEmpty()) {
                                continue;
                            }

                            // Score extrahieren und zum entsprechenden Thema addieren
                            double score = 0.0;
                            Object scoreObj = topic.get("score");
                            if (scoreObj instanceof Double) {
                                score = (Double) scoreObj;
                            } else if (scoreObj instanceof String) {
                                try {
                                    score = Double.parseDouble((String) scoreObj);
                                } catch (NumberFormatException e) {
                                    continue;
                                }
                            } else if (scoreObj instanceof Integer) {
                                score = ((Integer) scoreObj).doubleValue();
                            } else if (scoreObj instanceof Float) {
                                score = ((Float) scoreObj).doubleValue();
                            }

                            // Addiere Score zum entsprechenden Thema
                            themaScores.put(value, themaScores.getOrDefault(value, 0.0) + score);
                        }

                        // Finde das Thema mit dem höchsten Gesamtscore
                        String hauptThema = null;
                        double maxScore = 0.0;

                        for (Map.Entry<String, Double> entry : themaScores.entrySet()) {
                            if (entry.getValue() > maxScore) {
                                maxScore = entry.getValue();
                                hauptThema = entry.getKey();
                            }
                        }

                        // Wenn das Hauptthema mit dem gesuchten Thema übereinstimmt, füge die Rede hinzu
                        if (hauptThema != null && thema.equalsIgnoreCase(hauptThema)) {
                            redenList.add(rede);
                            System.out.println("Rede hinzugefügt mit Hauptthema: " + hauptThema + " (Score: " + maxScore + ")");
                        }
                    }
                }
            }

            // Filter: nur Reden mit textContent wegen DB Duplikate
            List<Document> filteredRedenList = redenList.stream()
                    .filter(rede -> {
                        List<Document> textContent = (List<Document>) rede.get("textContent");
                        return textContent != null && !textContent.isEmpty();
                    })
                    .collect(Collectors.toList());

            if (filteredRedenList.isEmpty()) {
                System.out.println("Keine Reden zum Thema " + thema + " gefunden!");
                ctx.status(404).result("Keine Reden zu diesem Thema gefunden");
                return;
            }
            System.out.println("Insgesamt gefunden: " + filteredRedenList.size() + " Reden zum Thema " + thema);

            // Erstelle LaTeX-Dokument
            Path exportDir = Files.createTempDirectory("thema_reden_" + UUID.randomUUID().toString());
            ThemaRedenDocument themaDocument = new ThemaRedenDocument(thema, filteredRedenList, exportDir, mongoHandler);

            System.out.println("Generiere LaTeX-Code...");
            String latexCode = themaDocument.toTex();
            System.out.println("LaTeX-Code generiert, Länge: " + latexCode.length() + " Zeichen");

            // Generiere PDF
            System.out.println("Starte PDF-Generierung mit LaTeX...");
            byte[] pdfBytes = generatePDFFromLaTeX(latexCode, exportDir);
            System.out.println("PDF erfolgreich generiert, Größe: " + pdfBytes.length + " Bytes");

            // Sende PDF als Antwort
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "inline; filename=\"thema_" + escapeFileName(thema) + ".pdf\"");
            ctx.result(pdfBytes);
            System.out.println("PDF-Antwort gesendet.");

            deleteDirectory(exportDir);

        } catch (Exception e) {
            System.err.println("Fehler beim PDF-Export: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim PDF-Export: " + e.getMessage());
        }
    }

    /*
     * XMI-Export-Funktionen
     */

    /**
     * Exportiert ein einzelnes Protokoll als XMI-Datei.
     * Findet alle Reden für das angegebene Protokoll und erzeugt daraus
     * eine XMI-Datei mit allen relevanten Informationen und NLP-Annotationen.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert ein Protokoll als XMI", description = "Generiert eine XMI-Datei mit allen Reden und NLP-Annotationen eines Protokolls")
    private void exportProtokollAsXMI(Context ctx) {
        String id = ctx.pathParam("id");
        System.out.println("Exportiere Protokoll mit ID: " + id + " als XMI");

        try {
            // Finde alle Reden für Protokoll
            Document filter = new Document("protocol.index", id);
            FindIterable<Document> reden = mongoHandler.findDocuments("rede", filter);

            List<Document> redenList = new ArrayList<>();
            reden.forEach(redenList::add);

            if (redenList.isEmpty()) {
                System.out.println("Keine Reden für Protokoll mit ID " + id + " gefunden!");
                ctx.status(404).result("Keine Reden für dieses Protokoll gefunden");
                return;
            }

            System.out.println("Gefunden: " + redenList.size() + " Reden für das Protokoll");
            // Erstelle XMI-Exporter und generiere XMI
            XMIExporter xmiExporter = new XMIExporter(redenList, mongoHandler);

            System.out.println("Generiere XMI-Daten für das Protokoll...");
            String xmiContent = xmiExporter.generateXMI();
            System.out.println("XMI generiert, Länge: " + xmiContent.length() + " Zeichen");
            // Sende XMI als Antwort
            ctx.contentType("application/xml");
            ctx.header("Content-Disposition", "inline; filename=\"protokoll_" + id + ".xmi\"");
            ctx.result(xmiContent);
            System.out.println("XMI-Antwort gesendet.");

        } catch (Exception e) {
            System.err.println("Fehler beim XMI-Export des Protokolls: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim XMI-Export: " + e.getMessage());
        }
    }

    /**
     * Exportiert mehrere oder alle Protokolle als eine kombinierte XMI-Datei.
     * Die zu exportierenden Protokolle können durch den Parameter 'ids' spezifiziert werden.
     * Ohne Parameter werden alle verfügbaren Protokolle exportiert.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert mehrere Protokolle als XMI", description = "Generiert eine XMI-Datei mit allen ausgewählten Protokollen inklusive NLP-Annotationen")
    private void exportProtokolleAsXMI(Context ctx) {
        String idsParam = ctx.queryParam("ids");
        System.out.println("Exportiere mehrere Protokolle als XMI. Parameter ids: " + idsParam);

        try {
            List<String> protokollIds;

            // Bestimme zu exportierende Protokoll-IDs
            if (idsParam != null && !idsParam.isEmpty()) {
                protokollIds = Arrays.asList(idsParam.split(","));
                System.out.println("Exportiere " + protokollIds.size() + " ausgewählte Protokolle als XMI.");
            } else {
                System.out.println("Exportiere alle Protokolle als XMI...");

                // Finde alle eindeutigen Protokoll-IDs
                Set<String> uniqueProtocolIds = new HashSet<>();
                FindIterable<Document> allReden = mongoHandler.findDocuments("rede", new Document());

                for (Document rede : allReden) {
                    Document protocol = (Document) rede.get("protocol");
                    if (protocol != null) {
                        String protocolIndex = protocol.getString("index");
                        if (protocolIndex != null && !protocolIndex.isEmpty()) {
                            uniqueProtocolIds.add(protocolIndex);
                        }
                    }
                }

                protokollIds = new ArrayList<>(uniqueProtocolIds);
                System.out.println("Gefunden: " + protokollIds.size() + " eindeutige Protokolle.");
            }

            if (protokollIds.isEmpty()) {
                System.out.println("Keine Protokolle gefunden!");
                ctx.status(404).result("Keine Protokolle gefunden");
                return;
            }

            // Erstelle kombiniertes XMI für alle Protokolle
            StringBuilder combinedXMI = new StringBuilder();
            combinedXMI.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            combinedXMI.append("<xmi:XMI xmlns:xmi=\"http://www.omg.org/XMI\" ");
            combinedXMI.append("xmlns:collection=\"http:///de/parliament/collection.ecore\">\n");
            combinedXMI.append("  <collection:ProtocolCollection xmi:id=\"_collection1\">\n");

            // Füge jedes Protokoll in die Sammlung ein
            for (String protokollId : protokollIds) {
                System.out.println("Verarbeite Protokoll " + protokollId + " für XMI-Export");
                XMIExporter xmiExporter = XMIExporter.createForProtokoll(protokollId, mongoHandler);

                String protocolXMI = xmiExporter.generateXMI();

                int startIdx = protocolXMI.indexOf("<protocol:Protocol");
                int endIdx = protocolXMI.lastIndexOf("</protocol:Protocol>");

                if (startIdx >= 0 && endIdx >= 0) {
                    String protocolContent = protocolXMI.substring(startIdx, endIdx + "</protocol:Protocol>".length());
                    combinedXMI.append("    " + protocolContent.replace("\n", "\n    ") + "\n");
                }
            }

            combinedXMI.append("  </collection:ProtocolCollection>\n");
            combinedXMI.append("</xmi:XMI>");

            // Sende kombiniertes XMI als Antwort
            ctx.contentType("application/xml");
            ctx.header("Content-Disposition", "inline; filename=\"protokolle.xmi\"");
            ctx.result(combinedXMI.toString());
            System.out.println("Kombinierte XMI-Antwort gesendet.");

        } catch (Exception e) {
            System.err.println("Fehler beim XMI-Export mehrerer Protokolle: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim XMI-Export: " + e.getMessage());
        }
    }

    /**
     * Exportiert alle Reden eines bestimmten Redners als XMI-Datei.
     * Verwendet einen speziellen XMI-Exporter für Rednerreden, um eine
     * semantisch annotierte XML-Datei für NLP-Tools zu erzeugen.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert alle Reden eines Redners als XMI", description = "Generiert eine XMI-Datei mit allen Reden eines bestimmten Abgeordneten inklusive NLP-Annotationen")
    private void exportRednerRedenAsXMI(Context ctx) {
        try {
            // Dekodiere den URL-Parameter (vorname%20nachname)
            String encodedName = ctx.pathParam("name");
            String rednerName = URLDecoder.decode(encodedName, "UTF-8");

            System.out.println("Exportiere Reden des Redners: " + rednerName);

            // Trenne Vor- und Nachname (falls möglich)
            String vorname = "";
            String nachname = rednerName;

            if (rednerName.contains(" ")) {
                String[] nameParts = rednerName.split(" ", 2);
                vorname = nameParts[0];
                nachname = nameParts[1];
            }

            // Suche nach Redner in der Datenbank
            Document rednerFilter = new Document();
            if (!vorname.isEmpty()) {
                rednerFilter.append("vorname", vorname)
                        .append("name", nachname);
            } else {
                rednerFilter.append("name", nachname);
            }

            System.out.println("Suche Redner mit Filter: " + rednerFilter.toJson());
            Document redner = mongoHandler.findDocuments("abgeordnete", rednerFilter).first();

            if (redner == null) {
                System.out.println("Redner '" + rednerName + "' nicht gefunden!");
                ctx.status(404).result("Redner nicht gefunden");
                return;
            }

            // Bestimme vollständigen Namen
            String rednerVorname = redner.getString("vorname") != null ? redner.getString("vorname") : "";
            String rednerNachname = redner.getString("name") != null ? redner.getString("name") : "";
            String rednerFullName = (rednerVorname + " " + rednerNachname).trim();
            System.out.println("Redner gefunden: " + rednerFullName);

            // Suche Reden mit speaker = fullName
            FindIterable<Document> reden = mongoHandler.findDocuments("rede", new Document("speaker", rednerFullName));
            List<Document> redenList = new ArrayList<>();
            reden.forEach(redenList::add);

            if (redenList.isEmpty()) {
                System.out.println("Keine Reden für " + rednerFullName + " gefunden!");
                ctx.status(404).result("Keine Reden für diesen Redner gefunden");
                return;
            }
            System.out.println("Gefunden: " + redenList.size() + " Reden von " + rednerFullName);

            // Filtere gültige Reden (mit Textinhalt) und nur die besten pro Protokoll
            Map<String, Document> bestRedePerProtokoll = new HashMap<>();

            for (Document rede : redenList) {
                List<Document> textContent = (List<Document>) rede.get("textContent");
                if (textContent == null || textContent.isEmpty()) continue;

                Document protocol = (Document) rede.get("protocol");
                if (protocol == null || protocol.getString("index") == null) continue;

                String protocolIndex = protocol.getString("index");
                int textSize = textContent.size();

                Document bestRede = bestRedePerProtokoll.get(protocolIndex);
                if (bestRede == null) {
                    bestRedePerProtokoll.put(protocolIndex, rede);
                } else {
                    List<Document> bestTextContent = (List<Document>) bestRede.get("textContent");
                    int bestSize = bestTextContent != null ? bestTextContent.size() : 0;

                    if (textSize > bestSize) {
                        bestRedePerProtokoll.put(protocolIndex, rede);
                    }
                }
            }

            List<Document> validReden = new ArrayList<>(bestRedePerProtokoll.values());

            if (validReden.isEmpty()) {
                System.out.println("Keine gültigen Reden mit Textinhalt für " + rednerFullName + " gefunden!");
                ctx.status(404).result("Keine gültigen Reden mit Textinhalt gefunden");
                return;
            }

            System.out.println("Nach Filterung: " + validReden.size() + " gültige Reden mit Textinhalt");

            // Generiere XMI
            RednerRedenXMIExporter xmiExporter = new RednerRedenXMIExporter(redner, validReden, mongoHandler);

            System.out.println("Generiere XMI-Daten...");
            String xmiContent = xmiExporter.generateXMI();
            System.out.println("XMI generiert, Länge: " + xmiContent.length() + " Zeichen");

            // Sende XMI-Datei
            ctx.contentType("application/xml");
            ctx.header("Content-Disposition", "inline; filename=\"redner_" + escapeFileName(rednerFullName) + ".xmi\"");
            ctx.result(xmiContent);
            System.out.println("XMI-Antwort gesendet für Redner " + rednerFullName);

        } catch (Exception e) {
            System.err.println("Fehler beim XMI-Export: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim XMI-Export: " + e.getMessage());
        }
    }


    /**
     * Exportiert alle Reden zu einem bestimmten Thema als XMI-Datei.
     * Analysiert alle Reden in der Datenbank auf Relevanz zum angegebenen Thema
     * und erstellt eine XMI-Datei mit allen thematisch passenden Reden.
     *
     * @param ctx Der Javalin-Kontext für die Anfrage und Antwort
     * @author Maik Kitzmann
     */
    @Operation(summary = "Exportiert Reden zu einem Thema als XMI", description = "Generiert eine XMI-Datei mit allen Reden zu einem bestimmten Thema inklusive NLP-Annotationen")
    private void exportThemaRedenAsXMI(Context ctx) {
        String thema = ctx.pathParam("thema");
        System.out.println("Exportiere Reden zum Thema: " + thema + " als XMI");

        try {
            // Hole alle reden für deren topics
            System.out.println("Lade alle Reden für die Themenanalyse...");
            FindIterable<Document> allReden = mongoHandler.findDocuments("rede", new Document());

            List<Document> redenList = new ArrayList<>();
            allReden.forEach(redenList::add);

            if (redenList.isEmpty()) {
                System.out.println("Keine Reden in der Datenbank gefunden!");
                ctx.status(404).result("Keine Reden gefunden");
                return;
            }
            System.out.println("Gefunden: " + redenList.size() + " Reden insgesamt für die Themenanalyse");

            // ThemaRedenXMIExporter zum exporten
            ThemaRedenXMIExporter xmiExporter = new ThemaRedenXMIExporter(thema, redenList, mongoHandler);

            // Generierr XMI
            System.out.println("Generiere XMI-Daten für das Thema " + thema + "...");
            String xmiContent = xmiExporter.generateXMI();
            System.out.println("XMI generiert, Länge: " + xmiContent.length() + " Zeichen");

            // Sende fertige XMI
            ctx.contentType("application/xml");
            ctx.header("Content-Disposition", "inline; filename=\"thema_" + escapeFileName(thema) + ".xmi\"");
            ctx.result(xmiContent);
            System.out.println("XMI-Antwort für Thema " + thema + " gesendet");

        } catch (Exception e) {
            System.err.println("Fehler beim XMI-Export der Themen-Reden: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).result("Fehler beim XMI-Export: " + e.getMessage());
        }
    }

    /*
     * PDF-Generierungsmethoden
     */

    /**
     * Hauptmethode zur Generierung von PDFs aus LaTeX-Code.
     * Schreibt den übergebenen LaTeX-Code in eine temporäre Datei,
     * führt pdflatex darauf aus und gibt die erzeugte PDF-Datei zurück.
     *
     * @param latexCode Der LaTeX-Code, aus dem das PDF generiert werden soll
     * @param exportDir Das temporäre Verzeichnis für die Generierung
     * @return Die generierte PDF-Datei als Byte-Array
     * @throws IOException Wenn Fehler beim Schreiben oder Lesen von Dateien auftreten
     * @throws InterruptedException Wenn der pdflatex-Prozess unterbrochen wird
     * @author Maik Kitzmann
     */
    private byte[] generatePDFFromLaTeX(String latexCode, Path exportDir) throws IOException, InterruptedException {
        System.out.println("Starte PDF-Generierung aus LaTeX-Code (Länge: " + latexCode.length() + " Zeichen)");

        verifyLatexInstallation();

        // Schreibe LaTeX-Code in temporäre Datei
        Path texFile = exportDir.resolve("export.tex");
        Files.write(texFile, latexCode.getBytes("UTF-8"));
        System.out.println("LaTeX-Datei geschrieben: " + texFile);

        // Führe pdflatex zweimal aus (für Referenzen und Inhaltsverzeichnis)
        runPdfLatex(exportDir, texFile, 1);
        runPdfLatex(exportDir, texFile, 2);

        logGeneratedFiles(exportDir);

        // Lese generierte PDF-Datei
        Path pdfFile = exportDir.resolve("export.pdf");
        if (Files.exists(pdfFile)) {
            System.out.println("PDF-Datei gefunden und wird gelesen: " + pdfFile);
            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            System.out.println("PDF gelesen, Größe: " + pdfBytes.length + " Bytes");
            return pdfBytes;
        } else {
            handleMissingPdfFile(exportDir);
            throw new IOException("PDF-Datei wurde nicht erzeugt");
        }
    }

    /**
     * Prüft, ob LaTeX (pdflatex) korrekt im System installiert ist.
     * Führt einen Versionscheck aus und gibt Warnungen aus, falls
     * Probleme mit der Installation festgestellt werden.
     *
     * @author Maik Kitzmann
     */
    private void verifyLatexInstallation() {
        try {
            ProcessBuilder checkPb = new ProcessBuilder("pdflatex", "--version");
            Process checkProcess = checkPb.start();
            boolean checkCompleted = checkProcess.waitFor(10, TimeUnit.SECONDS);

            if (!checkCompleted || checkProcess.exitValue() != 0) {
                System.err.println("WARNUNG: pdflatex könnte nicht korrekt installiert sein!");
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()))) {
                    String version = reader.readLine();
                    System.out.println("LaTeX-Version: " + version);
                }
            }
        } catch (Exception e) {
            System.err.println("WARNUNG: pdflatex-Installation konnte nicht überprüft werden: " + e.getMessage());
        }
    }

    /**
     * Listet alle Dateien im Exportverzeichnis auf (für Debugging).
     * Gibt die Namen aller erzeugten Dateien in der Konsole aus.
     *
     * @param exportDir Das zu untersuchende Verzeichnis
     * @author Maik Kitzmann
     */
    private void logGeneratedFiles(Path exportDir) {
        try {
            System.out.println("Erzeugte Dateien im Verzeichnis " + exportDir + ":");
            Files.list(exportDir).forEach(path -> System.out.println("  " + path.getFileName()));
        } catch (IOException e) {
            System.err.println("Fehler beim Auflisten der Dateien: " + e.getMessage());
        }
    }

    /**
     * Hilft bei der Fehlersuche, wenn keine PDF-Datei erzeugt wurde.
     * Liest die LaTeX-Log-Datei und gibt relevante Fehler in der Konsole aus.
     *
     * @param exportDir Das Verzeichnis, in dem die Log-Datei zu finden ist
     * @author Maik Kitzmann
     */
    private void handleMissingPdfFile(Path exportDir) {
        Path logFile = exportDir.resolve("export.log");
        if (Files.exists(logFile)) {
            System.err.println("PDF wurde nicht erzeugt. Lese LaTeX-Log:");
            try {
                // Suche in der Log-Datei nach Fehlern und zeige sie an
                List<String> logLines = Files.readAllLines(logFile);
                logLines.stream()
                        .filter(line -> line.contains("Error") || line.contains("Fatal") || line.contains("!"))
                        .limit(20)
                        .forEach(System.err::println);
            } catch (Exception e) {
                System.err.println("Fehler beim Lesen der Log-Datei: " + e.getMessage());
            }
        }
    }

    /**
     * Führt den pdflatex-Prozess mit den richtigen Parametern aus.
     * Konfiguriert und startet den pdflatex-Prozess und überwacht dessen Ausführung.
     * Bei Problemen werden Fehlerinformationen aus der Log-Datei extrahiert.
     *
     * @param exportDir Das Arbeitsverzeichnis für pdflatex
     * @param texFile Der Pfad zur LaTeX-Datei
     * @param runNumber Die Laufnummer (1 oder 2 für wiederholte Ausführung)
     * @throws IOException Wenn Probleme beim Prozessstart oder der Dateiverarbeitung auftreten
     * @throws InterruptedException Wenn der Prozess unterbrochen wird
     * @author Maik Kitzmann
     */
    private void runPdfLatex(Path exportDir, Path texFile, int runNumber) throws IOException, InterruptedException {
        System.out.println("Führe " + (runNumber == 1 ? "ersten" : "zweiten") + " pdflatex-Durchlauf aus...");

        // Konfiguriere pdflatex-Prozess
        ProcessBuilder pb = new ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",  // Keine Interaktion erforderlich
                "-halt-on-error",            // Bei Fehlern abbrechen
                texFile.getFileName().toString());
        pb.directory(exportDir.toFile());

        Process p = pb.start();

        // Thread für die Standardausgabe des Prozesses
        StringBuilder output = new StringBuilder();
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Warning") || line.contains("Error") || line.contains("!")) {
                        System.out.println("pdflatex: " + line);
                        output.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                System.err.println("Fehler beim Lesen der Prozessausgabe: " + e.getMessage());
            }
        });
        outputThread.start();

        // Thread für die Fehlerausgabe des Prozesses
        StringBuilder errorOutput = new StringBuilder();
        Thread errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("pdflatex-Fehler: " + line);
                    errorOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                System.err.println("Fehler beim Lesen der Prozessfehlerausgabe: " + e.getMessage());
            }
        });
        errorThread.start();

        // Warte auf Prozess ende mit Timeout (5 Minuten)
        boolean completed = p.waitFor(5, TimeUnit.MINUTES);

        // Warte bis die Output-Threads fertig sind (max 10 Sekunden)
        outputThread.join(10000);
        errorThread.join(10000);

        if (!completed) {
            System.err.println("pdflatex-Prozess läuft zu lange (Timeout nach 5 Minuten)");
            p.destroyForcibly();
            throw new IOException("PDF-Generierung dauert zu lange - abgebrochen");
        }

        // Prüfe den Exit-Code des Prozesses
        int exitCode = p.exitValue();
        System.out.println("pdflatex-Durchlauf " + runNumber + " beendet mit Exit-Code: " + exitCode);

        // Bei Fehlern: Versuche mehr Informationen aus dem Log zu bekommen
        if (exitCode != 0) {
            System.err.println("Fehler im pdflatex-Durchlauf " + runNumber);
            System.err.println("Gesamte Fehlerausgabe: " + errorOutput);

            Path logFile = exportDir.resolve("export.log");
            if (Files.exists(logFile)) {
                System.err.println("Lese LaTeX-Log-Datei für weitere Details...");
                try {
                    List<String> logLines = Files.readAllLines(logFile);

                    // Suche nach Fehlern und zeige Kontext an
                    boolean errorFound = false;
                    for (int i = 0; i < logLines.size(); i++) {
                        String line = logLines.get(i);
                        if (line.contains("Error") || line.contains("Fatal") || line.contains("!")) {
                            errorFound = true;
                            System.err.println("Log-Fehler: " + line);
                            for (int j = Math.max(0, i-2); j < Math.min(logLines.size(), i+3); j++) {
                                if (j != i) {
                                    System.err.println("Kontext: " + logLines.get(j));
                                }
                            }
                        }
                    }

                    if (!errorFound) {
                        System.err.println("Keine spezifischen Fehler im Log gefunden");
                    }
                } catch (Exception e) {
                    System.err.println("Fehler beim Lesen der Log-Datei: " + e.getMessage());
                }
            }
        }
    }

    /*
     * Hilfsmethoden
     */

    /**
     * Löscht ein Verzeichnis rekursiv mit allen enthaltenen Dateien und Unterverzeichnissen.
     * Verwendet einen rekursiven Ansatz, bei dem zuerst die tiefsten Dateien gelöscht werden.
     *
     * @param dir Das zu löschende Verzeichnis
     * @author Maik Kitzmann
     */
    private void deleteDirectory(Path dir) {
        try {
            if (dir == null || !Files.exists(dir)) {
                return;
            }

            System.out.println("Lösche Verzeichnis: " + dir);
            // Lösche Dateien und Verzeichnisse (tiefste zuerst)
            Files.walk(dir)
                    .sorted((p1, p2) -> -p1.compareTo(p2))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("  Fehler beim Löschen von " + path + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Fehler beim Löschen des Verzeichnisses " + dir + ": " + e.getMessage());
        }
    }

    // Macht Dateinamen sicher für das Dateisystem
    private String escapeFileName(String text) {
        if (text == null) return "";
        return text.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
