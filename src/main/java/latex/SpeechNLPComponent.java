package latex;

import latex.impl.LaTeXComponent;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Komponente zur Visualisierung von NLP-Analyseergebnissen einer Rede.
 * Generiert LaTeX-Code für verschiedene Aspekte der Sprachanalyse wie
 * Named Entities, Sentiment-Analyse, Themen (Topics) und Wortarten (POS-Tags).
 * Nutzt TikZ zur Erstellung von Diagrammen und Visualisierungen.
 *
 * @author Maik Kitzmann
 */
class SpeechNLPComponent implements LaTeXComponent {
    private final Document nlpResults;

    /**
     * Erstellt eine neue NLP-Komponente für die Visualisierung von Analyseergebnissen.
     *
     * @param nlpResults MongoDB-Dokument mit den NLP-Analyseergebnissen einer Rede
     * @author Maik Kitzmann
     */
    public SpeechNLPComponent(Document nlpResults) {
        this.nlpResults = nlpResults;
    }

    /**
     * Generiert den LaTeX-Code für die Visualisierung aller NLP-Analyseergebnisse.
     * Kombiniert Visualisierungen für Named Entities, Sentiment, Topics und POS-Tags,
     * sofern die entsprechenden Daten vorhanden sind.
     *
     * @return Der kombinierte LaTeX-Code für alle NLP-Visualisierungen
     * @author Maik Kitzmann
     */
    @Override
    public String toTex() {
        System.out.println("      NLP-Daten in der Rede gefunden");

        StringBuilder latex = new StringBuilder();

        // Named Entities Visualisierung
        List<Document> namedEntities = (List<Document>) nlpResults.get("namedEntities");
        if (namedEntities != null && !namedEntities.isEmpty()) {
            latex.append(generateNamedEntitiesVisualization(namedEntities));
        }

        // Sentiment-Analyse
        List<Document> sentiment = (List<Document>) nlpResults.get("sentiment");
        List<Document> vaderSentiment = (List<Document>) nlpResults.get("vadersentiment");

        if ((sentiment != null && !sentiment.isEmpty()) || (vaderSentiment != null && !vaderSentiment.isEmpty())) {
            latex.append(generateSentimentVisualization(sentiment, vaderSentiment));
        }

        // Topics-Visualisierung
        List<Document> topics = (List<Document>) nlpResults.get("topics");
        if (topics != null && !topics.isEmpty()) {
            latex.append(generateTopicsVisualization(topics));
        }

        // POS-Tags Visualisierung
        List<Document> tokens = (List<Document>) nlpResults.get("tokens");
        if (tokens != null && !tokens.isEmpty()) {
            latex.append(generatePOSTagsVisualization(tokens));
        }

        return latex.toString();
    }

