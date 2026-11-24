package utility;

import model.MatchStats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DatabaseManager (Utility / Persistence Layer)
 * -------------------------------------------------------------------------
 * Manages persistent storage of Valorant match history using SQL (JDBC + SQLite).
 *
 * Educational Highlights for 3rd-Year B.Tech CS:
 * 1. Uses standard JDBC classes: Connection, Statement, PreparedStatement, ResultSet.
 * 2. Prevents SQL Injection by using parameterized PreparedStatements (?).
 * 3. Includes automatic fallback persistence (data/matches_sql_backup.csv) if the
 *    external SQLite JDBC driver JAR is not attached in the IDE classpath, ensuring
 *    the project runs 100% reliably out-of-the-box in any college computer lab.
 */
public class DatabaseManager {

    private final String dbUrl;
    private boolean jdbcAvailable = false;
    private String storageStatusMessage = "Initializing storage...";
    private static final String BACKUP_TABLE_FILE = "data/matches_sql_table.csv";

    // SQL DDL Query to create the matches table
    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS valorant_matches (" +
            "    match_id TEXT PRIMARY KEY, " +
            "    player_name TEXT NOT NULL, " +
            "    player_tag TEXT NOT NULL, " +
            "    region TEXT NOT NULL, " +
            "    map_name TEXT NOT NULL, " +
            "    agent_name TEXT NOT NULL, " +
            "    game_mode TEXT NOT NULL, " +
            "    primary_weapon TEXT NOT NULL, " +
            "    kills INTEGER NOT NULL, " +
            "    deaths INTEGER NOT NULL, " +
            "    assists INTEGER NOT NULL, " +
            "    rounds_won INTEGER NOT NULL, " +
            "    rounds_lost INTEGER NOT NULL, " +
            "    headshots INTEGER NOT NULL, " +
            "    bodyshots INTEGER NOT NULL, " +
            "    legshots INTEGER NOT NULL, " +
            "    average_combat_score INTEGER NOT NULL, " +
            "    match_date TEXT NOT NULL" +
            ");";

    // SQL DML Query to insert or replace a match record
    private static final String INSERT_MATCH_SQL =
            "INSERT OR REPLACE INTO valorant_matches (" +
            "    match_id, player_name, player_tag, region, map_name, agent_name, " +
            "    game_mode, primary_weapon, kills, deaths, assists, rounds_won, rounds_lost, " +
            "    headshots, bodyshots, legshots, average_combat_score, match_date" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

    // SQL Query to retrieve all matches ordered chronologically
    private static final String SELECT_ALL_SQL =
            "SELECT * FROM valorant_matches ORDER BY match_date ASC;";

    // SQL Query to filter matches by Player Name & Tag
    private static final String SELECT_BY_PLAYER_SQL =
            "SELECT * FROM valorant_matches WHERE LOWER(player_name) = LOWER(?) AND LOWER(player_tag) = LOWER(?) ORDER BY match_date ASC;";

    // SQL Query to delete all stored matches
    private static final String DELETE_ALL_SQL =
            "DELETE FROM valorant_matches;";

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    /**
     * Initializes the SQLite database table via JDBC, or falls back gracefully
     * if sqlite-jdbc.jar is not present on the runtime classpath.
     */
    public final void initializeDatabase() {
        try {
            // Attempt to load the SQLite JDBC driver class
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_TABLE_SQL);
                jdbcAvailable = true;
                storageStatusMessage = "SQL Database Connected (" + dbUrl + ")";
            }
        } catch (ClassNotFoundException | SQLException e) {
            jdbcAvailable = false;
            storageStatusMessage = "SQL Engine Fallback Active (data/matches_sql_table.csv) — add sqlite-jdbc.jar for native SQLite";
            ensureBackupFileExists();
        }
    }

    /**
     * Saves a list of MatchStats into the SQL database using a batch transaction.
     */
    public int saveMatches(List<MatchStats> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0;
        }

        if (jdbcAvailable) {
            int savedCount = 0;
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(INSERT_MATCH_SQL)) {

                conn.setAutoCommit(false); // Start SQL transaction for better performance
                for (MatchStats m : matches) {
                    pstmt.setString(1, m.getMatchId());
                    pstmt.setString(2, m.getPlayerName());
                    pstmt.setString(3, m.getPlayerTag());
                    pstmt.setString(4, m.getRegion());
                    pstmt.setString(5, m.getMapName());
                    pstmt.setString(6, m.getAgentName());
                    pstmt.setString(7, m.getGameMode());
                    pstmt.setString(8, m.getPrimaryWeapon());
                    pstmt.setInt(9, m.getKills());
                    pstmt.setInt(10, m.getDeaths());
                    pstmt.setInt(11, m.getAssists());
                    pstmt.setInt(12, m.getRoundsWon());
                    pstmt.setInt(13, m.getRoundsLost());
                    pstmt.setInt(14, m.getHeadshots());
                    pstmt.setInt(15, m.getBodyshots());
                    pstmt.setInt(16, m.getLegshots());
                    pstmt.setInt(17, m.getAverageCombatScore());
                    pstmt.setString(18, m.getMatchDate());
                    pstmt.addBatch();
                    savedCount++;
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] SQL Insert Error: " + e.getMessage());
            }
            // Also keep the CSV backup synced for easy inspection
            saveToBackupTable(matches);
            return savedCount;
        } else {
            return saveToBackupTable(matches);
        }
    }

    /**
     * Loads all stored matches from the SQL database.
     */
    public List<MatchStats> getAllMatches() {
        if (jdbcAvailable) {
            List<MatchStats> list = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL_SQL);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    list.add(mapResultSetToMatch(rs));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] SQL Select Error: " + e.getMessage());
            }
        }
        return loadFromBackupTable();
    }

    /**
     * Queries matches for a specific player (Riot ID Name + Tag) using SQL WHERE clause.
     */
    public List<MatchStats> getMatchesByPlayer(String playerName, String playerTag) {
        if (jdbcAvailable) {
            List<MatchStats> list = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_PLAYER_SQL)) {

                pstmt.setString(1, playerName);
                pstmt.setString(2, playerTag);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSetToMatch(rs));
                    }
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] SQL Filter Error: " + e.getMessage());
            }
        }

        List<MatchStats> filtered = new ArrayList<>();
        for (MatchStats m : loadFromBackupTable()) {
            if (m.getPlayerName().equalsIgnoreCase(playerName) &&
                m.getPlayerTag().equalsIgnoreCase(playerTag)) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    /**
     * Clears all stored match history from the SQL table.
     */
    public void clearAllMatches() {
        if (jdbcAvailable) {
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(DELETE_ALL_SQL);
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] SQL Delete Error: " + e.getMessage());
            }
        }
        try {
            Path path = Paths.get(BACKUP_TABLE_FILE);
            if (Files.exists(path)) {
                Files.delete(path);
            }
            ensureBackupFileExists();
        } catch (IOException e) {
            System.err.println("[DatabaseManager] Failed to clear backup table: " + e.getMessage());
        }
    }

    public String getStorageStatusMessage() {
        return storageStatusMessage;
    }

    public boolean isJdbcAvailable() {
        return jdbcAvailable;
    }

    private MatchStats mapResultSetToMatch(ResultSet rs) throws SQLException {
        return new MatchStats(
                rs.getString("match_id"),
                rs.getString("player_name"),
                rs.getString("player_tag"),
                rs.getString("region"),
                rs.getString("map_name"),
                rs.getString("agent_name"),
                rs.getString("game_mode"),
                rs.getString("primary_weapon"),
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("assists"),
                rs.getInt("rounds_won"),
                rs.getInt("rounds_lost"),
                rs.getInt("headshots"),
                rs.getInt("bodyshots"),
                rs.getInt("legshots"),
                rs.getInt("average_combat_score"),
                rs.getString("match_date")
        );
    }

    // ==================== FALLBACK TABLE STORAGE (ZERO-JAR COMPATIBILITY) ====================

    private void ensureBackupFileExists() {
        try {
            Path path = Paths.get(BACKUP_TABLE_FILE);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (!Files.exists(path)) {
                try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    writer.write("match_id,player_name,player_tag,region,map_name,agent_name,game_mode,primary_weapon,kills,deaths,assists,rounds_won,rounds_lost,headshots,bodyshots,legshots,average_combat_score,match_date");
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("[DatabaseManager] Could not initialize backup table file: " + e.getMessage());
        }
    }

    private int saveToBackupTable(List<MatchStats> newMatches) {
        ensureBackupFileExists();
        Map<String, MatchStats> map = new LinkedHashMap<>();
        for (MatchStats existing : loadFromBackupTable()) {
            map.put(existing.getMatchId(), existing);
        }
        for (MatchStats m : newMatches) {
            map.put(m.getMatchId(), m);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(BACKUP_TABLE_FILE), StandardCharsets.UTF_8)) {
            writer.write("match_id,player_name,player_tag,region,map_name,agent_name,game_mode,primary_weapon,kills,deaths,assists,rounds_won,rounds_lost,headshots,bodyshots,legshots,average_combat_score,match_date");
            writer.newLine();
            for (MatchStats m : map.values()) {
                writer.write(String.join(",",
                        sanitize(m.getMatchId()),
                        sanitize(m.getPlayerName()),
                        sanitize(m.getPlayerTag()),
                        sanitize(m.getRegion()),
                        sanitize(m.getMapName()),
                        sanitize(m.getAgentName()),
                        sanitize(m.getGameMode()),
                        sanitize(m.getPrimaryWeapon()),
                        String.valueOf(m.getKills()),
                        String.valueOf(m.getDeaths()),
                        String.valueOf(m.getAssists()),
                        String.valueOf(m.getRoundsWon()),
                        String.valueOf(m.getRoundsLost()),
                        String.valueOf(m.getHeadshots()),
                        String.valueOf(m.getBodyshots()),
                        String.valueOf(m.getLegshots()),
                        String.valueOf(m.getAverageCombatScore()),
                        sanitize(m.getMatchDate())
                ));
                writer.newLine();
            }
            return newMatches.size();
        } catch (IOException e) {
            System.err.println("[DatabaseManager] Error writing backup table: " + e.getMessage());
            return 0;
        }
    }

    private List<MatchStats> loadFromBackupTable() {
        ensureBackupFileExists();
        List<MatchStats> matches = new ArrayList<>();
        Path path = Paths.get(BACKUP_TABLE_FILE);
        if (!Files.exists(path)) return matches;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 18) {
                    matches.add(new MatchStats(
                            parts[0].trim(),
                            parts[1].trim(),
                            parts[2].trim(),
                            parts[3].trim(),
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
            System.err.println("[DatabaseManager] Error loading backup table: " + e.getMessage());
        }
        return matches;
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace(",", " ").trim();
    }
}
