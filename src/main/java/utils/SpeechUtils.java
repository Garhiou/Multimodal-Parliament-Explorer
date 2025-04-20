package utils;

import database.MongoDatabaseHandler;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Implementiert von Luana Schäfer

public class SpeechUtils {


    /**
     * Parst den Parameter für die aktuelle Seite. Gibt 1 zurück, wenn ungültig oder null.
     *
     * @param pageParam Seitenparameter als String
     * @return gültiger Seitenwert als Integer
     *
     * Implementiert von Luana Schäfer
     */
    public static int parsePage(String pageParam) {
        if (pageParam != null && pageParam.matches("\\d+")) {  // Prüfen, ob der Parameter nur aus Ziffern besteht
            return Integer.parseInt(pageParam);
        }
        return 1;
    }


    /**
     * Prüft, ob eine Suchanfrage gültig ist (nicht null, nicht leer, mindestens 2 Zeichen).
     *
     * @param query Die Suchanfrage
     * @return true, wenn die Suche gültig ist
     *
     * Implementiert von Luana Schäfer
     */
    public static boolean isValidSearchQuery(String query) {
        return query != null && !query.isBlank() && query.length() >= 2;
    }


    /**
     * Baut einen MongoDB-$text-Suchfilter aus der übergebenen Suchanfrage.
     *
     * @param searchQuery Die gesuchte Zeichenkette
     * @return MongoDB-Dokument als Ergebnisse
     *
     * Implementiert von Luana Schäfer
     */
    public static Document buildSearchFilter(String searchQuery) {
        return new Document("$text", new Document("$search", searchQuery));

    }


    /**
     * Reichert eine Rede mit zusätzlichen Informationen wie Partei und Sitzungstitel an.
     *
     * @param speech Das ursprüngliche Rede-Dokument
     * @param mongoHandler Die MongoDB-Verbindung
     *
     * Implementiert von Luana Schäfer
     */
    public static void enrichSpeechWithMetadata(Document speech, MongoDatabaseHandler mongoHandler) {
        String speakerName = speech.getString("speaker");

        if (speakerName != null && !speakerName.trim().isEmpty()) {
            Document mp = getRednerBySpeakerName(speakerName, mongoHandler);

            if (mp != null) {
                if (mp.containsKey("party")) {
                    speech.append("party", mp.getString("party")); // Partei zur Rede hinzufügen
                    System.out.println("Partei: " + mp.getString("party"));
                } else {
                    speech.append("party", "Keine Angabe");
                }
            } else {
                speech.append("party", "Keine Angabe");
                System.out.println("Kein Abgeordneter gefunden für: " + speakerName);
            }
        } else {
            speech.append("party", "Keine Angabe");
            System.out.println("`speaker`-Feld ist leer.");
        }

        // 👉 Das bleibt unverändert:
        speech.append("sessionTitle", extractSessionTitle(speech)); // Sitzungstitel ergänzen
    }


    /**
     * Extrahiert den Sitzungstitel aus dem Rede-Dokument.
     *
     * @param rede Das Rede-Dokument
     * @return Der formatierte Sitzungstitel
     *
     * Implementiert von Luana Schäfer
     */
    public static String extractSessionTitle(Document rede) {
        Document protocol = (Document) rede.get("protocol");
        if (protocol != null && protocol.containsKey("index")) {
            return "Sitzung " + protocol.getString("index");
        }
        return "Unbekannte Sitzung";
    }


    /**
     * Formatiert den Text einer Rede. Bzw. reduziert TextContent auf notwendige Daten.
     *
     * @param rede Das Rede-Dokument
     * @return Liste von Textabschnitten mit Typ
     *
     * Implementiert von Luana Schäfer
     */
    public static List<Map<String, String>> formatSpeechText(Document rede) {
        List<Map<String, String>> formattedText = new ArrayList<>();

        if (rede.containsKey("textContent")) {
            List<Document> textContent = (List<Document>) rede.get("textContent");

            for (Document textBlock : textContent) {
                Map<String, String> textEntry = new HashMap<>();
                textEntry.put("text", textBlock.getString("text")); // Fügt Text ein

                if (textBlock.containsKey("type")) {
                    textEntry.put("type", textBlock.getString("type")); // Füge Type ein
                } else {
                    textEntry.put("type", "text");
                }
                formattedText.add(textEntry);
            }
        }
        return formattedText;
    }


