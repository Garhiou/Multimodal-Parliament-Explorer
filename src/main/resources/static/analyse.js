document.addEventListener("DOMContentLoaded", () => {
    // Implementiert von Luana Schäfer

    // HTML-Elemente abrufen
    const sessionSelect = document.getElementById("sessionSelect");
    const topicsSelect = document.getElementById("topicsSelect");
    const speakerInput = document.getElementById("speakerSearch");
    const applyFiltersButton = document.getElementById("applyFilters");
    const activeFilterText = document.getElementById("activeFilterText");


    // Vorschlags-Dropdown für Redner hinzufügen
    const speakerSuggestions = document.createElement("div");
    speakerSuggestions.classList.add("suggestions");
    speakerInput.parentNode.appendChild(speakerSuggestions);

    // Ladefunktionen für Filter
    loadSessions();
    loadTopics();

    // Button zum Filtern
    applyFiltersButton.addEventListener("click", applyFilters);

    // Diagramme aller Reden anzeigen
    fetch("/api/aggregation?type=all&value=all speeches")
        .then(res => res.json())
        .then(data => {
            if (!data || !data.nlpAggregation) {
                console.warn("Keine aggregierten Daten für alle Reden gefunden.");
                return;
            }

            if (activeFilterText) {
                activeFilterText.textContent = `Alle Reden`;
            }

            updateCharts(data.nlpAggregation);

            window.currentExportFilterType = "all";
            window.currentExportFilterValue = "all speeches";


        })
        .catch(err => console.error("Fehler beim Laden der Standarddaten:", err));

    // Export-Buttons abrufen
    const exportPdfBtn = document.getElementById("exportPdfBtn");
    const exportXmiBtn = document.getElementById("exportXmiBtn");

    // Aktueller Filtertyp und Wert merken
    window.currentFilterType = "all";
    window.currentFilterValue = "all speeches";

    /**
     * Lädt Sitzungen, sortiert sie aufsteigend und fügt sie in das Dropdown ein
     */
    function loadSessions() {
        fetch("/api/sessions")
            .then(response => response.json())
            .then(data => {
                sessionSelect.innerHTML = '<option value="all">Alle Sitzungen</option>';
                [...new Set(data.map(session => session.trim()))]
                    .map(session => parseInt(session, 10))
                    .filter(session => !isNaN(session) && session >= 90)
                    .sort((a, b) => a - b)
                    .forEach(session => {
                        const option = document.createElement("option");
                        option.value = session;
                        option.textContent = `Sitzung ${session}`;
                        sessionSelect.appendChild(option);
                    });
            })
            .catch(error => console.error("Fehler beim Laden der Sitzungen:", error));
    }

    /**
     * Lädt Themen und fügt sie in das Dropdown ein
     */
    function loadTopics() {
        fetch("/api/topics")
            .then(response => response.json())
            .then(data => {

                topicsSelect.innerHTML = "";

                // Standardoption hinzufügen
                const defaultOption = document.createElement("option");
                defaultOption.value = "all";
                defaultOption.textContent = "Alle Themen";
                topicsSelect.appendChild(defaultOption);

                if (!Array.isArray(data) || data.length === 0) {
                    console.error("Keine Topics gefunden!");
                    return;
                }

                // null-Werte herausfiltern
                data.filter(topic => topic && topic.trim().length > 0).forEach(topic => {
                    const option = document.createElement("option");
                    option.value = topic;
                    option.textContent = topic;
                    topicsSelect.appendChild(option);
                });
            })
            .catch(error => console.error("Fehler beim Laden der Themen:", error));
    }


    /**
     * Echtzeit-Redner-Suche mit Vorschlägen
     */
    speakerInput.addEventListener("input", () => {
        const query = speakerInput.value.trim().toLowerCase();
        speakerSuggestions.innerHTML = "";

        if (query.length < 2) {
            speakerSuggestions.style.display = "none";
            return;
        }

        fetch(`/api/speaker-suggestions?query=${encodeURIComponent(query)}`)
            .then(response => response.json())
            .then(data => {
                if (data.length === 0) {
                    speakerSuggestions.style.display = "none";
                    return;
                }

                speakerSuggestions.style.display = "block";
                data.forEach(speaker => {
                    const suggestion = document.createElement("div");
                    suggestion.textContent = speaker;
                    suggestion.classList.add("suggestion-item");
                    suggestion.addEventListener("click", () => {
                        speakerInput.value = speaker;
                        speakerSuggestions.style.display = "none";
                    });
                    speakerSuggestions.appendChild(suggestion);
                });
            })
            .catch(error => console.error("Fehler beim Abrufen der Redner-Vorschläge:", error));
    });

    // Klick außerhalb des Suchfelds schließt die Vorschläge
    document.addEventListener("click", (event) => {
        if (!speakerInput.contains(event.target) && !speakerSuggestions.contains(event.target)) {
            speakerSuggestions.style.display = "none";
        }
    });

    /**
     * Verhindert, dass mehrere Filter gewählt werden
     */

    sessionSelect.addEventListener("change", () => {
        updateFilterStates(sessionSelect);
    });

    topicsSelect.addEventListener("change", () => {
        updateFilterStates(topicsSelect);
    });

    speakerInput.addEventListener("input", () => {
        updateFilterStates(speakerInput);
    });


    // Filter anwenden
    function applyFilters() {
        console.log("Filter werden angewendet...");

        const selectedSession = sessionSelect.value !== "all" ? sessionSelect.value : "";
        const selectedTopic = topicsSelect.value !== "all" ? topicsSelect.value : "";
        const speakerName = speakerInput.value.trim();

        let filterType = "", filterValue = "";

        // Kein Filter → "alle Reden"
        if (
            sessionSelect.value === "all" &&
            topicsSelect.value === "all" &&
            speakerName === ""
        ) {
            filterType = "all";
            filterValue = "all speeches";
        } else if (selectedSession) {
            filterType = "sessions";
            filterValue = selectedSession;
        } else if (selectedTopic) {
            filterType = "topics";
            filterValue = selectedTopic;
        } else if (speakerName) {
            filterType = "speakers";
            filterValue = speakerName;
        } else {
            alert("Bitte wählen Sie einen Filter aus.");
            return;
        }

        // Filter für Export-Buttons merken
        window.currentExportFilterType = filterType;
        window.currentExportFilterValue = filterValue;


        console.log(`Anfrage an /api/aggregation → type=${filterType}, value=${filterValue}`);

        const loader = document.getElementById("loader-container");
        if (loader) loader.style.display = "inline-block";

        fetch(`/api/aggregation?type=${encodeURIComponent(filterType)}&value=${encodeURIComponent(filterValue)}`)
            .then(res => res.json())
            .then(data => {
                if (!data || !data.nlpAggregation) {
                    alert("Keine Daten gefunden.");
                    return;
                }

                // Aktive Filteranzeige
                if (activeFilterText) {
                    const labels = {
                        sessions: "Sitzung",
                        topics: "Thema",
                        speakers: "Redner",
                        all: "Alle Reden"
                    };
                    activeFilterText.textContent = `Gefiltert nach: ${labels[filterType] || "Unbekannt"} → ${filterValue}`;
                }

                updateCharts(data.nlpAggregation);
            })
            .catch(err => console.error("Fehler bei /api/aggregation:", err))
            .finally(() => {
                if (loader) loader.style.display = "none";
            });
    }

    // Filter-Status aktualisieren
    function updateFilterStates(changedFilter) {
        const filters = [sessionSelect, topicsSelect, speakerInput];

        const oneFilterSelected = (
            sessionSelect.value !== "all" ||
            topicsSelect.value !== "all" ||
            speakerInput.value.trim() !== ""
        );

        filters.forEach(filter => {
            if (filter === changedFilter) return;
            filter.disabled = oneFilterSelected;
        });

        if (!oneFilterSelected) {
            filters.forEach(filter => filter.disabled = false);
        }
    }

    function getExportUrl(type, value, format) {
        if (type === "sessions") {
            return `/api/export/${format}/protokoll/${value}`;
        } else if (type === "topics") {
            return `/api/export/${format}/thema/${encodeURIComponent(value)}`;
        } else if (type === "speakers") {
            return `/api/export/${format}/redner/${encodeURIComponent(value)}`;
        } else if (type === "all") {
            return `/api/export/${format}/protokolle`; // ALLE Protokolle exportieren
        } else {
            alert("Export nur für Sitzung, Thema, Redner oder alle Reden möglich.");
            return null;
        }
    }

    exportPdfBtn.addEventListener("click", () => {
        const type = window.currentExportFilterType;
        const value = window.currentExportFilterValue;

        if (!type || !value) {
            alert("Bitte wähle zuerst einen Filter aus, um einen Export durchzuführen.");
            return;
        }

        const url = getExportUrl(type, value, "pdf");
        if (url) window.open(url, "_blank");
    });

    exportXmiBtn.addEventListener("click", () => {
        const type = window.currentExportFilterType;
        const value = window.currentExportFilterValue;

        if (!type || !value) {
            alert("Bitte wähle zuerst einen Filter aus, um einen Export durchzuführen.");
            return;
        }

        const url = getExportUrl(type, value, "xmi");
        if (url) window.open(url, "_blank");
    });


});
