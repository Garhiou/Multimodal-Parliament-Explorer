<!DOCTYPE html>
<html lang="de">
<!-- Implementiert von Luana Schäfer -->

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Rede im Überblick</title>
    <link rel="stylesheet" href="/static/rede.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.2/css/all.min.css">

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
    <h2 class="page-title">Rede im Überblick</h2>

    <div class="rede-box">
        <!-- Linke Seite mit Video & Infos -->
        <div class="left-section">
            <div class="video-wrapper">
                <video id="redeVideo" controls>
                    <source src="" type="video/mp4">
                    Dein Browser unterstützt kein Video-Tag.
                </video>
            </div>
            <div class="speaker-container">
                <img id="speakerImage" alt="Redner Bild">
                <div class="speaker-divider"></div>
                <div class="speaker-details">
                    <h3 id="sitzung"></h3>
                    <p id="speakerName"></p>
                    <span id="party"></span>
                </div>
            </div>
            <div class="toggle-container">
                <span id="toggle-label">Markierungen:</span>
                <div class="toggle-switch">
                    <div class="toggle-option active" data-mode="off">Sentiment</div>
                    <div class="toggle-option" data-mode="ne">Named Entities</div>
                    <div class="toggle-option" data-mode="pos">POS</div>
                </div>
            </div>
        </div>

        <!-- Blaue Mittellinie zwischen Links & Rechts -->
        <div class="divider"></div>

        <!-- rechte Sektion -->
        <div class="right-section">
            <div class="text-section">
                <div class="text-container" id="speechText"></div>
            </div>


            <!-- Legende unter dem Rede-Text -->
            <div class="legend-wrapper">
                <div id="legend-container" class="legend-container hidden">
                    <div id="legend-ne" class="legend hidden">
                        <div class="legend-item"><span class="legend-color legend-person"></span> Person</div>
                        <div class="legend-item"><span class="legend-color legend-location"></span> Ort</div>
                        <div class="legend-item"><span class="legend-color legend-misc"></span> Sonstige</div>
                        <div class="legend-item"><span class="legend-color legend-organization"></span> Organisation</div>

                    </div>
                    <div id="legend-pos" class="legend hidden">
                        <div class="legend-item"><span class="legend-color legend-nom"></span> NOM</div>
                        <div class="legend-item"><span class="legend-color legend-nam"></span> NAME</div>
                        <div class="legend-item"><span class="legend-color legend-verb"></span> VERB</div>
                        <div class="legend-item"><span class="legend-color legend-adj"></span> ADJ</div>
                        <div class="legend-item"><span class="legend-color legend-adv"></span> ADV</div>
                    </div>

                    <div id="legend-sentiment" class="legend hidden">
                        <div class="legend-item"><i class="fa-solid fa-face-smile" style="color: #27AE60;"></i> Positiv</div>
                        <div class="legend-item"><i class="fa-solid fa-face-meh" style="color: #F39C12;"></i> Neutral</div>
                        <div class="legend-item"><i class="fa-solid fa-face-frown" style="color: #E74C3C;"></i> Negativ</div>
                    </div>

                </div>
            </div>
        </div>
    </div>


    <!-- DIAGRAMME -->
    <div class="dashboard">
        <div class="chart-container">
            <h3>Themen-Verteilung</h3>
            <div id="topicsBubbleChart"></div>
        </div>

        <div class="chart-container">
            <h3>Sentiment-Analyse</h3>
            <div id="sentimentRadarChart"></div>
        </div>

        <div class="chart-container">
            <h3>Named Entity Recognition</h3>
            <div id="namedEntitiesSunburst"></div>
        </div>

        <div class="chart-container">
            <h3>Wortarten-Verteilung</h3>
            <div id="wordTypeBarChart"></div>
        </div>
    </div>


</div>

<script src="/static/rede.js"></script>
<script src="https://d3js.org/d3.v7.min.js"></script>
<script src="/static/chart.js"></script>

</body>
</html>
