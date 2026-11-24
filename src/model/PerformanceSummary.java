package model;

import java.util.List;

/**
 * PerformanceSummary (Model Layer)
 * -------------------------------------------------------------------------
 * Encapsulates overall calculated KPIs (Key Performance Indicators) across
 * all tracked Valorant matches, along with automated coaching insights.
 */
public class PerformanceSummary {

    private final int totalMatches;
    private final int matchesWon;
    private final double matchWinRate;
    private final double roundWinRate;
    private final double overallKdRatio;
    private final double overallKdaRatio;
    private final double averageAcs;
    private final double averageHeadshotPct;
    private final double averageBodyshotPct;
    private final double averageLegshotPct;
    private final String bestAgent;
    private final String bestMap;
    private final String favoriteWeapon;
    private final String performanceRankEstimate;
    private final List<String> coachingInsights;

    public PerformanceSummary(int totalMatches, int matchesWon,
                              double matchWinRate, double roundWinRate,
                              double overallKdRatio, double overallKdaRatio,
                              double averageAcs,
                              double averageHeadshotPct, double averageBodyshotPct, double averageLegshotPct,
                              String bestAgent, String bestMap, String favoriteWeapon,
                              String performanceRankEstimate, List<String> coachingInsights) {
        this.totalMatches = totalMatches;
        this.matchesWon = matchesWon;
        this.matchWinRate = matchWinRate;
        this.roundWinRate = roundWinRate;
        this.overallKdRatio = overallKdRatio;
        this.overallKdaRatio = overallKdaRatio;
        this.averageAcs = averageAcs;
        this.averageHeadshotPct = averageHeadshotPct;
        this.averageBodyshotPct = averageBodyshotPct;
        this.averageLegshotPct = averageLegshotPct;
        this.bestAgent = bestAgent;
        this.bestMap = bestMap;
        this.favoriteWeapon = favoriteWeapon;
        this.performanceRankEstimate = performanceRankEstimate;
        this.coachingInsights = coachingInsights;
    }

    public int getTotalMatches() { return totalMatches; }
    public int getMatchesWon() { return matchesWon; }
    public double getMatchWinRate() { return matchWinRate; }
    public double getRoundWinRate() { return roundWinRate; }
    public double getOverallKdRatio() { return overallKdRatio; }
    public double getOverallKdaRatio() { return overallKdaRatio; }
    public double getAverageAcs() { return averageAcs; }
    public double getAverageHeadshotPct() { return averageHeadshotPct; }
    public double getAverageBodyshotPct() { return averageBodyshotPct; }
    public double getAverageLegshotPct() { return averageLegshotPct; }
    public String getBestAgent() { return bestAgent; }
    public String getBestMap() { return bestMap; }
    public String getFavoriteWeapon() { return favoriteWeapon; }
    public String getPerformanceRankEstimate() { return performanceRankEstimate; }
    public List<String> getCoachingInsights() { return coachingInsights; }
}
