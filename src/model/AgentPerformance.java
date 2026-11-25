package model;

/**
 * AgentPerformance (Model Layer)
 * -------------------------------------------------------------------------
 * Holds aggregated performance metrics for a specific Valorant Agent
 * (e.g., Jett, Reyna, Omen, Sova, Killjoy) across multiple matches.
 */
public class AgentPerformance {

    private final String agentName;
    private int matchesPlayed;
    private int matchesWon;
    private int totalKills;
    private int totalDeaths;
    private int totalAssists;
    private int totalAcs;
    private double totalHeadshotPct;

    public AgentPerformance(String agentName) {
        this.agentName = agentName;
    }

    public void addMatch(MatchStats match) {
        matchesPlayed++;
        if (match.isMatchWon()) {
            matchesWon++;
        }
        totalKills += match.getKills();
        totalDeaths += match.getDeaths();
        totalAssists += match.getAssists();
        totalAcs += match.getAverageCombatScore();
        totalHeadshotPct += match.getHeadshotPercentage();
    }

    public String getAgentName() {
        return agentName;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    public int getMatchesWon() {
        return matchesWon;
    }

    public double getWinRate() {
        if (matchesPlayed == 0) return 0.0;
        return (matchesWon * 100.0) / matchesPlayed;
    }

    public double getAverageKd() {
        if (totalDeaths == 0) return totalKills;
        return (double) totalKills / totalDeaths;
    }

    public double getAverageAcs() {
        if (matchesPlayed == 0) return 0.0;
        return (double) totalAcs / matchesPlayed;
    }

    public double getAverageHeadshotPct() {
        if (matchesPlayed == 0) return 0.0;
        return totalHeadshotPct / matchesPlayed;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public int getTotalAssists() {
        return totalAssists;
    }
}
