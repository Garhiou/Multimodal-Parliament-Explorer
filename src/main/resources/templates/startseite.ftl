<!DOCTYPE html>
<html lang="de">
<!-- Implementiert von Luana Schäfer -->

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Multimodal Parliament Explorer</title>

    <link rel="stylesheet" href="/static/startseite.css">
</head>
<body>

<header class="header">
    <h1>Multimodal Parliament Explorer</h1>
    <nav class="nav-buttons">
        <a href="/">Startseite</a>
        <a href="/analyse">Analyse</a>
    </nav>
</header>

<div class="search-container">
    <h2 class="page-title">Startseite</h2>
    <div class="search-box">
        <input type="text" id="searchText" placeholder="Suche nach Reden, Redner oder Parteien...">
        <button id="searchButton">Suchen</button>
        <span id="loader-container" class="loader-container">
            <div class="loader"></div>
        </span>
    </div>
</div>


<div class="results-container">
    <h3 class="result-title">Gefundene Reden:</h3>
    <div id="reden-container">
        <p>Keine Reden gefunden.</p>
    </div>
</div>

<script src="/static/startseite.js"></script>
</body>
</html>
