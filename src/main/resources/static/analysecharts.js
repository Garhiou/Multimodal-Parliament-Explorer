// analysecharts.js
document.addEventListener("DOMContentLoaded", () => {

    window.updateCharts = function (nlpAggregation) {
        if (!nlpAggregation) {
            console.warn("Keine NLP-Daten vorhanden!");
            return;
        }

        // Topics vorbereiten (Bubble Chart)
        const topics = (nlpAggregation.topics || []).map(t => ({
            topic: t._id,
            score: parseFloat(t.averageScore) * 100
        }));

        // POS-Tags vorbereiten (Balkendiagramm)
        const posTags = (nlpAggregation.pos_tags || []).map(p => ({
            pos: p._id,
            count: p.count
        }));

        // Named Entities (Text) vorbereiten (SunBurst außen)
        const namedEntitiesByText = (nlpAggregation.namedEntitiesByText || []).map(e => ({
            type: e._id.type || "MISC",
            text: e._id.text,
            count: e.count
        }));

        // Named Entities (nur Typ) vorbereiten (Sunburst innen)
        const namedEntitiesByType = (nlpAggregation.namedEntitiesByType || []).map(e => ({
            _id: e._id || "MISC",
            count: e.count
        }));

        // Sentiment-Werte vorbereiten (Radar-Chart)
        const sentimentsRaw = nlpAggregation.sentiment || [];

        // Klassifiziere Sentiments nach Score
        let positive = 0, neutral = 0, negative = 0;

        sentimentsRaw.forEach(s => {
            const score = s._id;
            const count = s.count;

            if (score < -0.3) {
                negative += count;
            } else if (score > 0.3) {
                positive += count;
            } else {
                neutral += count;
            }
        });

        const sentiments = [
            {sentiment: "Positiv", count: positive},
            {sentiment: "Neutral", count: neutral},
            {sentiment: "Negativ", count: negative}
        ];


        // erzeuge Diagramme
        createBubbleChart(topics);
        createPOSBarChart(posTags);
        createSentimentRadarChart(sentiments);
        createNamedEntitiesSunburst(namedEntitiesByType, namedEntitiesByText);
    };

    // ----------------------------

    function createBubbleChart(data) {

        const container = document.getElementById("topicsBubbleChart");

        // Vorherige Inhalte des Diagrammbereichs löschen
        d3.select("#topicsBubbleChart").selectAll("*").remove();

        const width = container.clientWidth;
        const height = container.clientHeight || 400;

        // SVG-Container zentriert anlegen
        const svg = d3.select("#topicsBubbleChart")
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .style("min-height", "400px")
            .append("g")
            .attr("transform", `translate(${width / 2}, ${height / 2})`);

        // Maximaler Radius
        const maxBubbleSize = Math.min(width, height) / 6;

        // Skala zur Umrechnung von Topic-Score auf Radiusgröße
        const radiusScale = d3.scaleSqrt()
            .domain([0, d3.max(data, d => d.score)])
            .range([15, maxBubbleSize]);

        // Farbpalette
        const colorPalette = [
            "rgba(30, 58, 138, 0.8)",
            "rgba(138, 30, 138, 0.8)",
            "rgba(230, 126, 34, 0.8)",
            "rgba(39, 174, 96, 0.8)",
            "rgba(231, 76, 60, 0.8)",
            "rgba(189, 195, 199, 0.8)"
        ];
        const colorScale = d3.scaleOrdinal(colorPalette);

        // Keine Überlappungen
        const simulation = d3.forceSimulation(data)
            .force("x", d3.forceX().strength(0.2).x(0))
            .force("y", d3.forceY().strength(0.2).y(0))
            .force("collision", d3.forceCollide().radius(d => radiusScale(d.score) + 4))
            .on("tick", ticked);

        // Bubbles erzeugen
        const bubbles = svg.selectAll(".bubble")
            .data(data)
            .enter()
            .append("g")
            .attr("class", "bubble");

        // Kreise zeichnen (mit Farbe und Radius)
        bubbles.append("circle")
            .attr("r", d => radiusScale(d.score))
            .attr("fill", (d, i) => colorScale(i))
            .attr("stroke", "#fff")
            .attr("stroke-width", 2);

        // Topic-Namen mittig in die Kreise schreiben
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
            .style("pointer-events", "none");

        function ticked() {
            bubbles.attr("transform", d => `translate(${d.x},${d.y})`);
        }

        console.log("Bubble Chart erfolgreich erstellt!");
    }

    // ----------------------------


    function createPOSBarChart(posTags) {
        const container = document.getElementById("wordTypeBarChart");

        // Vorherige Inhalte löschen
        d3.select("#wordTypeBarChart").selectAll("*").remove();

        const margin = {top: 30, right: 20, bottom: 40, left: 55};
        const width = container.clientWidth - margin.left - margin.right;
        const height = 300;

        // Mapping: POS-Tags zu Farbe und Label
        const posMapping = {
            "NN": {color: "#34495E", label: "Nomen"},
            "NE": {color: "#8A1E8A", label: "Eigenname"},
            "ADJ": {color: "#F39C12", label: "Adjektiv"},
            "ADJA": {color: "#F39C12", label: "Adjektiv"},
            "ADJD": {color: "#F39C12", label: "Adjektiv"},
            "ADV": {color: "#E74C3C", label: "Adverb"},
            "VVFIN": {color: "#27AE60", label: "Verb"},
            "VVINF": {color: "#27AE60", label: "Verb"},
            "VVIZU": {color: "#27AE60", label: "Verb"},
            "VVPP": {color: "#27AE60", label: "Verb"},
            "VAFIN": {color: "#27AE60", label: "Verb"},
            "VAIMP": {color: "#27AE60", label: "Verb"},
            "VAINF": {color: "#27AE60", label: "Verb"},
            "VMPP": {color: "#27AE60", label: "Verb"},
            "VMFIN": {color: "#27AE60", label: "Verb"},
            "VMINF": {color: "#27AE60", label: "Verb"},
            "VAPP": {color: "#27AE60", label: "Verb"},
        };

        // Daten formatieren
        const data = posTags
            .filter(token => posMapping[token.pos])
            .map(token => ({
                pos: token.pos,
                count: token.count,
                color: posMapping[token.pos].color,
                label: posMapping[token.pos].label
            }));

        if (data.length === 0) return;

        // Tooltip
        const tooltip = d3.select("body")
            .append("div")
            .attr("class", "chart-tooltip");

        // SVG erstellen
        const svg = d3.select("#wordTypeBarChart")
            .append("svg")
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top + margin.bottom)
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        // X-Achse: POS-Tags
        const xScale = d3.scaleBand()
            .domain(data.map(d => d.pos))
            .range([0, width])
            .padding(0.2);

        // Y-Achse: Häufigkeit
        const yScale = d3.scaleLinear()
            .domain([0, d3.max(data, d => d.count)])
            .range([height, 0]);

        // X-Achse rendern
        svg.append("g")
            .attr("transform", `translate(0, ${height})`)
            .call(d3.axisBottom(xScale))
            .selectAll("text")
            .attr("transform", "rotate(-45)")
            .style("text-anchor", "end")
            .style("font-size", "10px");

        // Y-Achse rendern
        svg.append("g")
            .call(d3.axisLeft(yScale).ticks(5));

        // Balken zeichnen
        svg.selectAll(".bar")
            .data(data)
            .enter()
            .append("rect")
            .attr("class", "bar")
            .attr("x", d => xScale(d.pos))
            .attr("y", d => yScale(d.count))
            .attr("width", xScale.bandwidth())
            .attr("height", d => height - yScale(d.count))
            .attr("fill", d => d.color)
            .on("mouseover", (event, d) => {
                tooltip.transition().duration(200).style("opacity", 1);
                tooltip.html(d.label)
                    .style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })

            // Tooltip einblenden
            .on("mousemove", (event) => {
                tooltip.style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })
            .on("mouseout", () => {
                tooltip.transition().duration(200).style("opacity", 0);
            });
    }

    // ----------------------------


    function createSentimentRadarChart(sentiments) {
        const container = document.getElementById("sentimentRadarChart");

        // Vorherige Inhalte löschen
        d3.select("#sentimentRadarChart").selectAll("*").remove();

        // Maße und Radius definieren
        const width = 400, height = 400, margin = 50;
        const radius = Math.min(width, height) / 2 - margin;

        // Farben je Sentiment
        const sentimentColors = {
            "Positiv": "#27AE60",
            "Neutral": "#F39C12",
            "Negativ": "#E74C3C"
        };

        // Winkel und Farbe berechnen
        const data = sentiments.map((s, i) => ({
            ...s,
            angle: (2 * Math.PI / sentiments.length) * i,
            color: sentimentColors[s.sentiment]
        }));

        const maxValue = d3.max(data, d => d.count);
        const rScale = d3.scaleLinear().domain([0, maxValue]).range([0, radius]);
        const angleSlice = (2 * Math.PI) / data.length;

        // SVG-Container erzeugen
        const svg = d3.select("#sentimentRadarChart")
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform", `translate(${width / 2}, ${height / 2})`);

        // Netz
        for (let i = 0; i < 5; i++) {
            svg.append("circle")
                .attr("r", (radius / 5) * (i + 1))
                .style("fill", "none")
                .style("stroke", "#ccc");
        }

        // Achsen und Labels
        data.forEach(d => {
            const x = rScale(maxValue) * Math.cos(d.angle);
            const y = rScale(maxValue) * Math.sin(d.angle);

            svg.append("line")
                .attr("x1", 0)
                .attr("y1", 0)
                .attr("x2", x)
                .attr("y2", y)
                .style("stroke", "#999");

            svg.append("text")
                .attr("x", x * 1.1)
                .attr("y", y * 1.1)
                .attr("text-anchor", "middle")
                .style("font-weight", "bold")
                .style("fill", d.color)
                .text(d.sentiment);
        });

        // Radar-Linie
        const radarLine = d3.lineRadial()
            .radius(d => rScale(d.count))
            .angle((d, i) => i * angleSlice + Math.PI / 2)
            .curve(d3.curveLinearClosed);

        svg.append("path")
            .datum([...data, data[0]]) // Pfad schließen
            .attr("d", radarLine)
            .style("fill", "none")
            .style("stroke", "#1E3A8A")
            .style("stroke-width", 2);

        // Punkte
        svg.selectAll(".dot")
            .data(data)
            .enter()
            .append("circle")
            .attr("cx", d => rScale(d.count) * Math.cos(d.angle))
            .attr("cy", d => rScale(d.count) * Math.sin(d.angle))
            .attr("r", 5)
            .style("fill", d => d.color);

        console.log("Sentiment Radar Chart erfolgreich erstellt!");
    }


    // ----------------------------

    function createNamedEntitiesSunburst(namedEntitiesByType, namedEntitiesByText) {
        const container = document.getElementById("namedEntitiesSunburst");

        // Vorherige Inalte entfernen
        d3.select("#namedEntitiesSunburst").selectAll("*").remove();

        const width = 400, height = 400, radius = Math.min(width, height) / 2;

        // Typenreihenfolge (wichtig für die Farbgebung)
        const entityOrder = ["PERSON", "ORG", "LOC", "MISC"];

        // Gesamtzahl der Typen (für innere Segmente)
        const typeCounts = {};
        (namedEntitiesByType || []).forEach(e => {
            typeCounts[e._id] = e.count;
        });

        //Text er Typen (für äußere Segmente)
        const textMap = {};
        (namedEntitiesByText || []).forEach(e => {
            const type = e.type || "MISC";
            const text = e.text || "unbekannt";
            if (!textMap[type]) textMap[type] = [];
            textMap[type].push({
                name: text,
                rawCount: e.count  // Count für Tooltip
            });
        });

        // Jeder Text bekommt anteilig einen Wert basierend auf Gesamtmenge pro Typ
        Object.keys(textMap).forEach(type => {
            const children = textMap[type];
            const totalValue = typeCounts[type] || children.length;
            const valuePerChild = totalValue / children.length;

            textMap[type] = children.map(child => ({
                name: child.name,
                value: valuePerChild,
                tooltipCount: child.rawCount
            }));
        });


        // Sunburst-Hierarchie aufbauen
        const hierarchy = {
            name: "Named Entities",
            children: Object.keys(typeCounts).map(type => ({
                name: type,
                value: typeCounts[type],
                children: textMap[type] || []
            }))
        };


        const root = d3.hierarchy(hierarchy).sum(d => d.children ? 0 : d.value);

        // Partition (Kreis schließen)
        const partition = d3.partition().size([2 * Math.PI, radius]);
        partition(root);

        // Farbskala nach Entity-Typ
        const colorScale = d3.scaleOrdinal()
            .domain(entityOrder)
            .range(["#1E3A8A", "#E67E22", "#8A1E8A", "#27AE60"]);

        // SVG vorbereiten
        const svg = d3.select("#namedEntitiesSunburst")
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform", `translate(${width / 2}, ${height / 2})`);

        const arc = d3.arc()
            .startAngle(d => d.x0)
            .endAngle(d => d.x1)
            .innerRadius(d => d.y0)
            .outerRadius(d => d.y1);

        // Tooltip erstellen
        const tooltip = d3.select("body")
            .append("div")
            .attr("id", "tooltip")
            .style("opacity", 0)
            .style("position", "absolute")
            .style("background", "rgba(0,0,0,0.8)")
            .style("color", "#fff")
            .style("padding", "6px 10px")
            .style("border-radius", "4px")
            .style("font-size", "12px")
            .style("pointer-events", "none");

        // Segmente erstellen
        svg.selectAll("path")
            .data(root.descendants().slice(1)) // ohne Root
            .enter()
            .append("path")
            .attr("d", arc)
            .attr("fill", d => {
                if (d.depth === 1) return colorScale(d.data.name);       // Typ-Farbe
                if (d.parent) return colorScale(d.parent.data.name);     // Text-Farbe nach Typ
                return "#BDC3C7";
            })
            .attr("stroke", "#fff")
            .on("mouseover", (event, d) => {
                const name = d.data.name;
                const count = d.data.tooltipCount !== undefined ? d.data.tooltipCount : d.value;
                tooltip.style("opacity", 1).html(`${name} (${count})`);
            })
            .on("mousemove", event => {
                tooltip
                    .style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })
            .on("mouseout", () => tooltip.style("opacity", 0));

        // Beschriftung im inneren Ring (für Typen)
        svg.selectAll(".label")
            .data(root.descendants().filter(d => d.depth === 1))
            .enter()
            .append("text")
            .attr("transform", d => {
                const [x, y] = arc.centroid(d);
                return `translate(${x}, ${y})`;
            })
            .attr("text-anchor", "middle")
            .style("fill", "#fff")
            .style("font-weight", "bold")
            .style("font-size", "13px")
            .text(d => d.data.name);
    }
});