    /**
     * Sucht einen Abgeordneten anhand eines Speaker-Namens in der Datenbank.
     *
     * @param speakerName  Name des Redners
     * @param mongoHandler Datenbankzugriff
     * @return Dokument des Abgeordneten oder null
     *
     * Implementiert von Luana Schäfer
     */
    public static Document getRednerBySpeakerName(String speakerName, MongoDatabaseHandler mongoHandler) {
        if (speakerName == null || speakerName.isBlank()) return null;

        String[] nameParts = speakerName.trim().split("\\s+");

        String firstName = "";
        String lastName = "";

        if (nameParts.length > 1) {
            firstName = nameParts[0];
            lastName = nameParts[nameParts.length - 1];
        } else {
            lastName = nameParts[0];
        }

        // Exakter Match auf Vorname & Nachname
        Document mpFilter = new Document("vorname", firstName).append("name", lastName);
        Document mp = mongoHandler.findDocuments("abgeordnete", mpFilter).first();

        if (mp != null) return mp;

        // Fallback: Regex-Match
        String fullName = speakerName.trim().replaceAll("\\s+", " ");
        Document regexFilter = new Document("$expr", new Document("$regexMatch", new Document()
                .append("input", new Document("$concat", List.of("$vorname", " ", "$name")))
                .append("regex", "^" + fullName + "$")
                .append("options", "i")
        ));

        return mongoHandler.findDocuments("abgeordnete", regexFilter).first();
    }



    /**
     * Gibt die Bild-URL für einen Redner zurück.
     *
     * @param redner Dokument des Abgeordneten
     * @param mongoHandler MongoDB-Handler
     * @return Bild-URL oder null
     *
     * Implementiert von Luana Schäfer
     */
    public static String getBildUrlForRedner(Document redner, MongoDatabaseHandler mongoHandler) {
        if (redner == null || !redner.containsKey("id")) return null;

        // Bild-Dokument aus der Datenbank abrufen
        Document bild = mongoHandler.findDocuments("bilder", new Document("_id", redner.getString("id"))).first();
        if (bild != null && bild.containsKey("pictures")) {
            List<Document> bilderListe = (List<Document>) bild.get("pictures");
            if (!bilderListe.isEmpty()) {
                Document erstesBild = bilderListe.get(0);
                return erstesBild.getString("hq_picture") != null ? erstesBild.getString("hq_picture") : erstesBild.getString("hp_picture");
            }
        }
        return null;
    }


    /**
     * Ruft den Videopfad für eine Rede anhand ihrer ID ab.
     *
     * @param redeId Die ID der Rede
     * @param mongoHandler MongoDB-Handler
     * @return Pfad zum Video oder null
     *
     * Implementiert von Luana Schäfer
     */
    public static String getVideoUrlForRede(String redeId, MongoDatabaseHandler mongoHandler) {
        if (redeId == null) return null;

        Document videoDoc = mongoHandler.findDocuments("VideoLinks", new Document("RedeId", redeId)).first();
        return (videoDoc != null && videoDoc.containsKey("videoPath")) ? videoDoc.getString("videoPath") : null;
    }


    /**
     * Extrahiert Named Entities aus dem übergebenen Rede-Dokument.
     *
     * @param rede Das Rede-Dokument
     * @return Liste von Map-Einträgen mit Named Entities
     *
     * Implementiert von Luana Schäfer
     */
    public static List<Map<String, Object>> extractNamedEntities(Document rede) {
        List<Map<String, Object>> namedEntities = new ArrayList<>();

        // Prüfen, ob das Dokument NLP-Analysen enthält
        if (rede.containsKey("nlpResults")) {
            Document nlpResults = rede.get("nlpResults", Document.class);

            // Prüfen, ob in nlpResults Named Entities vorhanden sind
            if (nlpResults.containsKey("namedEntities")) {
                Object namedEntitiesObj = nlpResults.get("namedEntities");


                if (namedEntitiesObj instanceof List<?>) {
                    List<?> entities = (List<?>) namedEntitiesObj;

                    for (Object obj : entities) {
                        if (obj instanceof Document) {
                            Document entity = (Document) obj;
                            Map<String, Object> entityMap = new HashMap<>();

                            entityMap.put("text", entity.getString("text"));
                            entityMap.put("type", entity.getString("type"));
                            entityMap.put("begin", entity.getInteger("begin", -1));
                            entityMap.put("end", entity.getInteger("end", -1));

                            namedEntities.add(entityMap);
                        } else {
                            System.out.println("Ungültiges Named Entity-Objekt: " + obj);
                        }
                    }
                } else {
                    System.out.println("namedEntities ist kein gültiges Array-Format: " + namedEntitiesObj);
                }
            } else {
                System.out.println("Kein namedEntities-Array in nlpResults gefunden!");
            }
        } else {
            System.out.println("Kein nlpResults in diesem Dokument gefunden!");
        }

        System.out.println("Extrahierte Named Entities: " + namedEntities);
        return namedEntities;
    }

