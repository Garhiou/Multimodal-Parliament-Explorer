package latex.impl;

import java.util.Locale;

// Implementiert von Maik Kitzmann

// Basis-Interface für alle LaTeX-Komponenten
public interface LaTeXComponent {

    /**
     * Wandelt die Komponente in LaTeX-Code um.
     * Wird von konkreten Klassen implementiert, die Inhalte für LaTeX erzeugen.
     *
     * @return Ein String mit dem LaTeX-Code
     * @author Maik Kitzmann
     */
    String toTex();

    /**
     * Enthält Hilfsmethoden zur LaTeX-Formatierung.
     * Beinhaltet Funktionen zum Escapen von Sonderzeichen, Unicode-Bereinigung usw.
     *
     * @author Maik Kitzmann
     */
    class Utils {

        /**
         * Escaped alle relevanten Sonderzeichen in einem Text für LaTeX.
         * Nutzt zusätzlich eine Unicode-Bereinigung, damit problematische Zeichen ersetzt werden.
         *
         * @param text Der zu bereinigende Text
         * @return Der für LaTeX sichere Text
         * @author Maik Kitzmann
         */
        public static String escapeTeX(String text) {
            if (text == null) return "";

            String result = cleanUnicodeForLaTeX(text);

            result = result.replace("\\", "\\textbackslash ")
                    .replace("&", "\\&")
                    .replace("%", "\\%")
                    .replace("$", "\\$")
                    .replace("#", "\\#")
                    .replace("_", "\\_")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("~", "\\textasciitilde ")
                    .replace("^", "\\textasciicircum ");

            return result;
        }

        /**
         * Entfernt oder ersetzt Unicode-Zeichen, die in LaTeX Probleme machen könnten.
         * Wird intern von escapeTeX() verwendet.
         *
         * @param text Eingabetext mit potenziell problematischen Zeichen
         * @return Bereinigter Text für LaTeX-Verwendung
         * @author Maik Kitzmann
         */
        public static String cleanUnicodeForLaTeX(String text) {
            if (text == null) return "";

            return text
                    .replace("\u02BC", "'")
                    .replace("\u2032", "'")

                    .replace("\u2018", "'")
                    .replace("\u2019", "'")
                    .replace("\u201A", "'")
                    .replace("\u201B", "'")

                    .replace("\u201C", "``")
                    .replace("\u201D", "''")
                    .replace("\u201E", ",,")
                    .replace("\u201F", "``")

                    .replace("\u2013", "--")
                    .replace("\u2014", "---")
                    .replace("\u2015", "---")
                    .replace("\u2026", "...")
                    .replace("\u2022", "$\\bullet$")

                    .replace("\u202F", " ")
                    .replace("\u00A0", " ")
                    .replace("\u2009", " ")
                    .replace("\u200A", " ")
                    .replace("\u200B", "")
                    .replace("\u2060", "")
                    .replace("\uFEFF", "")

                    .replace("\u2010", "-")
                    .replace("\u2011", "-")
                    .replace("\u2012", "-")

                    .replace("\u00A9", "\\textcopyright ")
                    .replace("\u00AE", "\\textregistered ")
                    .replace("\u2122", "\\texttrademark ")

                    .replace("\u00AB", "``")
                    .replace("\u00BB", "''")
                    .replace("\u203A", "'")
                    .replace("\u2039", "'")

                    .replace("&nbsp;", " ")
                    .replace("&#160;", " ")
                    .replace("&#xa0;", " ")
                    .replace("&#xA0;", " ")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")

                    .replace("ä", "\\\"a")
                    .replace("ö", "\\\"o")
                    .replace("ü", "\\\"u")
                    .replace("Ä", "\\\"A")
                    .replace("Ö", "\\\"O")
                    .replace("Ü", "\\\"U")
                    .replace("ß", "\\ss{}")

                    .replaceAll("[\\p{Cc}\\p{Cf}]", "");
        }

        /**
         * Wandelt einen Text in ein TikZ-kompatibles Label um.
         * Nur bestimmte Zeichen werden escaped.
         *
         * @param text Eingabetext
         * @return Formatierter TikZ-Label-Text
         * @author Maik Kitzmann
         */
        public static String escapeTikzLabel(String text) {
            if (text == null) return "";

            return "{" + text.replace("_", "\\_").replace("$", "\\$") + "}";
        }

        /**
         * Formatiert eine Zahl für Diagramme, damit sie im Plot korrekt (mit Punkt) angezeigt wird.
         *
         * @param number Die zu formatierende Zahl
         * @return String mit einer Nachkommastelle und US-Notation
         * @author Maik Kitzmann
         */
        public static String formatNumberForPlot(double number) {
            return String.format(Locale.US, "%.1f", number);
        }

        /**
         * Wandelt einen Text so um, dass er in XML-Dateien eingebettet werden kann.
         *
         * @param text Ursprünglicher Text
         * @return XML-sicherer Text
         * @author Maik Kitzmann
         */
        public static String escapeXML(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }
    }
}
