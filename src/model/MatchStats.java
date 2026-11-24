package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MatchStats (Model Layer)
 * -------------------------------------------------------------------------
 * Represents the statistics of a single Valorant match.
 * Demonstrates OOP concepts: Encapsulation (private fields + getters/setters),
 * Constructors, and computed domain metrics (K/D ratio, Headshot %, Round Win Rate).
 */
public class MatchStats {

    private String matchId;
    private String playerName;     // e.g., "TenZ"
    private String playerTag;      // e.g., "SEN"
    private String region;         // e.g., "ap", "na", "eu"
    private String mapName;        // e.g., "Ascent", "Bind", "Haven", "Lotus"
    private String agentName;      // e.g., "Jett", "Omen", "Reyna", "Sova"
    private String gameMode;       // e.g., "Competitive", "Unrated"
    private String primaryWeapon;  // e.g., "Vandal", "Phantom", "Operator"

    private int kills;
    private int deaths;
    private int assists;
    private int roundsWon;
    private int roundsLost;
    private int headshots;
    private int bodyshots;
    private int legshots;
    private int averageCombatScore; // ACS (Valorant's primary score metric)
    private String matchDate;       // ISO or formatted date-time string

    public MatchStats() {
        this.matchDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public MatchStats(String matchId, String playerName, String playerTag, String region,
                      String mapName, String agentName, String gameMode, String primaryWeapon,
                      int kills, int deaths, int assists,
                      int roundsWon, int roundsLost,
                      int headshots, int bodyshots, int legshots,
                      int averageCombatScore, String matchDate) {
        this.matchId = matchId;
        this.playerName = playerName;
        this.playerTag = playerTag;
        this.region = region;
        this.mapName = mapName;
        this.agentName = agentName;
        this.gameMode = gameMode;
        this.primaryWeapon = primaryWeapon;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.roundsWon = roundsWon;
        this.roundsLost = roundsLost;
        this.headshots = headshots;
        this.bodyshots = bodyshots;
        this.legshots = legshots;
        this.averageCombatScore = averageCombatScore;
        this.matchDate = matchDate;
    }

    // ==================== COMPUTED ANALYTICS METRICS ====================

    /**
     * Calculates Kill/Death (K/D) Ratio.
     * Avoids division by zero if deaths == 0.
     */
    public double getKdRatio() {
        if (deaths <= 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    /**
     * Calculates (Kills + Assists) / Deaths (KDA Ratio).
     */
    public double getKdaRatio() {
        if (deaths <= 0) {
            return kills + assists;
        }
        return (double) (kills + assists) / deaths;
    }

    /**
     * Calculates Headshot Accuracy Percentage:
     * HS% = (Headshots / Total Shots Hit) * 100
     */
    public double getHeadshotPercentage() {
        int totalHits = headshots + bodyshots + legshots;
        if (totalHits <= 0) {
            return 0.0;
        }
        return (headshots * 100.0) / totalHits;
    }

    /**
     * Calculates Bodyshot Percentage.
     */
    public double getBodyshotPercentage() {
        int totalHits = headshots + bodyshots + legshots;
        if (totalHits <= 0) {
            return 0.0;
        }
        return (bodyshots * 100.0) / totalHits;
    }

    /**
     * Calculates Legshot Percentage.
     */
    public double getLegshotPercentage() {
        int totalHits = headshots + bodyshots + legshots;
        if (totalHits <= 0) {
            return 0.0;
        }
        return (legshots * 100.0) / totalHits;
    }

    /**
     * Calculates Round Win Rate Percentage for this match.
     */
    public double getRoundWinRate() {
        int totalRounds = roundsWon + roundsLost;
        if (totalRounds <= 0) {
            return 0.0;
        }
        return (roundsWon * 100.0) / totalRounds;
    }

    /**
     * Returns true if the player's team won the match (more rounds won than lost).
     */
    public boolean isMatchWon() {
        return roundsWon > roundsLost;
    }

    public String getMatchResultText() {
        if (roundsWon > roundsLost) return "VICTORY (" + roundsWon + "-" + roundsLost + ")";
        if (roundsWon < roundsLost) return "DEFEAT (" + roundsWon + "-" + roundsLost + ")";
        return "DRAW (" + roundsWon + "-" + roundsLost + ")";
    }

    // ==================== GETTERS & SETTERS ====================

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getPlayerTag() { return playerTag; }
    public void setPlayerTag(String playerTag) { this.playerTag = playerTag; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getPrimaryWeapon() { return primaryWeapon; }
    public void setPrimaryWeapon(String primaryWeapon) { this.primaryWeapon = primaryWeapon; }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }

    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }

    public int getAssists() { return assists; }
    public void setAssists(int assists) { this.assists = assists; }

    public int getRoundsWon() { return roundsWon; }
    public void setRoundsWon(int roundsWon) { this.roundsWon = roundsWon; }

    public int getRoundsLost() { return roundsLost; }
    public void setRoundsLost(int roundsLost) { this.roundsLost = roundsLost; }

    public int getHeadshots() { return headshots; }
    public void setHeadshots(int headshots) { this.headshots = headshots; }

    public int getBodyshots() { return bodyshots; }
    public void setBodyshots(int bodyshots) { this.bodyshots = bodyshots; }

    public int getLegshots() { return legshots; }
    public void setLegshots(int legshots) { this.legshots = legshots; }

    public int getAverageCombatScore() { return averageCombatScore; }
    public void setAverageCombatScore(int averageCombatScore) { this.averageCombatScore = averageCombatScore; }

    public String getMatchDate() { return matchDate; }
    public void setMatchDate(String matchDate) { this.matchDate = matchDate; }

    @Override
    public String toString() {
        return String.format("[%s] %s on %s (%s) | K/D/A: %d/%d/%d (%.2f) | HS: %.1f%% | ACS: %d | %s",
                matchDate, agentName, mapName, primaryWeapon,
                kills, deaths, assists, getKdRatio(),
                getHeadshotPercentage(), averageCombatScore, getMatchResultText());
    }
}