    /**
     * Extrahiert POS-Tags aus dem übergebenen Rede-Dokument.
     *
     * @param rede Das Rede-Dokument
     * @return Liste von Tokens mit Wortart (POS), Text und Positionen
     *
     * Implementiert von Luana Schäfer
     */
    public static List<Map<String, Object>> extractPOSTags(Document rede) {
        List<Map<String, Object>> posTags = new ArrayList<>();

        // Prüfen, ob NLP-Analysen vorhanden sind
        if (rede.containsKey("nlpResults") && rede.get("nlpResults") instanceof Document) {
            Document nlpResults = rede.get("nlpResults", Document.class);

            // Prüfen, ob Token-Analyse vorhanden ist
            if (nlpResults.containsKey("tokens") && nlpResults.get("tokens") instanceof List) {
                List<Document> tokens = (List<Document>) nlpResults.get("tokens");

                for (Document token : tokens) {
                    String posTag = token.getString("pos");
                    if (posTag != null && isValidPOS(posTag)) {
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("text", token.getString("text"));
                        tokenData.put("pos", posTag);
                        tokenData.put("begin", token.getInteger("begin", -1));
                        tokenData.put("end", token.getInteger("end", -1));

                        posTags.add(tokenData);
                    }
                }
            } else {
                System.out.println("Keine 'tokens' in nlpResults gefunden!");
            }
        } else {
            System.out.println("Kein 'nlpResults' in diesem Dokument gefunden!");
        }
        return posTags;
    }


    /**
     * Prüft, ob ein POS-Tag einer erlaubten Kategorie angehört.
     *
     * @param pos POS-Tag
     * @return true, wenn gültig
     *
     * Implementiert von Luana Schäfer
     */
    private static boolean isValidPOS(String pos) {
        return List.of("NN", "NE", "ADJ", "ADJA", "ADJD", "ADV", "VVFIN", "VVINF", "VVIZU",
                        "VVPP", "VAFIN", "VAIMP", "VAINF", "VAPP", "VMFIN", "VMINF", "VMPP")
                .contains(pos);
    }


    /**
     * Extrahiert Sentiment-Werte aus einer Rede.
     *
     * @param rede Das Rede-Dokument
     * @return Liste von Sentiments
     *
     * Implementiert von Luana Schäfer
     */
    public static List<Map<String, Object>> extractSentiments(Document rede) {
        List<Map<String, Object>> sentiments = new ArrayList<>();

        if (rede.containsKey("nlpResults") && rede.get("nlpResults") instanceof Document) {
            Document nlpResults = rede.get("nlpResults", Document.class);

            if (nlpResults.containsKey("sentiment") && nlpResults.get("sentiment") instanceof List) {
                List<Document> sentimentList = (List<Document>) nlpResults.get("sentiment");

                // Der erste Eintrag (index 0) ist die Gesamtbewertung, überspringen
                for (int i = 1; i < sentimentList.size(); i++) {
                    Document sentimentDoc = sentimentList.get(i);

                    Map<String, Object> sentimentEntry = new HashMap<>();
                    sentimentEntry.put("sentiment", sentimentDoc.getDouble("sentiment"));
                    sentimentEntry.put("subjectivity", sentimentDoc.getDouble("subjectivity"));
                    sentimentEntry.put("begin", sentimentDoc.getInteger("begin"));
                    sentimentEntry.put("end", sentimentDoc.getInteger("end"));

                    sentiments.add(sentimentEntry);
                }
            } else {
                System.out.println("Keine Sentiment-Daten gefunden!");
            }
        } else {
            System.out.println("Kein 'nlpResults'-Objekt in diesem Dokument gefunden!");
        }

        System.out.println("Extrahierte Sentiments: " + sentiments);
        return sentiments;
    }