    /**
     * Generiert eine Visualisierung der Named Entities in der Rede.
     * Erstellt ein Balkendiagramm mit der Häufigkeit verschiedener Entity-Typen
     * und listet die häufigsten Entities auf.
     *
     * @param namedEntities Liste von Named Entities aus der NLP-Analyse
     * @return LaTeX-Code für die Named Entities Visualisierung
     * @author Maik Kitzmann
     */
    private String generateNamedEntitiesVisualization(List<Document> namedEntities) {
        System.out.println("        Generiere Named Entities Visualisierung aus " + namedEntities.size() + " Entities");

        // Zähle Entitäten nach Typ
        Map<String, Integer> counts = new HashMap<>();
        counts.put("PER", 0);
        counts.put("LOC", 0);
        counts.put("ORG", 0);
        counts.put("MISC", 0);

        Set<String> allTypes = new HashSet<>();

        for (Document entity : namedEntities) {
            String type = entity.getString("type");
            if (type != null) {
                allTypes.add(type);
                counts.put(type, counts.getOrDefault(type, 0) + 1);
            }
        }

        StringBuilder latex = new StringBuilder();

        latex.append("\\begin{center}\n");
        latex.append("\\fbox{\\begin{minipage}{0.95\\textwidth}\n");
        latex.append("\\centering\n");

        latex.append("\\textbf{\\Large Named Entities}\n\n");
        latex.append("\\vspace{0.7cm}\n");

        // Balkendiagramm erstellen
        latex.append("\\begin{tikzpicture}\n");
        latex.append("\\begin{axis}[\n");
        latex.append("    xlabel={Entity-Typ},\n");
        latex.append("    ylabel={Anzahl},\n");
        latex.append("    ybar,\n");
        latex.append("    bar width=1cm,\n");
        latex.append("    ymin=0,\n");

        // X-Achsenbeschriftungen (Entity-Typen)
        latex.append("    symbolic x coords={");
        List<String> escapedTypes = new ArrayList<>();
        for (String type : allTypes) {
            escapedTypes.add(LaTeXComponent.Utils.escapeTikzLabel(type));
        }
        latex.append(String.join(", ", escapedTypes));
        latex.append("},\n");

        latex.append("    xtick=data,\n");
        latex.append("    nodes near coords,\n");
        latex.append("    nodes near coords align={vertical},\n");
        latex.append("    legend pos=north west,\n");
        latex.append("    width=12cm,\n");
        latex.append("    height=9cm,\n");
        latex.append("    every axis title/.style={at={(0.5,1.05)}},\n");
        latex.append("]\n");

        // Daten für das Diagramm
        latex.append("\\addplot[fill=blue!70] coordinates {");  // Dunkleres Blau
        for (String type : allTypes) {
            int count = counts.getOrDefault(type, 0);
            latex.append("(" + LaTeXComponent.Utils.escapeTikzLabel(type) + "," + count + ") ");
        }
        latex.append("};\n");

        latex.append("\\end{axis}\n");
        latex.append("\\end{tikzpicture}\n");
        latex.append("\\vspace{0.5cm}\n");

        // Häufigste Entitäten als Text
        latex.append("\\begin{minipage}{0.9\\textwidth}\n");
        latex.append("\\textbf{Häufigste Entitäten:}\n\n");

        Map<String, Integer> entityFrequency = new HashMap<>();
        for (Document entity : namedEntities) {
            String text = entity.getString("text");
            if (text != null) {
                entityFrequency.put(text, entityFrequency.getOrDefault(text, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sortedEntities = new ArrayList<>(entityFrequency.entrySet());
        sortedEntities.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        latex.append("\\begin{itemize}\n");
        int count = 0;
        for (Map.Entry<String, Integer> entry : sortedEntities) {
            if (count >= 5) break;
            latex.append("  \\item " + LaTeXComponent.Utils.escapeTeX(entry.getKey()) + ": " + entry.getValue() + "\n");
            count++;
        }
        latex.append("\\end{itemize}\n");
        latex.append("\\end{minipage}\n");

        latex.append("\\end{minipage}}\\par\n");
        latex.append("\\end{center}\n\n");

        return latex.toString();
    }

    /**
     * Generiert eine Visualisierung der Sentiment-Analyse der Rede.
     * Erstellt ein Balkendiagramm mit der Verteilung von positiven, neutralen
     * und negativen Sentiment-Werten und fasst die überwiegende Stimmung zusammen.
     *
     * @param sentiment Liste von Sentiment-Werten aus der Standardanalyse
     * @param vaderSentiment Liste von Sentiment-Werten aus der VADER-Analyse (Fallback)
     * @return LaTeX-Code für die Sentiment-Visualisierung
     * @author Maik Kitzmann
     */
    private String generateSentimentVisualization(List<Document> sentiment, List<Document> vaderSentiment) {
        System.out.println("        Generiere verbesserte Sentiment-Visualisierung");

        // Sentiment-Scores zählen
        double positiveCounts = 0;
        double neutralCounts = 0;
        double negativeCounts = 0;

        // Normale Sentiment-Analyse
        if (sentiment != null && !sentiment.isEmpty()) {
            for (Document s : sentiment) {
                Double value = null;
                try {
                    Object sentObj = s.get("sentiment");
                    if (sentObj instanceof Double) {
                        value = (Double) sentObj;
                    } else if (sentObj instanceof String) {
                        value = Double.parseDouble((String) sentObj);
                    }
                } catch (Exception e) {
                    continue;
                }

                if (value != null) {
                    if (value > 0.05) positiveCounts++;
                    else if (value < -0.05) negativeCounts++;
                    else neutralCounts++;
                }
            }
        }

        // Fallback auf VADER Sentiment wenn keine normalen Sentiments gefunden
        if ((positiveCounts + neutralCounts + negativeCounts < 1) && vaderSentiment != null && !vaderSentiment.isEmpty()) {
            for (Document s : vaderSentiment) {
                Double value = null;
                try {
                    Object sentObj = s.get("sentiment");
                    if (sentObj instanceof Double) {
                        value = (Double) sentObj;
                    } else if (sentObj instanceof String) {
                        value = Double.parseDouble((String) sentObj);
                    }
                } catch (Exception e) {
                    continue;
                }

                if (value != null) {
                    if (value > 0.05) positiveCounts++;
                    else if (value < -0.05) negativeCounts++;
                    else neutralCounts++;
                }
            }
        }

        StringBuilder latex = new StringBuilder();

        latex.append("\\vspace{2.0cm}\n\n");

        // Fallback Text wenn keine Sentiment-Daten vorhanden
        if (positiveCounts + neutralCounts + negativeCounts < 1) {
            latex.append("\\textbf{\\large Stimmungsanalyse:} Keine Sentiment-Daten verfügbar für diese Rede.\n\n");
            return latex.toString();
        }

        // Grafische Darstellung mit TikZ
        latex.append("\\begin{center}\n");
        latex.append("\\fbox{\\begin{minipage}{0.9\\textwidth}\n");
        latex.append("\\centering\n");

        latex.append("\\textbf{\\Large Stimmungsanalyse}\n\n");
        latex.append("\\vspace{0.7cm}\n");

        // Balkendiagramm für Sentiment
        latex.append("\\begin{tikzpicture}\n");
        latex.append("\\begin{axis}[\n");
        latex.append("    xlabel={Stimmung},\n");
        latex.append("    ylabel={Anzahl},\n");
        latex.append("    ybar=0pt,\n");
        latex.append("    bar width=0.7cm,\n");
        latex.append("    symbolic x coords={Positiv, Neutral, Negativ},\n");
        latex.append("    xtick=data,\n");
        latex.append("    ytick={0,20,40,60,80,100,120},\n");
        latex.append("    ymin=0,\n");
        latex.append("    ymax=" + Math.max(130, Math.ceil(Math.max(Math.max(positiveCounts, neutralCounts), negativeCounts) * 1.3)) + ",\n");
        latex.append("    nodes near coords,\n");
        latex.append("    nodes near coords align={vertical},\n");
        latex.append("    width=9cm,\n");
        latex.append("    height=7cm,\n");
        latex.append("    enlarge x limits=0.25,\n");
        latex.append("    x tick label style={font=\\normalsize},\n");
        latex.append("    major x tick style={opacity=0},\n");
        latex.append("    x tick label style={anchor=north},\n");
        latex.append("]\n");

        // Balken zeichnen mit unterschiedlichen Farben
        latex.append("\\addplot[fill=green!80] coordinates {(Positiv," + LaTeXComponent.Utils.formatNumberForPlot(positiveCounts) + ")};\n");
        latex.append("\\addplot[fill=gray!70] coordinates {(Neutral," + LaTeXComponent.Utils.formatNumberForPlot(neutralCounts) + ")};\n");
        latex.append("\\addplot[fill=red!80] coordinates {(Negativ," + LaTeXComponent.Utils.formatNumberForPlot(negativeCounts) + ")};\n");

        latex.append("\\end{axis}\n");
        latex.append("\\end{tikzpicture}\n");
        latex.append("\\vspace{0.5cm}\n");

        // Überwiegende Stimmung als Text zusammenfassen
        latex.append("\\parbox{0.9\\textwidth}{\\centering ");
        if (positiveCounts > neutralCounts && positiveCounts > negativeCounts) {
            latex.append("Die Analyse zeigt eine überwiegend \\textbf{positive} Grundhaltung in der Rede.");
        } else if (negativeCounts > neutralCounts && negativeCounts > positiveCounts) {
            latex.append("Die Analyse zeigt eine überwiegend \\textbf{negative} Grundhaltung in der Rede.");
        } else if (neutralCounts > positiveCounts && neutralCounts > negativeCounts) {
            latex.append("Die Analyse zeigt eine überwiegend \\textbf{neutrale} Grundhaltung in der Rede.");
        } else {
            latex.append("Die Analyse zeigt eine \\textbf{gemischte} Stimmungslage in der Rede.");
        }
        latex.append("}\n\n");

        latex.append("\\end{minipage}}\\par\n");
        latex.append("\\end{center}\n\n");

        latex.append("\\vspace{1.5cm}\n\n");

        return latex.toString();
    }

    /**
     * Generiert eine Visualisierung der thematischen Schwerpunkte der Rede.
     * Erstellt ein horizontales Balkendiagramm mit den Top-5-Themen,
     * sortiert nach ihrer Relevanz für die Rede.
     *
     * @param topics Liste von Themen aus der NLP-Analyse
     * @return LaTeX-Code für die Themen-Visualisierung
     * @author Maik Kitzmann
     */
    private String generateTopicsVisualization(List<Document> topics) {
        System.out.println("        Generiere Topics-Visualisierung aus " + topics.size() + " Topics");

        // Score pro Topic zusammenrechnen
        Map<String, Double> topicScores = new HashMap<>();

        for (Document topic : topics) {
            String value = topic.getString("value");

            Double score = 0.0;
            try {
                Object scoreObj = topic.get("score");
                if (scoreObj instanceof Double) {
                    score = (Double) scoreObj;
                } else if (scoreObj instanceof String) {
                    score = Double.parseDouble((String) scoreObj);
                }
            } catch (Exception e) {
                System.out.println("        Fehler bei der Score-Konvertierung: " + e.getMessage());
                continue;
            }

            if (value != null && score != null) {
                topicScores.put(value, topicScores.getOrDefault(value, 0.0) + score);
            }
        }

        if (topicScores.isEmpty()) {
            return "Keine Themen (Topics) gefunden.\n\n";
        }

        // Nach Score sortieren und Top-Themen auswählen
        List<Map.Entry<String, Double>> sortedTopics = new ArrayList<>(topicScores.entrySet());
        sortedTopics.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        List<Map.Entry<String, Double>> topTopics = sortedTopics.stream()
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder latex = new StringBuilder();

        latex.append("\\vspace{2.0cm}\n\n");


        // Grafische Darstellung mit TikZ
        latex.append("\\begin{center}\n");
        latex.append("\\fbox{\\begin{minipage}{0.95\\textwidth}\n");
        latex.append("\\centering\n");

        latex.append("\\textbf{\\Large Thematische Schwerpunkte}\n\n");
        latex.append("\\vspace{0.7cm}\n");

        // Balkendiagramm für Themen
        latex.append("\\begin{tikzpicture}\n");
        latex.append("\\begin{axis}[\n");
        latex.append("    xlabel={Relevanz-Score},\n");
        latex.append("    xbar, \n");
        latex.append("    bar width=0.6cm,\n");
        latex.append("    y=-0.6cm,\n");
        latex.append("    enlarge y limits={abs=1cm},\n");
        latex.append("    symbolic y coords={");

        List<String> escapedTopics = new ArrayList<>();
        for (Map.Entry<String, Double> entry : topTopics) {
            escapedTopics.add(LaTeXComponent.Utils.escapeTikzLabel(entry.getKey()));
        }
        latex.append(String.join(", ", escapedTopics));
        latex.append("},\n");

        latex.append("    ytick=data,\n");
        latex.append("    nodes near coords,\n");
        latex.append("    nodes near coords align={horizontal},\n");
        latex.append("    xmin=0,\n");
        latex.append("    width=12cm,\n");
        latex.append("    height=8cm,\n");
        latex.append("]\n");

        // Daten für das Diagramm
        latex.append("\\addplot[fill=blue!70] coordinates {");
        for (Map.Entry<String, Double> entry : topTopics) {
            latex.append("(" + LaTeXComponent.Utils.formatNumberForPlot(entry.getValue()) + "," +
                    LaTeXComponent.Utils.escapeTikzLabel(entry.getKey()) + ") ");
        }
        latex.append("};\n");

        latex.append("\\end{axis}\n");
        latex.append("\\end{tikzpicture}\n");
        latex.append("\\end{minipage}}\\par\n");
        latex.append("\\end{center}\n\n");

        latex.append("\\vspace{1.5cm}\n\n");

        return latex.toString();
    }

    /**
     * Generiert eine Visualisierung der Wortarten (POS-Tags) in der Rede.
     * Erstellt ein Balkendiagramm mit der Häufigkeit verschiedener Wortarten
     * in der analysierten Rede.
     *
     * @param tokens Liste von Tokens mit ihren POS-Tags aus der NLP-Analyse
     * @return LaTeX-Code für die POS-Tags-Visualisierung
     * @author Maik Kitzmann
     */
    private String generatePOSTagsVisualization(List<Document> tokens) {
        System.out.println("        Generiere POS-Tags Visualisierung aus " + tokens.size() + " Tokens");

        // Zähle POS-Tags
        Map<String, Integer> posTagCounts = new HashMap<>();
        Set<String> allPosTags = new HashSet<>();

        for (Document token : tokens) {
            String posTag = token.getString("pos");
            if (posTag != null) {
                allPosTags.add(posTag);
                posTagCounts.put(posTag, posTagCounts.getOrDefault(posTag, 0) + 1);
            }
        }

        System.out.println("          Gefundene POS-Tag-Typen: " + allPosTags);

        StringBuilder latex = new StringBuilder();

        // Neue Seite beginnen für Layout
        latex.append("\\clearpage\n\n");

        latex.append("\\begin{center}\n");
        latex.append("\\fbox{\\begin{minipage}{0.95\\textwidth}\n");
        latex.append("\\centering\n");

        latex.append("\\textbf{\\Large Verteilung der Wortarten}\n\n");
        latex.append("\\vspace{0.7cm}\n");

        // Balkendiagramm mit Tikz
        latex.append("\\begin{tikzpicture}[scale=0.9]\n");
        latex.append("\\begin{axis}[\n");
        latex.append("    xlabel={Wortart},\n");
        latex.append("    ylabel={Anzahl},\n");
        latex.append("    ybar,\n");
        latex.append("    bar width=0.3cm,\n");
        latex.append("    ymin=0,\n");

        // X-Achsenbeschriftungen
        latex.append("    symbolic x coords={");
        List<String> escapedTags = new ArrayList<>();
        for (String tag : allPosTags) {
            escapedTags.add(LaTeXComponent.Utils.escapeTikzLabel(tag));
        }
        latex.append(String.join(", ", escapedTags));
        latex.append("},\n");

        latex.append("    xtick=data,\n");
        latex.append("    nodes near coords,\n");
        latex.append("    nodes near coords style={font=\\scriptsize, rotate=90, anchor=west},\n");
        latex.append("    nodes near coords align={anchor=west},\n");
        latex.append("    legend pos=north west,\n");
        latex.append("    width=16cm,\n");
        latex.append("    height=10cm,\n");
        latex.append("    xtick pos=bottom,\n");
        latex.append("    xtick align=outside,\n");
        latex.append("    x tick label style={rotate=45, anchor=east, align=right, font=\\scriptsize},\n");
        latex.append("    enlarge x limits=0.03,\n");
        latex.append("]\n");

        // Daten für das Diagramm mit escapeTikz
        latex.append("\\addplot[fill=blue!70] coordinates {");
        for (String tag : allPosTags) {
            int count = posTagCounts.getOrDefault(tag, 0);
            latex.append("(" + LaTeXComponent.Utils.escapeTikzLabel(tag) + "," + count + ") ");
        }
        latex.append("};\n");

        latex.append("\\end{axis}\n");
        latex.append("\\end{tikzpicture}\n");

        latex.append("\\end{minipage}}\\par\n");
        latex.append("\\end{center}\n\n");

        return latex.toString();
    }
}
