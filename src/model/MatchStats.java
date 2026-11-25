package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MatchStats (Model Layer)
 * -------------------------------------------------------------------------
 * Represents the complete statistics of a Valorant match, including:
 *   - Core combat stats (Kills, Deaths, Assists, Headshots, Bodyshots, Legshots, ACS)
 *   - Real Damage Dealt (damageMade) and Damage Received (damageReceived) for ADR & DD-Delta
 *   - Episode/Act season code (e.g. "e11a6"), Server cluster ("Mumbai"), and Match Rank Tier
 *   - Scoreboard placement ("★ MATCH MVP", "TEAM MVP", "2nd Place".."10th Place")
 *   - Per-match Weapon Kill & Hitbox events and 10-player Post-Match Scoreboard entries
 */
public class MatchStats {

    /**
     * Represents a single weapon's performance inside a match or aggregated across an Act.
     */
    public static class WeaponEvent {
        public String name;
        public String iconUrl;
        public int kills;
        public int hipFireKills;
        public int altFireKills;
        public int headshots;
        public int bodyshots;
        public int legshots;
        public int matchesUsed;
        public int bestMatchKills;
        public Map<String, Integer> mapKills = new LinkedHashMap<>();

        public WeaponEvent(String name, String iconUrl) {
            this.name = name;
            this.iconUrl = iconUrl;
        }

        public WeaponEvent copy() {
            WeaponEvent c = new WeaponEvent(this.name, this.iconUrl);
            c.kills = this.kills;
            c.hipFireKills = this.hipFireKills;
            c.altFireKills = this.altFireKills;
            c.headshots = this.headshots;
            c.bodyshots = this.bodyshots;
            c.legshots = this.legshots;
            c.matchesUsed = this.matchesUsed;
            c.bestMatchKills = this.bestMatchKills;
            c.mapKills.putAll(this.mapKills);
            return c;
        }
    }

    /**
     * Represents one of the 10 players in a match's expandable post-match scoreboard.
     */
    public static class ScoreboardPlayer {
        public int rankPos;
        public boolean isMe;
        public String puuid = "";
        public String name = "Player";
        public String tag = "";
        public String team = "Blue";
        public String character = "Agent";
        public String agentIconUrl = "";
        public String tierPatched = "Unrated";
        public int score;
        public int kills;
        public int deaths;
        public int assists;
        public int headshots;
        public int bodyshots;
        public int legshots;
        public int damageMade;
        public int damageReceived;
        public int acs;
        public int adr;
        public double hsPct;
    }

    /**
     * Represents a player's Competitive Episode // Act record from mmrData.by_season.
     */
    public static class ActSeasonRecord {
        public String code;          // e.g. "e11a6"
        public String longName;      // e.g. "Episode 11 // Act 6"
        public String shortName;     // e.g. "EP11 : ACT 6"
        public int wins;
        public int losses;
        public int games;
        public double winRate;
        public int finalRankId;
        public String finalRankName; // e.g. "Silver 1"
        public int peakInActTier;
        public String peakInActName; // e.g. "Silver 2"
        public int triangleWinsCount;
        public String smallIconUrl;
        public String largeIconUrl;
    }

    /**
     * Holds live Account & MMR Profile metadata from /v1/account and /v2/mmr.
     */
    public static class PlayerProfileData {
        public String puuid = "";
        public String name = "Karthikkkk";
        public String tag = "1795";
        public String region = "ap";
        public int accountLevel = 0;
        public String smallCardUrl = "";
        public String wideCardUrl = "";
        public String lastUpdate = "Recently";

        public String currentRankName = "Unrated";
        public String currentRankRrText = "No active RR";
        public String currentRankIconUrl = "";
        public String peakRankName = "N/A";
        public String peakRankSeason = "";
        public List<ActSeasonRecord> actsList = new ArrayList<>();
    }

    private String matchId;
    private String seasonCode = "";
    private String playerName;
    private String playerTag;
    private String region;
    private String cluster = "";
    private String mapName;
    private String agentName;
    private String agentIconUrl = "";
    private String matchTierPatched = "Competitive";
    private String gameMode;
    private String primaryWeapon;
    private String topWeaponIconUrl = "";
    private int topWeaponKills = 0;

