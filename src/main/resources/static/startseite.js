document.addEventListener("DOMContentLoaded", function () {
    // Implementiert von Luana Schäfer


    const searchButton = document.getElementById("searchButton");
    if (searchButton) {
        searchButton.addEventListener("click", () => searchReden(1));
        console.log("Suchbutton ist bereit!");
    } else {
        console.error("Suchbutton nicht gefunden!");
    }
});

let currentPage = 1;
// ----------------------------

// Führt eine Suchanfrage an das Backend durch und zeigt die Ergebnisse an
function searchReden(page) {
    const searchText = document.getElementById("searchText").value.trim();
    if (searchText === "") {
        alert("Bitte geben Sie ein Suchwort ein!");
        return;
    }

    console.log(`Anfrage wird gesendet an: /api/search?text=${searchText}&page=${page}`);

    // Zeigt Lade-Spinner während der Anfrage
    document.getElementById("loader-container").style.display = "inline-block";

    // GET-Anfrage an das Backend mit Suchtext und Seitenzahl
    fetch(`/api/search?text=${encodeURIComponent(searchText)}&page=${page}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`Fehler beim Abrufen der Daten: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("Daten empfangen:", data);

            // Ergebnisse anzeigen, bei Seite 1 wird vorheriger Inhalt überschrieben
            if (page === 1) {
                displayResults(data, true);
            } else {
                displayResults(data, false);
            }

            currentPage = page; // Aktuelle Seite merken


        })
        .catch(error => {
            console.error("Fehler:", error);
            document.getElementById("reden-container").innerHTML = "<p>Fehler beim Laden der Ergebnisse.</p>";
        })
        .finally(() => {

            // Lade-Spinner wieder ausblenden
            document.getElementById("loader-container").style.display = "none";
        });
}
// ----------------------------

// Zeigt Suchergebnisse an
function displayResults(data) {

    if (!data || !data.results || !Array.isArray(data.results)) {
        document.getElementById("reden-container").innerHTML = "<p>Fehler beim Laden der Ergebnisse.</p>";
        return;
    }

    const container = document.getElementById("reden-container");
    container.innerHTML = ""; // Vorherige Ergebnisse löschen


    if (data.results.length === 0) {
        container.innerHTML = "<p>Keine passenden Ergebnisse gefunden.</p>";
        return;
    }

    // Ergebnisse einzeln durchgehen und Boxen erstellen
    data.results.forEach(item => {
        const box = document.createElement("div");
        box.classList.add("speech-box");

        // Redner und Partei holen
        const speaker = item.speaker || "Unbekannter Redner";
        const party = item.party ? ` (${item.party})` : "";

        // Sitzungsdetails
        const protocol = item.protocol || {};
        const sessionTitle = item.sessionTitle || "Unbekannte Sitzung";
        const date = protocol.date || "Kein Datum verfügbar";

        // Nur textContent mit type: "text"` herausfiltern
        let speechText = "Kein Redetext verfügbar";
        if (Array.isArray(item.textContent)) {
            const validTexts = item.textContent
                .filter(tc => tc.type === "text")
                .map(tc => tc.text)
                .join(" ");

            if (validTexts.length > 0) {
                speechText = validTexts.length > 200 ? validTexts.substring(0, 200) + "..." : validTexts;
            }
        }

        // Box mit Daten füllen
        box.innerHTML = `
            <h3 class="session-title">${sessionTitle}</h3>
            <p><strong>${speaker}${party}</strong></p>
            <p class="speech-date">${date}</p>
            <p class="speech-preview">„${speechText}“</p>
            <button class="more-button" onclick="zeigeDetails('${item._id}')">Mehr anzeigen</button>
        `;

        container.appendChild(box);
    });

    // Mehr lade button, wenn mehr Ergebnisse vorhanden.
    if (data.hasMore) {
        const loadMoreButton = document.createElement("button");
        loadMoreButton.textContent = "Mehr laden";
        loadMoreButton.classList.add("load-more-button");
        loadMoreButton.onclick = () => loadMoreResults();
        container.appendChild(loadMoreButton);
    }
}
// ----------------------------

// Lädt bei Klick auf "Mehr laden" die nächste Seite der Ergebnisse nach
function loadMoreResults() {
    currentPage++;

    const searchText = document.getElementById("searchText").value.trim();

    // API-Request mit aktualisierter Seitenzahl
    fetch(`/api/search?text=${encodeURIComponent(searchText)}&page=${currentPage}`)
        .then(response => response.json())
        .then(data => {

            if (!data || !data.results || !Array.isArray(data.results)) {
                console.error("Fehler beim Nachladen der Ergebnisse");
                return;
            }

            displayResults(data, false);
        })
        .catch(error => console.error("Fehler beim Nachladen:", error));
}
// ----------------------------


function zeigeDetails(redeId) {
    window.location.href = `/rede/${redeId}`;
}