    /**
     * Berechnet durchschnittliche Scores pro Topic innerhalb einer Rede.
     *
     * @param speech Die Rede
     * @return Liste mit Topics, Durchschnittsscore und Zählung
     *
     * Implementiert von Luana Schäfer
     */
    public static List<Document> getTopicsFromSpeech(Document speech) {
        List<Document> topics = new ArrayList<>();

        if (speech.containsKey("nlpResults") && speech.get("nlpResults") instanceof Document) {
            Document nlpResults = speech.get("nlpResults", Document.class);

            if (nlpResults.containsKey("topics") && nlpResults.get("topics") instanceof List) {
                List<Document> topicList = (List<Document>) nlpResults.get("topics");

                Map<String, Double> topicScores = new HashMap<>();
                Map<String, Integer> topicCounts = new HashMap<>();

                for (Document topic : topicList) {
                    String topicName = topic.getString("value");
                    double score;
                    Object scoreObj = topic.get("score");

                    if (scoreObj instanceof Double) {
                        score = (Double) scoreObj;  // Falls es bereits ein Double ist
                    } else if (scoreObj instanceof String) {
                        try {
                            score = Double.parseDouble((String) scoreObj);  // Falls es ein String ist, in Double umwandeln
                        } catch (NumberFormatException e) {
                            System.err.println("Fehler bei der Umwandlung von 'score': " + scoreObj);
                            score = 0.0;
                        }
                    } else {
                        score = 0.0;
                    }

                    topicScores.put(topicName, topicScores.getOrDefault(topicName, 0.0) + score);
                    topicCounts.put(topicName, topicCounts.getOrDefault(topicName, 0) + 1);
                }

                for (String topicName : topicScores.keySet()) {
                    double avgScore = topicScores.get(topicName) / topicCounts.get(topicName);
                    topics.add(new Document("topic", topicName)
                            .append("averageScore", avgScore)
                            .append("count", topicCounts.get(topicName)));
                }
            }
        }
        return topics;
    }


    /**
     * Holt alle eindeutigen Parteibezeichnungen aus der Datenbank und normalisiert diese.
     *
     * @param mongoHandler MongoDB-Zugriff
     * @return Liste bereinigter Parteiennamen
     *
     * Implementiert von Luana Schäfer
     */

    public static List<String> getUniqueParties(MongoDatabaseHandler mongoHandler) {
        try {
            List<String> rawParties = mongoHandler.getDatabase()
                    .getCollection("abgeordnete")
                    .distinct("party", String.class)
                    .into(new ArrayList<>());

            // Bereinigung und Zusammenfassung der Namen
            Map<String, String> normalizedParties = new HashMap<>();
            for (String rawParty : rawParties) {
                String normalized = normalizePartyName(rawParty);
                normalizedParties.put(normalized, normalized);
            }

            return new ArrayList<>(normalizedParties.values());
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Parteien: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gibt alle vorhandenen Sitzungstitel zurück.
     *
     * @param mongoHandler MongoDB-Verbindung
     * @return Liste eindeutiger Sitzungsbezeichner
     *
     * Implementiert von Luana Schäfer
     */
    public static List<String> getUniqueSessions(MongoDatabaseHandler mongoHandler) {
        try {
            List<String> rawSessions = mongoHandler.getDatabase()
                    .getCollection("rede")
                    .distinct("protocol.index", String.class)
                    .into(new ArrayList<>());

            return rawSessions.stream().distinct().toList(); // Duplikate entfernen
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Sitzungen: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gibt alle in den Reden vorkommenden Topics zurück.
     *
     * @param mongoHandler MongoDB-Verbindung
     * @return Liste eindeutiger Topic-Bezeichnungen
     *
     * Implementiert von Luana Schäfer
     */
    public static List<String> getUniqueTopics(MongoDatabaseHandler mongoHandler) {
        try {
            List<String> rawTopics = mongoHandler.getDatabase()
                    .getCollection("rede")
                    .distinct("nlpResults.topics.value", String.class)
                    .into(new ArrayList<>());

            return rawTopics.stream().distinct().toList();
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Themen: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static final Map<String, String> PARTY_MAPPING = Map.ofEntries(
            Map.entry("GRÜNE", "BÜNDNIS 90/DIE GRÜNEN"),
            Map.entry("DIE GRÜNEN", "BÜNDNIS 90/DIE GRÜNEN"),
            Map.entry("BÜNDNIS 90/DIE GRÜNEN", "BÜNDNIS 90/DIE GRÜNEN"),
            Map.entry("DIE GRÜNEN/BÜNDNIS 90", "BÜNDNIS 90/DIE GRÜNEN"),


            Map.entry("CDU/CSU", "CDU/CSU"),
            Map.entry("CSU", "CDU/CSU"),
            Map.entry("CSUS", "CDU/CSU"),

            Map.entry("PDS/LL", "PDS"),

            Map.entry("Plos", "PIRATEN")
    );


    /**
     * Normalisiert Parteiennamen mit einer Mapping-Tabelle
     *
     * @param rawPartyName Der ursprüngliche Eintrag
     * @return Vereinheitlichter Parteienname
     *
     * Implementiert von Luana Schäfer
     */
    public static String normalizePartyName(String rawPartyName) {
        return PARTY_MAPPING.getOrDefault(rawPartyName, rawPartyName);
    }

}
