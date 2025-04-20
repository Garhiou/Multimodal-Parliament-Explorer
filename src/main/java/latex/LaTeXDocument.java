package latex;

import latex.impl.LaTeXComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Basisklasse für alle LaTeX-Dokumente im Parlamentssystem.
 * Implementiert das grundlegende Gerüst eines LaTeX-Dokuments mit Präambel,
 * Titelsection, optionalem Inhaltsverzeichnis und Hauptteil.
 * Ermöglicht die einfache Erstellung verschiedener Dokumenttypen durch Ableitung.
 *
 * @author Maik Kitzmann
 */
public abstract class LaTeXDocument implements LaTeXComponent {

    protected final List<LaTeXComponent> preambleComponents = new ArrayList<>();
    protected final List<LaTeXComponent> bodyComponents = new ArrayList<>();

    /**
     * Erstellt ein grundlegendes LaTeX-Dokument mit Standardpräambel.
     * Initialisiert die Präambel mit notwendigen LaTeX-Paketen für Dokumentformatierung,
     * Sprachunterstützung, Grafiken, Tabellen und weiteren Elementen.
     *
     * @author Maik Kitzmann
     */
    public LaTeXDocument() {
        // Standard Präambel Komponenten hinzufügen
        preambleComponents.add(() -> "\\documentclass[a4paper,12pt]{article}\n" +
                "\\usepackage[utf8]{inputenc}\n" +
                "\\usepackage[T1]{fontenc}\n" +
                "\\usepackage{textcomp}\n" +
                "\\usepackage[german]{babel}\n" +
                "\\usepackage{graphicx}\n" +
                "\\usepackage{tikz}\n" +
                "\\usepackage{pgfplots}\n" +
                "\\pgfplotsset{compat=1.18}\n" +
                "\\pgfkeys{/pgf/number format/read comma as period=true}\n" +
                "\\usepackage{hyperref}\n" +
                "\\usepackage{fancyhdr}\n" +
                "\\usepackage{booktabs}\n" +
                "\\usepackage{xcolor}\n" +
                "\\usepackage{geometry}\n" +
                "\\usepackage{setspace}\n" +
                "\\usepackage{float}\n" +
                "\\usepackage{adjustbox}\n" +  // bessere Box-Positionierung weil sonst rechts außen
                "\\usepackage{caption}\n" +
                "\\geometry{a4paper, margin=2.5cm}\n\n");
    }

    /**
     * Fügt eine Komponente zum Hauptteil des Dokuments hinzu.
     * Diese Komponenten werden in der Reihenfolge ihres Hinzufügens
     * in den Dokumentkörper eingefügt.
     *
     * @param component Die hinzuzufügende LaTeX-Komponente
     * @author Maik Kitzmann
     */
    public void addToBody(LaTeXComponent component) {
        bodyComponents.add(component);
    }

    /**
     * Generiert das komplette LaTeX-Dokument als String.
     * Kombiniert Präambel, Titelabschnitt, Inhaltsverzeichnis (optional)
     * und alle Komponenten des Hauptteils zu einem vollständigen LaTeX-Dokument.
     *
     * @return Das vollständige LaTeX-Dokument als String
     * @author Maik Kitzmann
     */
    @Override
    public String toTex() {
        StringBuilder latex = new StringBuilder();

        // Präambel hinzufügen
        for (LaTeXComponent component : preambleComponents) {
            latex.append(component.toTex());
        }

        // Dokument beginnen
        latex.append("\\begin{document}\n\n");

        // Titel, Autor, Datum hinzufügen
        String titleSection = generateTitleSection();
        if (titleSection != null && !titleSection.isEmpty()) {
            latex.append(titleSection).append("\n\n");
        }

        // Inhaltsverzeichnis hinzufügen
        if (includeTableOfContents()) {
            latex.append("\\tableofcontents\n");
            latex.append("\\newpage\n\n");
        }

        // Komponenten des Hauptteils hinzufügen
        for (LaTeXComponent component : bodyComponents) {
            latex.append(component.toTex()).append("\n\n");
        }

        // Dokument beenden
        latex.append("\\end{document}");

        return latex.toString();
    }

    /**
     * Bestimmt, ob ein Inhaltsverzeichnis eingefügt werden soll.
     * Kann von Unterklassen überschrieben werden, um das Inhaltsverzeichnis auszublenden.
     *
     * @return true wenn ein Inhaltsverzeichnis eingefügt werden soll (Standardwert), sonst false
     * @author Maik Kitzmann
     */
    protected boolean includeTableOfContents() {
        return true;
    }

    /**
     * Generiert den Titelabschnitt des Dokuments mit Titel, Autor und Datum.
     * Muss von jeder konkreten Unterklasse implementiert werden.
     *
     * @return Der formatierte Titelabschnitt als LaTeX-Code
     * @author Maik Kitzmann
     */
    protected abstract String generateTitleSection();
}
