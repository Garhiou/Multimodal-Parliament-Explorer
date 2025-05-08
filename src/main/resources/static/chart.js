document.addEventListener("DOMContentLoaded", function () {
    //Implementiert von Luana Schäfer

    const redeId = window.location.pathname.split("/").pop();


    // Themen laden
    fetch(`/api/rede/${redeId}`)
        .then(response => response.json())
        .then(data => {

            // Topic Diagramm aufrufen
            if (data.topics && Array.isArray(data.topics) && data.topics.length > 0) {
                const topicsData = data.topics.map(topic => ({
                    topic: topic.topic,
                    score: parseFloat(topic.averageScore) * 100
                }));
                createBubbleChart(topicsData);
            } else {
                console.warn("Keine Themen gefunden.");
            }

            // POS Diagramm aufrufen
            if (data.posTags && Array.isArray(data.posTags) && data.posTags.length > 0) {
                createPOSBarChart(data.posTags);
            } else {
                console.warn("Keine POS-Tags gefunden.");
            }

            // Sentiment Diagramm aufrufen
            if (data.sentiments && Array.isArray(data.sentiments) && data.sentiments.length > 0) {
                createSentimentRadarChart(data.sentiments);
            } else {
                console.warn("Keine Sentiment-Daten gefunden.");
            }

            // Named Entities Diagramm aufrufen
            if (data.namedEntities && Array.isArray(data.namedEntities) && data.namedEntities.length > 0) {
                createNamedEntitiesSunburst(data.namedEntities);
            } else {
                console.warn("Keine Named Entity Daten gefunden.");
            }

        })
        .catch(error => {
            console.error("Fehler beim Laden der Rede-Daten:", error);
        });

    // ----------------------------

    function createBubbleChart(data) {

        const container = document.getElementById("topicsBubbleChart");

        // Vorherige Inhalte im Diagramm-Container löschen
        d3.select("#topicsBubbleChart").selectAll("*").remove();

        const width = container.clientWidth;
        const height = container.clientHeight || 400;


        // SVG-Bereich initialisieren und zentrieren
        const svg = d3.select("#topicsBubbleChart")
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .style("min-height", "400px")
            .append("g")
            .attr("transform", `translate(${width / 2}, ${height / 2})`);

        // Maximaler Radius für die größten Bubbles
        const maxBubbleSize = Math.min(width, height) / 5;

        // Radius-Skalierung abhängig vom Score
        const radiusScale = d3.scaleSqrt()
            .domain([0, d3.max(data, d => d.score)])
            .range([15, maxBubbleSize]);

        // Farbpalette für die Kreise
        const colorPalette = [
            "rgba(30, 58, 138, 0.8)",
            "rgba(138, 30, 138, 0.8)",
            "rgba(230, 126, 34, 0.8)",
            "rgba(39, 174, 96, 0.8)",
            "rgba(231, 76, 60, 0.8)",
            "rgba(189, 195, 199, 0.8)"
        ];
        const colorScale = d3.scaleOrdinal(colorPalette);

        // Simulation zur Positionierung der Bubbles ohne Überlappung
        const simulation = d3.forceSimulation(data)
            .force("x", d3.forceX().strength(0.2).x(0))
            .force("y", d3.forceY().strength(0.2).y(0))
            .force("collision", d3.forceCollide().radius(d => radiusScale(d.score) + 4))
            .on("tick", ticked);


        // Gruppenelemente pro Bubble anlegen
        const bubbles = svg.selectAll(".bubble")
            .data(data)
            .enter()
            .append("g")
            .attr("class", "bubble");

        // Kreise zeichnen
        bubbles.append("circle")
            .attr("r", d => radiusScale(d.score))
            .attr("fill", (d, i) => colorScale(i))
            .attr("stroke", "#fff")
            .attr("stroke-width", 2);


        // Text-Labels zentriert auf die Kreise setzen
        bubbles.append("text")
            .attr("text-anchor", "middle")
            .attr("dy", ".3em")
            .text(d => d.topic)
            .style("font-size", d => `${Math.max(8, radiusScale(d.score) / 5)}px`)
            .style("fill", "#ffffff")
            .style("stroke", "#505050")
            .style("stroke-width", "2px")
            .style("paint-order", "stroke fill")
            .style("font-weight", "bold")
            .style("pointer-events", "none"); // Hover aus

        // Positionsaktualisierung bei jeder Iteration der Simulation
        function ticked() {
            bubbles.attr("transform", d => `translate(${d.x},${d.y})`);
        }

        console.log("Bubble Chart erfolgreich erstellt!");
    }

    // ----------------------------


    function createPOSBarChart(posTags) {
        console.log("Starte Erstellung des POS-Balkendiagramms...");

        const container = document.getElementById("wordTypeBarChart");

        // Vorherigen SVG-Inhalt im Container löschen
        d3.select("#wordTypeBarChart").selectAll("*").remove();

        // Diagramm-Maße definieren
        const margin = { top: 30, right: 20, bottom: 40, left: 50 };
        const width = container.clientWidth - margin.left - margin.right;
        const height = 300;


        // POS-Farbzuordnung + Labels
        const posMapping = {
            "NN": { color: "#34495E", label: "Nomen" },
            "NE": { color: "#8A1E8A", label: "Eigenname" },
            "ADJ": { color: "#F39C12", label: "Adjektiv" },
            "ADJA": { color: "#F39C12", label: "Adjektiv" },
            "ADJD": { color: "#F39C12", label: "Adjektiv" },
            "ADV": { color: "#E74C3C", label: "Adverb" },
            "VVFIN": { color: "#27AE60", label: "Verb" },
            "VVINF": { color: "#27AE60", label: "Verb" },
            "VVIZU": { color: "#27AE60", label: "Verb" },
            "VVPP": { color: "#27AE60", label: "Verb" },
            "VAFIN": { color: "#27AE60", label: "Verb" },
            "VAIMP": { color: "#27AE60", label: "Verb" },
            "VAINF": { color: "#27AE60", label: "Verb" },
            "VMPP": { color: "#27AE60", label: "Verb" },
            "VMFIN": { color: "#27AE60", label: "Verb" },
            "VMINF": { color: "#27AE60", label: "Verb" },
            "VAPP": { color: "#27AE60", label: "Verb" },
        };


        // POS-Tags zählen, nur bekannte aus dem Mapping berücksichtigen
        const posCounts = {};
        posTags.forEach(token => {
            const pos = token.pos;
            if (posMapping[pos]) {
                if (!posCounts[pos]) {
                    posCounts[pos] = 0;
                }
                posCounts[pos]++;
            }
        });

        // Strukturierte Datenliste für D3 erzeugen
        const data = Object.keys(posCounts).map(pos => ({
            pos: pos,
            count: posCounts[pos],
            color: posMapping[pos].color,
            label: posMapping[pos].label
        }));

        if (data.length === 0) {
            console.warn("Keine gültigen POS-Daten zum Anzeigen!");
            return;
        }

        // Sortierung nach Farbe
        const colorOrder = ["#34495E", "#8A1E8A", "#F39C12", "#E74C3C", "#27AE60", "#27AE60"];
        data.sort((a, b) => colorOrder.indexOf(a.color) - colorOrder.indexOf(b.color));


        // Tooltip erstellen
        const tooltip = d3.select("body")
            .append("div")
            .style("position", "absolute")
            .style("background", "rgba(0,0,0,0.8)")
            .style("color", "white")
            .style("padding", "5px 10px")
            .style("border-radius", "5px")
            .style("font-size", "12px")
            .style("pointer-events", "none")
            .style("opacity", 0);

        // SVG erstellen
        const svg = d3.select("#wordTypeBarChart")
            .append("svg")
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top + margin.bottom)
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        // Skalen
        const xScale = d3.scaleBand()
            .domain(data.map(d => d.pos))
            .range([0, width])
            .padding(0.2);

        const yScale = d3.scaleLinear()
            .domain([0, d3.max(data, d => d.count)])
            .range([height, 0]);

        // X-Achse hinzufügen
        svg.append("g")
            .attr("transform", `translate(0, ${height})`)
            .call(d3.axisBottom(xScale))
            .selectAll("text")
            .attr("transform", "rotate(-45)")
            .style("text-anchor", "end")
            .style("font-size", "10px");

        // Y-Achse hinzufügen
        svg.append("g")
            .call(d3.axisLeft(yScale).ticks(5));

        // Balken hinzufügen
        svg.selectAll(".bar")
            .data(data)
            .enter()
            .append("rect")
            .attr("class", "bar")
            .attr("x", d => xScale(d.pos))
            .attr("y", d => yScale(d.count))
            .attr("width", xScale.bandwidth())
            .attr("height", d => height - yScale(d.count))
            .attr("fill", d => d.color) // Färben

            // Tooltip beim Hover einblenden
            .on("mouseover", (event, d) => {
                tooltip.transition().duration(200).style("opacity", 1);
                tooltip.html(d.label) // Label anzeigen
                    .style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })
            .on("mousemove", (event) => {
                tooltip.style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })
            .on("mouseout", () => {
                tooltip.transition().duration(200).style("opacity", 0);
            });

        console.log("POS-Balkendiagramm erfolgreich erstellt mit Hover-Funktion!");
    }

    // ----------------------------


    function createSentimentRadarChart(sentiments) {

        const container = document.getElementById("sentimentRadarChart");

        // Vorherige Inhalte im Container löschen
        d3.select("#sentimentRadarChart").selectAll("*").remove();

        // Diagrammgröße und Radius definieren
        const width = 400, height = 400, margin = 50;
        const radius = Math.min(width, height) / 2 - margin;

        // Sentiment-Farben
        const sentimentColors = {
            "Positiv": "#27AE60",
            "Neutral": "#F39C12",
            "Negativ": "#E74C3C"
        };

        // Sentiment-Typen zählen
        const sentimentCounts = { "Positiv": 0, "Neutral": 0, "Negativ": 0 };

        sentiments.forEach(sentiment => {
            if (sentiment.sentiment > 0.2) {
                sentimentCounts["Positiv"]++;
            } else if (sentiment.sentiment >= -0.2 && sentiment.sentiment <= 0.2) {
                sentimentCounts["Neutral"]++;
            } else {
                sentimentCounts["Negativ"]++;
            }
        });

        // Datenstruktur für das Diagramm aufbauen
        const data = Object.keys(sentimentCounts).map(key => ({
            sentiment: key,
            count: sentimentCounts[key],
            color: sentimentColors[key]
        }));

        // Berechnung der Winkel für jede Achse im Radar
        const angleSlice = (2 * Math.PI) / data.length;
        const maxValue = d3.max(data, d => d.count);

        const rScale = d3.scaleLinear()
            .domain([0, maxValue])
            .range([0, radius]);

        // SVG erstellen
        const svg = d3.select("#sentimentRadarChart")
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform", `translate(${width / 2}, ${height / 2})`);

        // Linien für das Netz zeichnen
        for (let i = 0; i < 5; i++) {
            svg.append("circle")
                .attr("r", (radius / 5) * (i + 1))
                .style("fill", "none")
                .style("stroke", "#ddd")
                .style("stroke-width", "1px");
        }

        // Achsen für jede Kategorie
        data.forEach((d, i) => {
            const angle = angleSlice * i;
            const x = rScale(maxValue) * Math.cos(angle);
            const y = rScale(maxValue) * Math.sin(angle);

            // Linie zeichnen
            svg.append("line")
                .attr("x1", 0)
                .attr("y1", 0)
                .attr("x2", x)
                .attr("y2", y)
                .style("stroke", "#999")
                .style("stroke-width", "1px");

            // Text-Label am Ende der Linie
            svg.append("text")
                .attr("x", x * 1.1)
                .attr("y", y * 1.1)
                .text(d.sentiment)
                .style("font-size", "14px")
                .style("font-weight", "bold")
                .attr("text-anchor", "middle")
                .style("fill", d.color);
        });

        // Linienpfad für die Werte zeichnen
        const radarLine = d3.lineRadial()
            .radius(d => rScale(d.count))
            .angle((d, i) => i * angleSlice + Math.PI / 2)
            .curve(d3.curveLinearClosed); // Schließt die Linie

        // Linie zeichnen
        svg.append("path")
            .datum([...data, data[0]])  // Ersten Punkt am Ende wiederholen
            .attr("d", radarLine)
            .style("fill", "none")
            .style("stroke", "#1E3A8A")
            .style("stroke-width", 2)
            .style("stroke-opacity", 0.8);

        // Punkte für die Werte
        svg.selectAll(".dot")
            .data(data)
            .enter()
            .append("circle")
            .attr("cx", d => rScale(d.count) * Math.cos(data.indexOf(d) * angleSlice))
            .attr("cy", d => rScale(d.count) * Math.sin(data.indexOf(d) * angleSlice))
            .attr("r", 5)
            .style("fill", d => d.color);

        console.log("Sentiment Radar Chart erfolgreich erstellt!");
    }

    // ----------------------------

    function createNamedEntitiesSunburst(namedEntities) {

        const container = document.getElementById("namedEntitiesSunburst");

        // Vorherige Inhalte im Diagramm löschen
        d3.select("#namedEntitiesSunburst").selectAll("*").remove();

        const width = 400, height = 400;
        const radius = Math.min(width, height) / 2;


        // Feste Reihenfolge für Hauptkategorien
        const entityOrder = ["PERSON", "LOCATION", "ORGANIZATION", "MISC"];

        // Hierarchische Struktur für Sunburst vorbereiten
        const entityHierarchy = { name: "Named Entities", children: [] };
        const entityMap = {};

        // Named Entities nach Typ gruppieren
        namedEntities.forEach(entity => {
            const type = entity.type;
            const text = entity.text;

            if (!entityMap[type]) {
                entityMap[type] = { name: type, children: [] };
                entityHierarchy.children.push(entityMap[type]);
            }

            entityMap[type].children.push({ name: text, value: 1 });
        });

        // Sortierung der Hauptkategorien nach fester Reihenfolge
        entityHierarchy.children.sort((a, b) => entityOrder.indexOf(a.name) - entityOrder.indexOf(b.name));

        // Sortierung der äußeren Segmente alphabetisch innerhalb der jeweiligen Kategorie
        entityHierarchy.children.forEach(category => {
            category.children.sort((a, b) => a.name.localeCompare(b.name));
        });

        // D3 Hierarchie erstellen
        const root = d3.hierarchy(entityHierarchy).sum(d => d.value);

        // Partition für Sunburst
        const partition = d3.partition().size([2 * Math.PI, radius]);
        partition(root);

        // Farbskala für Named Entity Typen
        const colorScale = d3.scaleOrdinal()
            .domain(entityOrder)
            .range(["#1E3A8A", "#E67E22", "#8A1E8A", "#27AE60"]);

        // SVG erstellen
        const svg = d3.select("#namedEntitiesSunburst")
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform", `translate(${width / 2}, ${height / 2})`);

        // Arc-Generator konfigurieren für Kreissegmente
        const arc = d3.arc()
            .startAngle(d => d.x0)
            .endAngle(d => d.x1)
            .innerRadius(d => d.y0)
            .outerRadius(d => d.y1);

        // Tooltip
        const tooltip = d3.select("body")
            .append("div")
            .attr("id", "tooltip")
            .style("opacity", 0);

        // Segmente hinzufügen
        svg.selectAll("path")
            .data(root.descendants().slice(1)) // Erste Stufe (Root) überspringen
            .enter()
            .append("path")
            .attr("d", arc)
            .attr("fill", d => colorScale(d.depth === 1 ? d.data.name : d.parent.data.name) || "#BDC3C7")
            .attr("stroke", "#fff")
            .attr("cursor", "pointer")
            .attr("data-name", d => d.data.name)

            // Tooltip bei Hover anzeigen
            .on("mouseover", (event, d) => {
                tooltip.style("opacity", 1).attr("data-tooltip", d.data.name);
            })
            .on("mousemove", (event) => {
                tooltip.style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })
            .on("mouseout", () => {
                tooltip.style("opacity", 0);
            });

        // Text-Beschriftung für den inneren Ring
        svg.selectAll(".inner-label")
            .data(root.descendants().filter(d => d.depth === 1))
            .enter()
            .append("text")
            .attr("class", "inner-label")
            .attr("transform", d => {
                const centroid = arc.centroid(d);
                return `translate(${centroid[0]}, ${centroid[1]})`;
            })
            .attr("dy", "0.35em")
            .attr("text-anchor", "middle")
            .style("font-size", "14px")
            .style("fill", "#ffffff")
            .style("font-weight", "bold")
            .text(d => d.data.name);

        console.log("Named Entities Sunburst Chart erfolgreich erstellt!");
    }

});