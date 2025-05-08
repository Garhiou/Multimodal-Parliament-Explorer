<!DOCTYPE html>
<html lang="de">
<!-- Implementiert von Luana Schäfer -->
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Analyse</title>
    <link rel="stylesheet" href="/static/analyse.css">
</head>
<body>

<header class="header">
    <h1>Multimodal Parliament Explorer</h1>
    <nav class="nav-buttons">
        <a href="/">Startseite</a>
        <a href="/analyse">Analyse</a>
    </nav>
</header>

<div class="container">
    <h2 class="page-title">NLP-Analyse</h2>
    <p class="subtitle">Hier können Sie NLP-Analysen für mehrere Reden gleichzeitig anzeigen. Nutzen Sie die Filter, um gezielt nach Parteien, Themen oder Sitzungen zu filtern.</p>

    <!-- Filter-Bereich -->
    <div class="filter-container">
        <div class="filter-item">
            <label for="sessionSelect">Sitzung:</label>
            <select id="sessionSelect">
                <option value="">Lade Sitzungen...</option>
            </select>
        </div>

        <div class="filter-item">
            <label for="topicsSelect">Themen:</label>
            <select id="topicsSelect">
                <option value="">Lade Themen...</option>
            </select>
        </div>

        <div class="filter-item">
            <label for="speakerSearch">Redner:</label>
            <input type="text" id="speakerSearch" placeholder="Redner suchen">
        </div>

        <div class="filter-actions">
            <button id="applyFilters">Filtern</button>
            <span id="loader-container" class="loader-container">
                <div class="loader"></div>
            </span>
        </div>
    </div>


    <div id="speechCount" class="speech-count">
        Gefiltert nach: <span id="activeFilterText">–</span>
    </div>

    <!-- Diagramm-Bereich -->
    <div class="dashboard">
        <div class="chart-container">
            <h3>Themen-Verteilung</h3>
            <div id="topicsBubbleChart"></div>
        </div>

        <div class="chart-container">
            <h3>Named Entity Recognition</h3>
            <div id="namedEntitiesSunburst"></div>
        </div>

        <div class="chart-container">
            <h3>Sentiment-Analyse</h3>
            <div id="sentimentRadarChart"></div>
        </div>

        <div class="chart-container">
            <h3>Wortarten-Verteilung</h3>
            <div id="wordTypeBarChart"></div>
        </div>
    </div>

    <!-- Export-Buttons -->
    <div class="export-buttons">
        <button id="exportPdfBtn">PDF exportieren</button>
        <button id="exportXmiBtn">XMI exportieren</button>
        <p class="subtitle">Export kann ein bisschen Zeit beanspruchen</p>
    </div>

</div>

<script src="https://d3js.org/d3.v7.min.js"></script>
<script src="/static/analyse.js"></script>
<script src="/static/analysecharts.js"></script>
</body>
</html>