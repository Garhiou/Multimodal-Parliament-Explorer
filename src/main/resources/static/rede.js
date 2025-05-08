document.addEventListener("DOMContentLoaded", function () {
    //Implementiert von Luana Schäfer


    const redeId = window.location.pathname.split("/").pop();
    const legendSentiment = document.getElementById("legend-sentiment");
    const legendContainer = document.getElementById("legend-container");
    const toggleSentiment = document.querySelector(".toggle-option[data-mode='off']");


    fetch(`/api/rede/${redeId}`)
        .then(response => response.json())
        .then(data => {


            if (!data) {
                document.body.innerHTML = "<h2>Rede nicht gefunden</h2>";
                return;
            }

            window.namedEntities = data.namedEntities || [];
            window.posTags = data.posTags || [];
            window.originalTextContent = data.textContent ? [...data.textContent] : [];
            window.sentiments = data.sentiments || [];


            // Video setzen
            const videoElement = document.getElementById("redeVideo");
            if (data.videoUrl) {
                videoElement.src = data.videoUrl;
            } else {
                videoElement.removeAttribute("poster");
                videoElement.style.backgroundColor = "transparent";
            }

            // Bild setzen (oder Platzhalter)
            const imageElement = document.getElementById("speakerImage");
            const dummyImage = "https://dummyimage.com/120x120/ffffff/000000&text=Kein+Bild+vorhanden";

            imageElement.src = data.imageUrl || dummyImage; // Falls kein Bild, setze den Platzhalter

            // Sitzungsinfos
            const sitzungElement = document.getElementById("sitzung");
            let sitzungText = data.sessionTitle || "Unbekannte Sitzung";
            if (data.protocol && data.protocol.date) {
                sitzungText += ` - ${data.protocol.date}`;
            }
            sitzungElement.innerText = sitzungText;

            // Redner-Infos
            document.getElementById("speakerName").innerText = data.speaker || "Unbekannter Redner";
            document.getElementById("party").innerText = data.party || "Partei nicht bekannt";

            // Rede-Text formatieren (Kommentare kursiv und in grauem Kasten)
            const textContainer = document.getElementById("speechText");
            textContainer.innerHTML = "";

            if (data.textContent && Array.isArray(data.textContent)) {
                data.textContent.forEach((tc, index) => {
                    console.log("Type:", tc.type, "| Text:", tc.text);

                    const paragraph = document.createElement("div");

                    if (tc.type && typeof tc.type === "string" && tc.type.trim().toLowerCase() === "comment") {
                        paragraph.classList.add("comment-box");
                        paragraph.innerHTML = `<em>${tc.text}</em>`;
                    } else if (tc.type && typeof tc.type === "string" && tc.type.trim().toLowerCase() === "text") {
                        paragraph.classList.add("speech-text");

                        paragraph.innerHTML = tc.text;

                        // Das erste Element soll blau sein
                        if (index === 0) {
                            paragraph.classList.add("first-speaker");
                        }
                    } else {
                        console.warn("Unbekannter Typ:", tc.type, "bei Text:", tc.text);
                    }

                    textContainer.appendChild(paragraph);
                });
            } else {
                textContainer.innerHTML = "<p>Kein Redeinhalt verfügbar.</p>";
            }

            displayTextWithSentiments();
            legendContainer.classList.remove("hidden");
            legendSentiment.classList.remove("hidden");
            toggleSentiment.classList.add("active");

        })

        .catch(error => {
            console.error("Fehler beim Laden der Rede:", error);
            document.body.innerHTML = "<h2>Fehler beim Laden der Rede</h2>";

        });

    // Toggle-Schalter für Named Entities und POS
    const toggleOptions = document.querySelectorAll(".toggle-option");

    toggleOptions.forEach(option => {
        option.addEventListener("click", function () {
            // Entferne 'active' von allen Optionen
            toggleOptions.forEach(opt => opt.classList.remove("active"));

            // Füge 'active' zur geklickten Option hinzu
            this.classList.add("active");

            // Aktuellen Modus speichern (Sentiment, Named Entities, POS)
            const mode = this.getAttribute("data-mode");
            console.log("Aktueller Modus:", mode);

            updateTextAnnotations(mode);
        });
    });
    // ----------------------------

    function updateTextAnnotations(mode) {
        const textContainer = document.getElementById("speechText");
        const legendContainer = document.getElementById("legend-container");
        const legendNE = document.getElementById("legend-ne");
        const legendPOS = document.getElementById("legend-pos");
        const legendSentiment = document.getElementById("legend-sentiment");


        // Entferne alte Markierungen
        textContainer.classList.remove("highlight-ne", "highlight-pos");
        legendContainer.classList.add("hidden");
        legendNE.classList.add("hidden");
        legendPOS.classList.add("hidden");

        if (!window.originalTextContent || !Array.isArray(window.originalTextContent)) {
            console.error("Kein Originaltext gespeichert! Abbruch.");
            return;
        }

        // Sentiments auf aus
        if (mode === "off") {
            displayTextWithSentiments();
            legendContainer.classList.remove("hidden");
            legendSentiment.classList.remove("hidden");
            document.body.classList.remove("hide-sentiment-legend");
            return;
        }

        // Falls nicht Sentiment, verstecke die Sentiment-Legende
        document.body.classList.add("hide-sentiment-legend");
        legendSentiment.classList.add("hidden");


        textContainer.innerHTML = "";
        let currentTextIndex = 0;


        // Textzeilen rendern mit Annotation je nach Modus
        window.originalTextContent.forEach((tc, index) => {
            const paragraph = document.createElement("div");

            if (tc.type && typeof tc.type === "string" && tc.type.trim().toLowerCase() === "comment") {
                paragraph.classList.add("comment-box");

                // Named Entities markieren
                if (mode === "ne" && window.namedEntities) {
                    let relevantEntities = window.namedEntities.filter(e => e.begin >= currentTextIndex && e.end <= (currentTextIndex + tc.text.length));
                    paragraph.innerHTML = highlightText(tc.text, relevantEntities);

                    // POS markieren
                } else if (mode === "pos" && window.posTags) {
                    let relevantPos = window.posTags.filter(e => e.begin >= currentTextIndex && e.end <= (currentTextIndex + tc.text.length));
                    paragraph.innerHTML = highlightPos(tc.text, relevantPos);
                } else {
                    paragraph.innerHTML = `<em>${tc.text}</em>`;
                }
            }
            else if (tc.type && typeof tc.type === "string" && tc.type.trim().toLowerCase() === "text") {
                paragraph.classList.add("speech-text");

                if (mode === "ne" && window.namedEntities) {
                    let relevantEntities = window.namedEntities.filter(e => e.begin >= currentTextIndex && e.end <= (currentTextIndex + tc.text.length));
                    paragraph.innerHTML = highlightText(tc.text, relevantEntities);

                } else if (mode === "pos" && window.posTags) {
                    let relevantPos = window.posTags.filter(e => e.begin >= currentTextIndex && e.end <= (currentTextIndex + tc.text.length));
                    paragraph.innerHTML = highlightPos(tc.text, relevantPos);

                } else {
                    paragraph.innerHTML = tc.text;
                }

                if (index === 0) {
                    paragraph.classList.add("first-speaker");
                }
            }

            currentTextIndex += tc.text.length + 1;
            textContainer.appendChild(paragraph);
        });


        // Legende für aktiven Modus einblenden
        if (mode === "ne") {
            textContainer.classList.add("highlight-ne");
            legendContainer.classList.remove("hidden");
            legendNE.classList.remove("hidden");
        } else if (mode === "pos") {
            textContainer.classList.add("highlight-pos");
            legendContainer.classList.remove("hidden");
            legendPOS.classList.remove("hidden");
        }
    }
    // ----------------------------


    function highlightText(text, entities) {

        if (!entities || entities.length === 0) {
            return text;
        }

        // Zuordnung von Named Entity Typen zu CSS-Klassen
        const entityClassMap = {
            "PER": "entity-person",
            "LOC": "entity-location",
            "ORG": "entity-organization",
            "MISC": "entity-misc"
        };

        let currentIndex = 0;
        let highlightedText = "";
        let remainingText = text;

        // Sortiere Named Entities nach Reihenfolge im Text
        entities.sort((a, b) => a.begin - b.begin);

        for (let entity of entities) {
            let entityText = entity.text;
            let entityType = entity.type.toUpperCase();
            let entityClass = entityClassMap[entityType] || "entity-misc";

            // Suche das Entity ab currentIndex
            let entityIndex = remainingText.indexOf(entityText, currentIndex);

            if (entityIndex === -1) {
                continue;
            }

            // Füge unmarkierten Text hinzu
            highlightedText += remainingText.substring(currentIndex, entityIndex);

            // Füge markierten Entity-Text hinzu
            highlightedText += `<span class="${entityClass}">${entityText}</span>`;

            // Setze currentIndex hinter das markierte Wort
            currentIndex = entityIndex + entityText.length;
        }

        highlightedText += remainingText.substring(currentIndex);

        return highlightedText;
    }
    // ----------------------------


    function highlightPos(text, posTags) {

        if (!posTags || posTags.length === 0) {
            return text;
        }

        // Zuordnung von POS-Tags zu CSS-Klassen
        const posClassMap = {
            "NN": "pos-nom",
            "NE": "pos-ne",
            "ADJ": "pos-adj",
            "ADJA": "pos-adj",
            "ADJD": "pos-adj",
            "ADV": "pos-adv",
            "VVFIN": "pos-verb",
            "VVINF": "pos-verb",
            "VVIZU": "pos-verb",
            "VVPP": "pos-verb",
            "VAFIN": "pos-verb",
            "VAIMP": "pos-verb",
            "VAINF": "pos-verb",
            "VAPP": "pos-verb",
            "VMFIN": "pos-verb",
            "VMINF": "pos-verb",
            "VMPP": "pos-verb",
        };

        let currentIndex = 0;
        let highlightedText = "";
        let remainingText = text;

        // Sortiere POS nach Reihenfolge im Text
        posTags.sort((a, b) => a.begin - b.begin);

        for (let pos of posTags) {
            let word = pos.text;
            let posType = pos.pos.toUpperCase();
            let posClass = posClassMap[posType];

            // Suche das Wort im aktuellen Text
            let wordIndex = remainingText.indexOf(word, currentIndex);

            if (wordIndex === -1) {
                continue;
            }

            // Füge unmarkierten Text hinzu
            highlightedText += remainingText.substring(currentIndex, wordIndex);

            // Füge markierten POS-Text hinzu
            highlightedText += `<span class="${posClass}">${word}</span>`;

            // Setze currentIndex hinter das markierte Wort
            currentIndex = wordIndex + word.length;
        }

        highlightedText += remainingText.substring(currentIndex);

        return highlightedText;
    }
    // ----------------------------


    function displayTextWithSentiments() {

        const textContainer = document.getElementById("speechText");
        textContainer.innerHTML = "";

        let currentTextIndex = 0;

        window.originalTextContent.forEach((tc, index) => {
            const paragraph = document.createElement("div");

            // Prüfen, ob es sich um einen Kommentar handelt
            const isComment = tc.type.toLowerCase() === "comment";

            if (isComment) {
                paragraph.classList.add("comment-box");
            } else {
                paragraph.classList.add("speech-text");
            }

            // Sentiment-Icons anwenden
            let textWithSentiment = applySentimentIcons(tc.text, currentTextIndex, currentTextIndex + tc.text.length, isComment);

            paragraph.innerHTML = textWithSentiment;

            // Das erste Element soll blau sein
            if (!isComment && index === 0) {
                paragraph.classList.add("first-speaker");
            }

            currentTextIndex += tc.text.length + 1;
            textContainer.appendChild(paragraph);
        });
    }

    // ----------------------------

    function applySentimentIcons(text, start, end, isComment = false) {

        let sentimentEntries = window.sentiments.filter(sent => sent.begin >= start && sent.end <= end);

        if (sentimentEntries.length === 0) {
            return isComment ? `<em>${text}</em>` : text;
        }

        let modifiedText = text;
        let newText = "";
        let lastIndex = 0;

        sentimentEntries.forEach(sentiment => {
            let sentimentEmoji = getSentimentIcon(sentiment.sentiment);
            let sentimentTooltip = `<span class="sentiment-icon" title="Sentiment-Wert: ${sentiment.sentiment.toFixed(3)}">${sentimentEmoji}</span>`;
            let insertPos = sentiment.end - start; // Position des Emojis berechnen

            // Text bis zur Einfügeposition übernehmen, dann das Emoji direkt einfügen
            newText += modifiedText.slice(lastIndex, insertPos) + sentimentTooltip;
            lastIndex = insertPos;
        });

        // Restlichen Text anhängen
        newText += modifiedText.slice(lastIndex);

        // Kommentare bleiben in ihrem Format
        return isComment ? `<em>${newText}</em>` : newText;
    }
    // ----------------------------


    // Sentiment-Wert zu Icon umwandeln
    function getSentimentIcon(sentimentValue) {
        if (sentimentValue >= 0.3) {
            return '<i class="fa-solid fa-face-smile" style="color: #27AE60;"></i>'; // Positives Sentiment
        } else if (sentimentValue <= -0.3) {
            return '<i class="fa-solid fa-face-frown" style="color: #E74C3C;"></i>'; // Negatives Sentiment
        } else {
            return '<i class="fa-solid fa-face-meh" style="color: #F39C12;"></i>'; // Neutrales Sentiment
        }
    }

});