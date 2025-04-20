# Multimodal-Parliament-Explorer

Dieses Projekt wurde im Rahmen des Programmierpraktikums an der Goethe-Universität Frankfurt im Wintersemester 2024/25 umgesetzt.


## Inhalt

- Vollständiges Java-Backend zur Verwaltung, Analyse und Bereitstellung der Parlamentsdaten
- RESTful API zur Abfrage strukturierter Redeinhalte
- Freemarker-basiertes Frontend mit interaktiven d3.js-Diagrammen
- NLP-Verarbeitung via DUUI (Token, Lemma, NER, POS, Sentiment, Topic)
- Exportfunktionen in **PDF** und **XMI**
- Visuelle Annotation von Redeinhalten (Sentiment, Named Entities, POS)
- Filterbare Analyse-Seite mit interaktiven Visualisierungen

## Voraussetzungen

- Java 17
- Maven
- MongoDB (lokal oder remote)
- MiKTeX (für PDF-Export)

## Projekt starten

- Start des Servers: Starte ParliamentAPI
- Frontend öffnen unter: http://localhost:7070


## Funktionialitäten

1. Startseite
- Volltextsuche nach Begriffen, Rednern oder Parteien
- Paginierte Ausgabe von Reden mit Vorschau
- Zugriff auf Detailseite per Button „Mehr anzeigen“

2. Redendetailseite
- Video und Bild des Redners
- Scrollbarer Redetext mit Markierungen:
- Sentiment (mit Emoji-Hover)
- Named Entities (Farbmarkierung + Legende)
- POS-Tags (farbig + Legende)
- Kommentare und Zwischenrufe sind grau & kursiv
- Vier Diagramme (d3.js):
    - Bubble Chart (Topics)
    - Radar Chart (Sentiment)
    - Sunburst Chart (NER)
    - Balkendiagramm (POS)

3. Analyse-Seite
- Aggregierte NLP-Daten aller Reden
- Filterbar nach:
    - Sitzung (Dropdown)
    - Topic (Dropdown)
    - Redner (Live-Suche)
- Diagramme aktualisieren sich dynamisch
- Nur ein Filter gleichzeitig aktiv

4. Exportfunktionen
   Verfügbar am unteren Rand der Analyse-Seite:

- PDF-Export (via LaTeX):
    - Bild, Metadaten, Text, NLP-Diagramme
    -  grafische Statistik via TikZ

- XMI-Export:
    - Strukturierte XML-Repräsentation
    - Ideal für Weiterverarbeitung (z. B. mit UIMA)

!Achtung: PDF-Export funktioniert nur mit installierter MiKTeX-Umgebung. Export öffnet sich in neuem Tab und blockiert die Anwendung nicht.


## Bekannte Fehler

PDF-Generierung schlägt fehl, wenn TikZ nicht korrekt eingebunden ist → Logausgabe beachten


# Autoren

Gruppe 7_2 – Programmierpraktikum

- Delia-Ioana Maniliuc - Backend
- Ibrahim Garhiou - Backend
- Luana Schäfer - Frontend
- Maik Kitzmann - Export
