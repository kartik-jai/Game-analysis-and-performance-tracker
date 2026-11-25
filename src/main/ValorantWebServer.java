package main;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.MatchStats;
import service.GameApiClient;
import service.LocalLogApiService;
import utility.ApiException;
import utility.ConfigManager;
import utility.DatabaseManager;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * ValorantWebServer (Main / Chrome Presentation Layer)
 * -------------------------------------------------------------------------
 * Starts a lightweight embedded Java HTTP Server (using JDK's built-in
 * com.sun.net.httpserver.HttpServer) on http://localhost:8080, connects it
 * to our modular Java packages (model, service, utility), and automatically
 * opens the Valorant Analytics Dashboard in Google Chrome!
 */
public class ValorantWebServer {

    private static final int PORT = 8080;

    public static void startChromeWebApp() {
        ConfigManager config = new ConfigManager();
        DatabaseManager db = new DatabaseManager(config.getDatabaseUrl());
        GameApiClient localLogService = new LocalLogApiService(config.getLocalLogPath());

        // Seed initial data into SQL database if empty
        if (db.getAllMatches().isEmpty()) {
            try {
                List<MatchStats> sample = localLogService.fetchRecentMatches(
                        config.getDefaultPlayerName(),
                        config.getDefaultPlayerTag(),
                        config.getDefaultRegion()
                );
                db.saveMatches(sample);
            } catch (ApiException ignored) {
            }
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // 1. Serve the Chrome Web UI (web/index.html)
            server.createContext("/", exchange -> {
                Path htmlPath = Paths.get("web", "index.html");
                if (Files.exists(htmlPath)) {
                    byte[] bytes = Files.readAllBytes(htmlPath);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    sendText(exchange, 404, "web/index.html not found");
                }
            });

            // 2. REST API endpoint returning SQL matches as JSON
            server.createContext("/api/matches", exchange -> {
                List<MatchStats> matches = db.getAllMatches();
                String json = matchesToJson(matches);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.setExecutor(null);
            server.start();

            String url = "http://localhost:" + PORT;
            System.out.println("=====================================================================");
            System.out.println("  VALORANT ANALYTICS TRACKER - RUNNING IN GOOGLE CHROME              ");
            System.out.println("  URL: " + url);
            System.out.println("  SQL Engine: " + db.getStorageStatusMessage());
            System.out.println("=====================================================================");

            openInChrome(url);
        } catch (IOException e) {
            // If port 8080 is busy, open web/index.html directly in Chrome
            File htmlFile = new File("web/index.html");
            openInChrome(htmlFile.toURI().toString());
        }
    }

    private static void openInChrome(String url) {
        try {
            // Try launching Google Chrome explicitly on Windows
            new ProcessBuilder("cmd", "/c", "start", "chrome", url).start();
        } catch (Exception e) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String matchesToJson(List<MatchStats> matches) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matches.size(); i++) {
            MatchStats m = matches.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"matchId\":\"%s\",\"playerName\":\"%s\",\"playerTag\":\"%s\",\"region\":\"%s\"," +
                    "\"mapName\":\"%s\",\"agentName\":\"%s\",\"gameMode\":\"%s\",\"primaryWeapon\":\"%s\"," +
                    "\"kills\":%d,\"deaths\":%d,\"assists\":%d,\"roundsWon\":%d,\"roundsLost\":%d," +
                    "\"headshots\":%d,\"bodyshots\":%d,\"legshots\":%d,\"averageCombatScore\":%d,\"matchDate\":\"%s\"}",
                     m.getMatchId(), m.getPlayerName(), m.getPlayerTag(), m.getRegion(),
                    m.getMapName(), m.getAgentName(), m.getGameMode(), m.getPrimaryWeapon(),
                    m.getKills(), m.getDeaths(), m.getAssists(), m.getRoundsWon(), m.getRoundsLost(),
                    m.getHeadshots(), m.getBodyshots(), m.getLegshots(), m.getAverageCombatScore(), m.getMatchDate()
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    public static void main(String[] args) {
        startChromeWebApp();
    }
}
