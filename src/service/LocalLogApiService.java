package service;

import model.MatchStats;
import utility.ApiException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LocalLogApiService (Service Layer)
 * -------------------------------------------------------------------------
 * Second implementation of the GameApiClient interface.
 * Pulls Valorant match statistics from local log files (e.g., data/sample_valorant_logs.txt)
 * and can also simulate new competitive matches for any custom Riot ID.
 *
 * Educational Highlight for 3rd-Year B.Tech CS:
 * - Demonstrates Polymorphism: implements GameApiClient just like HenrikValorantApiService.
 * - Demonstrates Java File I/O (java.nio.file.Files, BufferedReader) and exception handling.
 * - Guarantees that you can demonstrate the full app in college lab viva even without Wi-Fi!
 */
public class LocalLogApiService implements GameApiClient {

    private String logFilePath;
    private static final String[] MAPS = {"Ascent", "Bind", "Haven", "Split", "Lotus", "Sunset", "Icebox", "Pearl", "Abyss"};
    private static final String[] AGENTS = {"Jett", "Reyna", "Omen", "Sova", "Raze", "Killjoy", "Clove", "Cypher"};
    private static final String[] WEAPONS = {"Vandal", "Phantom", "Operator", "Vandal", "Phantom"};

    public LocalLogApiService(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    @Override
    public String getProviderName() {
        return "Local Valorant Log File Parser (" + logFilePath + ")";
    }

    @Override
    public List<MatchStats> fetchRecentMatches(String playerName, String playerTag, String region) throws ApiException {
        Path path = Paths.get(logFilePath);
        if (!Files.exists(path)) {
            throw new ApiException(
                    "Local log file not found at: " + path.toAbsolutePath(),
                    404,
                    "Verify that 'data/sample_valorant_logs.txt' exists or browse to a valid log file."
            );
        }

        List<MatchStats> matches = new ArrayList<>();
        String targetName = (playerName == null || playerName.trim().isEmpty()) ? "TenZ" : playerName.trim();
        String targetTag = (playerTag == null || playerTag.trim().isEmpty()) ? "SEN" : playerTag.trim();
        String targetRegion = (region == null || region.trim().isEmpty()) ? "ap" : region.trim();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Format: MATCH_ID|PLAYER|TAG|REGION|MAP|AGENT|MODE|WEAPON|K|D|A|RW|RL|HS|BS|LS|ACS|DATE
                String[] parts = line.split("\\|");
                if (parts.length >= 18) {
                    matches.add(new MatchStats(
                            parts[0].trim(),
                            targetName,
                            targetTag,
                            targetRegion,
                            parts[4].trim(),
                            parts[5].trim(),
                            parts[6].trim(),
                            parts[7].trim(),
                            Integer.parseInt(parts[8].trim()),
                            Integer.parseInt(parts[9].trim()),
                            Integer.parseInt(parts[10].trim()),
                            Integer.parseInt(parts[11].trim()),
                            Integer.parseInt(parts[12].trim()),
                            Integer.parseInt(parts[13].trim()),
                            Integer.parseInt(parts[14].trim()),
                            Integer.parseInt(parts[15].trim()),
                            Integer.parseInt(parts[16].trim()),
                            parts[17].trim()
                    ));
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new ApiException(
                    "Failed to parse local Valorant log file: " + e.getMessage(),
                    e,
                    500,
                    "Check that the log file follows the pipe-delimited format."
            );
        }

        if (matches.isEmpty()) {
            throw new ApiException(
                    "The log file was empty or contained no valid match lines.",
                    204,
                    "Add match entries to " + logFilePath + " or click 'Simulate New Match'."
            );
        }

        return matches;
    }

    /**
     * Generates a realistic new Valorant match entry for testing live trend updates.
     */
    public MatchStats generateSimulatedMatch(String playerName, String playerTag, String region, int matchCounter) {
        Random rand = new Random();
        String map = MAPS[rand.nextInt(MAPS.length)];
        String agent = AGENTS[rand.nextInt(AGENTS.length)];
        String weapon = WEAPONS[rand.nextInt(WEAPONS.length)];

        boolean win = rand.nextDouble() < 0.62;
        int roundsWon = win ? 13 : (7 + rand.nextInt(5));
        int roundsLost = win ? (5 + rand.nextInt(7)) : 13;

        int kills = 14 + rand.nextInt(17);
        int deaths = 10 + rand.nextInt(10);
        int assists = 3 + rand.nextInt(10);

        int headshots = 15 + rand.nextInt(22);
        int bodyshots = 28 + rand.nextInt(20);
        int legshots = 2 + rand.nextInt(7);
        int acs = 185 + (kills * 6) + rand.nextInt(35);

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String matchId = "VAL-SIM-" + (System.currentTimeMillis() % 100000) + "-" + matchCounter;

        return new MatchStats(
                matchId,
                playerName.isEmpty() ? "TenZ" : playerName,
                playerTag.isEmpty() ? "SEN" : playerTag,
                region.isEmpty() ? "ap" : region,
                map,
                agent,
                "Competitive",
                weapon,
                kills,
                deaths,
                assists,
                roundsWon,
                roundsLost,
                headshots,
                bodyshots,
                legshots,
                acs,
                dateStr
        );
    }

    public File getLogFile() {
        return new File(logFilePath);
    }
}
