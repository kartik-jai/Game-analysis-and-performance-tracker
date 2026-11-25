package service;

import model.AgentPerformance;
import model.MatchStats;
import model.PerformanceSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AnalyticsService (Service Layer)
 * -------------------------------------------------------------------------
 * Business logic engine that processes a list of Valorant MatchStats to:
 *   1. Calculate Match Win Rate % and Round Win Rate %
 *   2. Calculate Overall K/D, KDA, and Average Combat Score (ACS)
 *   3. Compute Weapon / Shot Accuracy breakdown (Headshot %, Bodyshot %, Legshot %)
 *   4. Aggregate Agent-wise performance stats (Jett, Omen, Reyna, Sova, etc.)
 *   5. Generate personalized Valorant Coaching Insights based on recent trends
 */
public class AnalyticsService {

    /**
     * Computes a complete PerformanceSummary across all provided matches.
     */
    public PerformanceSummary analyzeMatches(List<MatchStats> matches) {
        if (matches == null || matches.isEmpty()) {
            List<String> emptyTips = new ArrayList<>();
            emptyTips.add("No matches loaded yet. Click 'Load Local Log / Demo Data' or 'Fetch Live API' to start analyzing!");
            return new PerformanceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "N/A", "N/A", "N/A", "Unranked", emptyTips);
        }

        int totalMatches = matches.size();
        int matchesWon = 0;
        int totalRoundsWon = 0;
        int totalRoundsLost = 0;
        int totalKills = 0;
        int totalDeaths = 0;
        int totalAssists = 0;
        int totalAcs = 0;
        int totalHeadshots = 0;
        int totalBodyshots = 0;
        int totalLegshots = 0;

        Map<String, Integer> mapWins = new HashMap<>();
        Map<String, Integer> mapPlayed = new HashMap<>();
        Map<String, Integer> weaponCounts = new HashMap<>();

        for (MatchStats m : matches) {
            if (m.isMatchWon()) {
                matchesWon++;
                mapWins.put(m.getMapName(), mapWins.getOrDefault(m.getMapName(), 0) + 1);
            }
            mapPlayed.put(m.getMapName(), mapPlayed.getOrDefault(m.getMapName(), 0) + 1);
            weaponCounts.put(m.getPrimaryWeapon(), weaponCounts.getOrDefault(m.getPrimaryWeapon(), 0) + 1);

            totalRoundsWon += m.getRoundsWon();
            totalRoundsLost += m.getRoundsLost();
            totalKills += m.getKills();
            totalDeaths += m.getDeaths();
            totalAssists += m.getAssists();
            totalAcs += m.getAverageCombatScore();
            totalHeadshots += m.getHeadshots();
            totalBodyshots += m.getBodyshots();
            totalLegshots += m.getLegshots();
        }

        double matchWinRate = (matchesWon * 100.0) / totalMatches;
        int totalRounds = totalRoundsWon + totalRoundsLost;
        double roundWinRate = totalRounds > 0 ? (totalRoundsWon * 100.0) / totalRounds : 0.0;
        double overallKd = totalDeaths > 0 ? (double) totalKills / totalDeaths : totalKills;
        double overallKda = totalDeaths > 0 ? (double) (totalKills + totalAssists) / totalDeaths : (totalKills + totalAssists);
        double avgAcs = (double) totalAcs / totalMatches;

        int totalShots = totalHeadshots + totalBodyshots + totalLegshots;
        double hsPct = totalShots > 0 ? (totalHeadshots * 100.0) / totalShots : 0.0;
        double bsPct = totalShots > 0 ? (totalBodyshots * 100.0) / totalShots : 0.0;
        double lsPct = totalShots > 0 ? (totalLegshots * 100.0) / totalShots : 0.0;

        // Determine Best Agent
        List<AgentPerformance> agentStats = calculateAgentPerformance(matches);
        String bestAgent = "N/A";
        if (!agentStats.isEmpty()) {
            AgentPerformance top = agentStats.get(0);
            bestAgent = String.format("%s (%.0f%% WR, %.2f K/D)",
                    top.getAgentName(), top.getWinRate(), top.getAverageKd());
        }

        // Determine Best Map
        String bestMap = "N/A";
        double bestMapWr = -1.0;
        for (Map.Entry<String, Integer> entry : mapPlayed.entrySet()) {
            String map = entry.getKey();
            int played = entry.getValue();
            int won = mapWins.getOrDefault(map, 0);
            double wr = (won * 100.0) / played;
            if (wr > bestMapWr) {
                bestMapWr = wr;
                bestMap = String.format("%s (%.0f%% WR)", map, wr);
            }
        }

        // Determine Favorite Weapon
        String favoriteWeapon = "Vandal";
        int maxWeaponCount = -1;
        for (Map.Entry<String, Integer> entry : weaponCounts.entrySet()) {
            if (entry.getValue() > maxWeaponCount) {
                maxWeaponCount = entry.getValue();
                favoriteWeapon = entry.getKey();
            }
        }