    private int kills;
    private int deaths;
    private int assists;
    private int roundsWon;
    private int roundsLost;
    private int roundsPlayed = 20;
    private int headshots;
    private int bodyshots;
    private int legshots;
    private int totalScore = 0;
    private int averageCombatScore;
    private int damageMade = 0;
    private int damageReceived = 0;
    private String matchDate;
    private String durationText = "";
    private boolean hasWon = false;
    private boolean deathmatch = false;
    private int placementNum = 0;
    private String placementLabel = "";

    private Map<String, WeaponEvent> weaponEvents = new LinkedHashMap<>();
    private List<ScoreboardPlayer> scoreboard = new ArrayList<>();

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
        this.roundsPlayed = Math.max(1, roundsWon + roundsLost);
        this.headshots = headshots;
        this.bodyshots = bodyshots;
        this.legshots = legshots;
        this.averageCombatScore = averageCombatScore;
        this.totalScore = averageCombatScore * this.roundsPlayed;
        this.damageMade = Math.round(averageCombatScore * 0.63f * this.roundsPlayed);
        this.damageReceived = Math.round(deaths * 145);
        this.matchDate = matchDate;
        this.hasWon = roundsWon > roundsLost;
    }

    public MatchStats copy() {
        MatchStats c = new MatchStats(
                matchId, playerName, playerTag, region,
                mapName, agentName, gameMode, primaryWeapon,
                kills, deaths, assists, roundsWon, roundsLost,
                headshots, bodyshots, legshots, averageCombatScore, matchDate
        );
        c.seasonCode = this.seasonCode;
        c.cluster = this.cluster;
        c.agentIconUrl = this.agentIconUrl;
        c.matchTierPatched = this.matchTierPatched;
        c.topWeaponIconUrl = this.topWeaponIconUrl;
        c.topWeaponKills = this.topWeaponKills;
        c.roundsPlayed = this.roundsPlayed;
        c.totalScore = this.totalScore;
        c.damageMade = this.damageMade;
        c.damageReceived = this.damageReceived;
        c.durationText = this.durationText;
        c.hasWon = this.hasWon;
        c.deathmatch = this.deathmatch;
        c.placementNum = this.placementNum;
        c.placementLabel = this.placementLabel;
        for (Map.Entry<String, WeaponEvent> e : this.weaponEvents.entrySet()) {
            c.weaponEvents.put(e.getKey(), e.getValue().copy());
        }
        c.scoreboard = new ArrayList<>(this.scoreboard);
        return c;
    }

    // ==================== COMPUTED ANALYTICS METRICS ====================

    public double getKdRatio() {
        if (deaths <= 0) return kills;
        return (double) kills / deaths;
    }

    public double getKdaRatio() {
        if (deaths <= 0) return kills + assists;
        return (double) (kills + assists) / deaths;
    }

    public double getHeadshotPercentage() {
        int totalHits = headshots + bodyshots + legshots;
        if (totalHits <= 0) return 0.0;
        return (headshots * 100.0) / totalHits;
    }

    public double getBodyshotPercentage() {
        int totalHits = headshots + bodyshots + legshots;
        if (totalHits <= 0) return 0.0;
        return (bodyshots * 100.0) / totalHits;
    }

    public double getLegshotPercentage() {
        int totalHits = headshots + bodyshots + legshots;
        if (totalHits <= 0) return 0.0;
        return (legshots * 100.0) / totalHits;
    }

    public double getRoundWinRate() {
        int totalRounds = roundsWon + roundsLost;
        if (totalRounds <= 0) return 0.0;
        return (roundsWon * 100.0) / totalRounds;
    }

    public double getAdr() {
        return (double) damageMade / Math.max(1, roundsPlayed);
    }

    public double getDdDeltaPerRound() {
        return (double) (damageMade - damageReceived) / Math.max(1, roundsPlayed);
    }

    public boolean isMatchWon() {
        return hasWon;
    }

    public String getMatchResultText() {
        if (isMatchWon()) return "VICTORY (" + roundsWon + "-" + roundsLost + ")";
        if (roundsWon < roundsLost) return "DEFEAT (" + roundsWon + "-" + roundsLost + ")";
        return "DRAW (" + roundsWon + "-" + roundsLost + ")";
    }

    // ==================== GETTERS & SETTERS ====================

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getSeasonCode() { return seasonCode; }
    public void setSeasonCode(String seasonCode) { this.seasonCode = seasonCode == null ? "" : seasonCode; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getPlayerTag() { return playerTag; }
    public void setPlayerTag(String playerTag) { this.playerTag = playerTag; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCluster() { return cluster; }
    public void setCluster(String cluster) { this.cluster = cluster == null ? "" : cluster; }

    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getAgentIconUrl() { return agentIconUrl; }
    public void setAgentIconUrl(String agentIconUrl) { this.agentIconUrl = agentIconUrl == null ? "" : agentIconUrl; }

    public String getMatchTierPatched() { return matchTierPatched; }
    public void setMatchTierPatched(String matchTierPatched) { this.matchTierPatched = matchTierPatched; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getPrimaryWeapon() { return primaryWeapon == null || primaryWeapon.isEmpty() ? "-" : primaryWeapon; }
    public void setPrimaryWeapon(String primaryWeapon) { this.primaryWeapon = primaryWeapon; }

    public String getTopWeaponIconUrl() { return topWeaponIconUrl; }
    public void setTopWeaponIconUrl(String topWeaponIconUrl) { this.topWeaponIconUrl = topWeaponIconUrl == null ? "" : topWeaponIconUrl; }

    public int getTopWeaponKills() { return topWeaponKills; }
    public void setTopWeaponKills(int topWeaponKills) { this.topWeaponKills = topWeaponKills; }

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

    public int getRoundsPlayed() { return Math.max(1, roundsPlayed); }
    public void setRoundsPlayed(int roundsPlayed) { this.roundsPlayed = Math.max(1, roundsPlayed); }

    public int getHeadshots() { return headshots; }
    public void setHeadshots(int headshots) { this.headshots = headshots; }

    public int getBodyshots() { return bodyshots; }
    public void setBodyshots(int bodyshots) { this.bodyshots = bodyshots; }

    public int getLegshots() { return legshots; }
    public void setLegshots(int legshots) { this.legshots = legshots; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public int getAverageCombatScore() { return averageCombatScore; }
    public void setAverageCombatScore(int averageCombatScore) { this.averageCombatScore = averageCombatScore; }

    public int getDamageMade() { return damageMade; }
    public void setDamageMade(int damageMade) { this.damageMade = damageMade; }

    public int getDamageReceived() { return damageReceived; }
    public void setDamageReceived(int damageReceived) { this.damageReceived = damageReceived; }

    public String getMatchDate() { return matchDate; }
    public void setMatchDate(String matchDate) { this.matchDate = matchDate; }

    public String getDurationText() { return durationText; }
    public void setDurationText(String durationText) { this.durationText = durationText == null ? "" : durationText; }

    public void setHasWon(boolean hasWon) { this.hasWon = hasWon; }

    public boolean isDeathmatch() { return deathmatch; }
    public void setDeathmatch(boolean deathmatch) { this.deathmatch = deathmatch; }

    public int getPlacementNum() { return placementNum; }
    public void setPlacementNum(int placementNum) { this.placementNum = placementNum; }

    public String getPlacementLabel() { return placementLabel == null ? "" : placementLabel; }
    public void setPlacementLabel(String placementLabel) { this.placementLabel = placementLabel; }

    public Map<String, WeaponEvent> getWeaponEvents() { return weaponEvents; }
    public void setWeaponEvents(Map<String, WeaponEvent> weaponEvents) { this.weaponEvents = weaponEvents; }

    public List<ScoreboardPlayer> getScoreboard() { return scoreboard; }
    public void setScoreboard(List<ScoreboardPlayer> scoreboard) { this.scoreboard = scoreboard; }

    @Override
    public String toString() {
        return String.format("[%s] %s on %s (%s) | K/D/A: %d/%d/%d (%.2f) | HS: %.1f%% | ACS: %d | %s",
                matchDate, agentName, mapName, getPrimaryWeapon(),
                kills, deaths, assists, getKdRatio(),
                getHeadshotPercentage(), averageCombatScore, getMatchResultText());
    }
}