        String rankEstimate = estimatePerformanceTier(overallKd, avgAcs, hsPct, roundWinRate);
        List<String> insights = buildCoachingInsights(matches, matchWinRate, roundWinRate, overallKd, avgAcs, hsPct, bestAgent, bestMap);

        return new PerformanceSummary(
                totalMatches, matchesWon, matchWinRate, roundWinRate,
                overallKd, overallKda, avgAcs,
                hsPct, bsPct, lsPct,
                bestAgent, bestMap, favoriteWeapon, rankEstimate, insights
        );
    }

    /**
     * Aggregates statistics per Valorant Agent sorted by Average ACS and Win Rate.
     */
    public List<AgentPerformance> calculateAgentPerformance(List<MatchStats> matches) {
        Map<String, AgentPerformance> agentMap = new LinkedHashMap<>();
        if (matches != null) {
            for (MatchStats m : matches) {
                String agent = m.getAgentName();
                AgentPerformance perf = agentMap.computeIfAbsent(agent, AgentPerformance::new);
                perf.addMatch(m);
            }
        }

        List<AgentPerformance> list = new ArrayList<>(agentMap.values());
        list.sort(Comparator.comparingDouble(AgentPerformance::getAverageAcs).reversed());
        return list;
    }

    /**
     * Estimates performance rating tier based on Valorant competitive benchmarks.
     */
    private String estimatePerformanceTier(double kd, double acs, double hsPct, double roundWinRate) {
        double score = (kd * 35) + (acs * 0.18) + (hsPct * 1.1) + (roundWinRate * 0.5);
        if (score >= 145) return "Immortal / Radiant Tier Form";
        if (score >= 128) return "Ascendant Tier Form";
        if (score >= 112) return "Diamond Tier Form";
        if (score >= 98)  return "Platinum Tier Form";
        if (score >= 82)  return "Gold Tier Form";
        return "Silver / Developing Form";
    }

    /**
     * Generates actionable coaching recommendations from calculated trends.
     */
    private List<String> buildCoachingInsights(List<MatchStats> matches, double matchWr, double roundWr,
                                               double kd, double acs, double hsPct,
                                               String bestAgent, String bestMap) {
        List<String> tips = new ArrayList<>();

        // 1. Crosshair Placement & Headshot Accuracy analysis
        if (hsPct >= 30.0) {
            tips.add(String.format("Elite Crosshair Placement: Your Headshot Accuracy is %.1f%% (Radiant/Immortal benchmark is 28%%+). Keep taking first-bullet Vandal taps.", hsPct));
        } else if (hsPct >= 22.0) {
            tips.add(String.format("Solid Aim Mechanics: Your Headshot rate is %.1f%% (above the 21%% competitive average). Focus on pre-aiming common peek angles.", hsPct));
        } else {
            tips.add(String.format("Aim Improvement Target: Headshot accuracy is %.1f%%. Practice counter-strafing and keeping crosshair at head level before firing.", hsPct));
        }

        // 2. K/D & Combat Impact (ACS)
        if (kd >= 1.25 && acs >= 250) {
            tips.add(String.format("High Fragging Impact: %.2f K/D with %.0f Average Combat Score (ACS) shows strong opening duel conversion.", kd, acs));
        } else if (kd >= 1.0) {
            tips.add(String.format("Consistent Trade Efficiency: %.2f K/D and %.0f ACS. Look for mid-round utility assists to push ACS past 240.", kd, acs));
        } else {
            tips.add(String.format("Survivability Focus: K/D is %.2f. Avoid solo over-peeking after getting first blood; play off teammate utility trades.", kd));
        }

        // 3. Round Win Rate & Clutch Conversion
        if (roundWr >= 53.0) {
            tips.add(String.format("Strong Round Economy: %.1f%% Round Win Rate (%.1f%% Match Win Rate) indicates effective bonus-round and post-plant conversion.", roundWr, matchWr));
        } else {
            tips.add(String.format("Round Conversion Alert: %.1f%% Round Win Rate. Coordinate team save rounds (eco) so you have full shields + utility on gun rounds.", roundWr));
        }

        // 4. Recent Form Trend (comparing last 3 matches vs earlier matches)
        if (matches.size() >= 4) {
            int n = matches.size();
            double recentKdSum = 0;
            for (int i = n - 3; i < n; i++) {
                recentKdSum += matches.get(i).getKdRatio();
            }
            double recentAvgKd = recentKdSum / 3.0;
            if (recentAvgKd > kd + 0.10) {
                tips.add(String.format("Upward Momentum: Your last 3 matches average a %.2f K/D (up from %.2f overall). Great time to queue Competitive!", recentAvgKd, kd));
            } else if (recentAvgKd < kd - 0.15) {
                tips.add(String.format("Fatigue Detected: Last 3 matches averaged %.2f K/D (below your %.2f average). Consider a 10-minute Deathmatch warmup.", recentAvgKd, kd));
            }
        }

        tips.add(String.format("Top Performing Setup: Best Agent is %s | Highest Win-Rate Map is %s.", bestAgent, bestMap));
        return tips;
    }
}
