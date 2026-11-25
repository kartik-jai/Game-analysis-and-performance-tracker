package service;

import model.MatchStats;
import model.MatchStats.ActSeasonRecord;
import model.MatchStats.PlayerProfileData;
import model.MatchStats.ScoreboardPlayer;
import model.MatchStats.WeaponEvent;
import utility.ApiException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HenrikValorantApiService (Service Layer)
 * -------------------------------------------------------------------------
 * Replicates the complete parallel backend pipeline of our Tracker.gg web app
 * using a brace-balanced JSON Object/Array parser so fields are never shifted
 * or truncated:
 * 1. GET /valorant/v1/account/{name}/{tag}
 * 2. GET /valorant/v2/mmr/{region}/{name}/{tag} (Rank, RR, ELO, and all
 * Episodes/Acts by_season)
 * 3. GET /valorant/v1/stored-matches/{region}/{name}/{tag}?page=1 & page=2
 * 4. GET /valorant/v3/matches/{region}/{name}/{tag}?size=10 (10-player
 * scoreboards + weapon kills + round damage)
 * 5. Deterministic Act-Specific Match & Hitbox generator for historical Acts
 */
public class HenrikValorantApiService implements GameApiClient {

    private static final String BASE_API = "https://api.henrikdev.xyz/valorant";
    private static final String TIER_UUID = "03621f52-342b-cf4e-4f86-9350a49c6d04";
    private static final String FALLBACK_KEY = "HDEV-fdabd296-a67c-473d-9f7a-efc1f183ae40";

    // Pre-compiled Pattern caches to avoid compiling thousands of regexes during
    // JSON parsing
    private static final Map<String, Pattern> OBJ_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Pattern> ARR_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Pattern> STR_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Pattern> INT_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Pattern> SINT_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final HttpClient httpClient;
    private String apiKey;

    public HenrikValorantApiService(String apiKey) {
        this.apiKey = (apiKey == null || apiKey.trim().isEmpty()) ? FALLBACK_KEY : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = (apiKey == null || apiKey.trim().isEmpty()) ? FALLBACK_KEY : apiKey.trim();
    }

    @Override
    public String getProviderName() {
        return "HenrikDev Live Valorant API (v1/v2/v3)";
    }

    /**
     * Always returns high-resolution competitive tier icon URLs so rank badges
     * never appear pixelated on High-DPI displays.
     */
    public static String getTierIconUrl(int tierId, boolean large) {
        int id = tierId >= 3 ? tierId : 0;
        return String.format("https://media.valorant-api.com/competitivetiers/%s/%d/largeicon.png",
                TIER_UUID, id);
    }

    public static String formatActCode(String code, boolean longFormat) {
        if (code == null || code.isEmpty())
            return "Unknown Act";
        Matcher m = Pattern.compile("^e(\\d+)a(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(code.trim());
        if (m.find()) {
            return longFormat
                    ? "Episode " + m.group(1) + " // Act " + m.group(2)
                    : "EP" + m.group(1) + " : ACT " + m.group(2);
        }
        return code.toUpperCase();
    }

    public static String getWeaponCategory(String wName) {
        if (wName == null)
            return "Weapons & Abilities";
        String n = wName.toLowerCase();
        if (n.equals("vandal") || n.equals("phantom") || n.equals("bulldog") || n.equals("guardian"))
            return "Assault Rifles";
        if (n.equals("spectre") || n.equals("stinger"))
            return "SMGs";
        if (n.equals("operator") || n.equals("marshal") || n.equals("outlaw"))
            return "Sniper Rifles";
        if (n.equals("classic") || n.equals("shorty") || n.equals("frenzy") || n.equals("ghost") || n.equals("sheriff"))
            return "Sidearms";
        if (n.equals("bucky") || n.equals("judge"))
            return "Shotguns";
        if (n.equals("ares") || n.equals("odin"))
            return "Heavy Weapons";
        if (n.contains("melee") || n.contains("knife"))
            return "Melee";
        return "Weapons & Abilities";
    }

    // ==================== PARALLEL FULL PROFILE FETCHER ====================

    public static class FullProfileResponse {
        public PlayerProfileData profile = new PlayerProfileData();
        public List<MatchStats> allMatches = new ArrayList<>();
    }

    /**
     * Fires Account, MMR (with all Acts), Stored Matches (page 1 & page 2),
     * and v3 Matches in parallel at t=0ms just like the Chrome frontend.
     */
    public FullProfileResponse fetchFullProfileParallel(String playerName, String playerTag, String region,
            String modeKey) throws ApiException {
        if (playerName == null || playerName.trim().isEmpty() || playerTag == null || playerTag.trim().isEmpty()) {
            throw new ApiException("Player Name and #Tag cannot be empty.", 400, "Enter a valid Riot ID and Tagline.");
        }

        final String cleanName = playerName.trim();
        final String cleanTag = playerTag.trim().replaceFirst("^#", "");
        final String initRegion = (region == null || region.trim().isEmpty()) ? "ap" : region.trim().toLowerCase();
        final String encName = encode(cleanName);
        final String encTag = encode(cleanTag);

        String accUrl = String.format("%s/v1/account/%s/%s", BASE_API, encName, encTag);
        String mmrUrl = String.format("%s/v2/mmr/%s/%s/%s", BASE_API, initRegion, encName, encTag);
        String storedUrl1 = String.format("%s/v1/stored-matches/%s/%s/%s?page=1", BASE_API, initRegion, encName,
                encTag);
        String storedUrl2 = String.format("%s/v1/stored-matches/%s/%s/%s?page=2", BASE_API, initRegion, encName,
                encTag);
        String modeParam = (modeKey == null || modeKey.isEmpty() || "ALL".equalsIgnoreCase(modeKey))
                ? ""
                : "&mode=" + encode(modeKey.toLowerCase());
        String v3Url = String.format("%s/v3/matches/%s/%s/%s?size=10%s", BASE_API, initRegion, encName, encTag,
                modeParam);
        String v3CompUrl = String.format("%s/v3/matches/%s/%s/%s?size=10&mode=competitive", BASE_API, initRegion,
                encName, encTag);

        CompletableFuture<HttpResponse<String>> accFuture = sendAsyncGet(accUrl);
        CompletableFuture<HttpResponse<String>> mmrFuture = sendAsyncGet(mmrUrl);
        CompletableFuture<HttpResponse<String>> storedFuture1 = sendAsyncGet(storedUrl1);
        CompletableFuture<HttpResponse<String>> storedFuture2 = sendAsyncGet(storedUrl2);
        CompletableFuture<HttpResponse<String>> v3Future = sendAsyncGet(v3Url);
        CompletableFuture<HttpResponse<String>> v3CompFuture = ("ALL".equalsIgnoreCase(modeKey) || modeKey == null
                || modeKey.isEmpty())
                        ? sendAsyncGet(v3CompUrl)
                        : CompletableFuture.completedFuture(null);

        FullProfileResponse result = new FullProfileResponse();
        result.profile.name = cleanName;
        result.profile.tag = cleanTag;
        result.profile.region = initRegion;

        // 1. Parse Account Response
        try {
            HttpResponse<String> accResp = accFuture.join();
            if (accResp != null && accResp.statusCode() == 200) {
                parseAccountJson(accResp.body(), result.profile);
            } else if (accResp != null && accResp.statusCode() == 404) {
                throw new ApiException("Player '" + cleanName + "#" + cleanTag + "' not found (HTTP 404).",
                        404, "Verify the Riot ID Name, #Tagline, and Region.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception ignored) {
        }

        // 2. Parse MMR & Episode/Act History (by_season)
        try {
            HttpResponse<String> mmrResp = mmrFuture.join();
            if (mmrResp != null && mmrResp.statusCode() == 200) {
                parseMmrAndActsJson(mmrResp.body(), result.profile);
            }
        } catch (Exception ignored) {
        }

        String latestActCode = result.profile.actsList.isEmpty() ? "" : result.profile.actsList.get(0).code;

        // 3. Parse Stored Matches Page 1 & Page 2 first so we know every match's exact
        // seasonCode (e.g. e11a6, e11a5)
        Map<String, String> seasonUuidToShort = new HashMap<>();
        List<MatchStats> storedMatches = new ArrayList<>();
        Set<String> seenStoredIds = new HashSet<>();

        try {
            HttpResponse<String> sResp1 = storedFuture1.join();
            if (sResp1 != null && sResp1.statusCode() == 200) {
                List<MatchStats> page1 = parseStoredMatchesJson(
                        sResp1.body(), result.profile.name, result.profile.tag, result.profile.region, latestActCode,
                        seasonUuidToShort);
                for (MatchStats sm : page1) {
                    if (sm.getMatchId() != null && !sm.getMatchId().isEmpty() && seenStoredIds.add(sm.getMatchId())) {
                        storedMatches.add(sm);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            HttpResponse<String> sResp2 = storedFuture2.join();
            if (sResp2 != null && sResp2.statusCode() == 200) {
                List<MatchStats> page2 = parseStoredMatchesJson(
                        sResp2.body(), result.profile.name, result.profile.tag, result.profile.region, latestActCode,
                        seasonUuidToShort);
                for (MatchStats sm : page2) {
                    if (sm.getMatchId() != null && !sm.getMatchId().isEmpty() && seenStoredIds.add(sm.getMatchId())) {
                        storedMatches.add(sm);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 4. Parse v3 Matches (both general recent feed AND Competitive 10-match feed)
        Map<String, MatchStats> v3ById = new LinkedHashMap<>();
        try {
            HttpResponse<String> v3Resp = v3Future.join();
            if (v3Resp != null && v3Resp.statusCode() == 200) {
                List<MatchStats> v3List = parseHenrikV3Json(
                        v3Resp.body(), result.profile.name, result.profile.tag, result.profile.region,
                        result.profile.puuid, seasonUuidToShort, latestActCode);
                for (MatchStats m : v3List) {
                    if (m.getMatchId() != null && !m.getMatchId().isEmpty()) {
                        v3ById.put(m.getMatchId(), m);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            HttpResponse<String> v3CompResp = v3CompFuture.join();
            if (v3CompResp != null && v3CompResp.statusCode() == 200) {
                List<MatchStats> v3CompList = parseHenrikV3Json(
                        v3CompResp.body(), result.profile.name, result.profile.tag, result.profile.region,
                        result.profile.puuid, seasonUuidToShort, latestActCode);
                for (MatchStats m : v3CompList) {
                    if (m.getMatchId() != null && !m.getMatchId().isEmpty() && !v3ById.containsKey(m.getMatchId())) {
                        v3ById.put(m.getMatchId(), m);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Normalize storedMatches season codes to align with mmrData.by_season act
        // codes (e.g. e10a6 -> e11a6)
        if (!storedMatches.isEmpty() && !result.profile.actsList.isEmpty()) {
            Set<String> knownActCodes = new HashSet<>();
            for (ActSeasonRecord a : result.profile.actsList)
                knownActCodes.add(a.code.toLowerCase());

            List<String> distinctStoredSeasons = new ArrayList<>();
            for (MatchStats sm : storedMatches) {
                String sc = sm.getSeasonCode().toLowerCase();
                if (!sc.isEmpty() && !distinctStoredSeasons.contains(sc)) {
                    distinctStoredSeasons.add(sc);
                }
            }
            if (!distinctStoredSeasons.isEmpty() && !knownActCodes.contains(distinctStoredSeasons.get(0))) {
                Map<String, String> storedToMmrSeasonMap = new HashMap<>();
                for (int i = 0; i < distinctStoredSeasons.size() && i < result.profile.actsList.size(); i++) {
                    storedToMmrSeasonMap.put(distinctStoredSeasons.get(i), result.profile.actsList.get(i).code);
                }
                for (MatchStats sm : storedMatches) {
                    String mapped = storedToMmrSeasonMap.get(sm.getSeasonCode().toLowerCase());
                    if (mapped != null)
                        sm.setSeasonCode(mapped);
                }
            }
        }

        // 5. Merge stored matches with v3 enriched match details (identical to
        // web/index.html)
        Set<String> mergedIds = new HashSet<>();
        for (MatchStats sm : storedMatches) {
            String id = sm.getMatchId();
            mergedIds.add(id);
            if (v3ById.containsKey(id)) {
                MatchStats enriched = v3ById.get(id);
                if (sm.getSeasonCode() != null && !sm.getSeasonCode().isEmpty()) {
                    enriched.setSeasonCode(sm.getSeasonCode());
                }
                result.allMatches.add(enriched);
            } else {
                result.allMatches.add(sm);
            }
        }

        // Prepend/append any v3 matches that were not in storedMatches
        int insertIdx = 0;
        for (MatchStats v3m : v3ById.values()) {
            if (!mergedIds.contains(v3m.getMatchId())) {
                if (v3m.getSeasonCode() == null || v3m.getSeasonCode().isEmpty()) {
                    v3m.setSeasonCode(latestActCode);
                }
                result.allMatches.add(insertIdx++, v3m);
                mergedIds.add(v3m.getMatchId());
            }
        }

        return result;
    }

    @Override
    public List<MatchStats> fetchRecentMatches(String playerName, String playerTag, String region) throws ApiException {
        FullProfileResponse res = fetchFullProfileParallel(playerName, playerTag, region, "ALL");
        if (res.allMatches.isEmpty()) {
            throw new ApiException(
                    "No recent matches found for " + playerName + "#" + playerTag + ".",
                    200,
                    "Check that the Riot ID and Region (" + region + ") are correct.");
        }
        return res.allMatches;
    }

    /**
     * Lightweight fetcher for mode switching: only fetches the 5 most recent v3
     * matches
     * for a specific game mode instead of re-fetching Account, MMR, and Stored
     * Matches.
     */
    public List<MatchStats> fetchModeMatchesOnly(
            String playerName, String playerTag, String region, String modeKey,
            String playerPuuid, String latestActCode) {
        if (modeKey == null || modeKey.isEmpty() || "ALL".equalsIgnoreCase(modeKey)) {
            return java.util.Collections.emptyList();
        }
        try {
            String cleanName = playerName.trim();
            String cleanTag = playerTag.trim().replaceFirst("^#", "");
            String initRegion = (region == null || region.trim().isEmpty()) ? "ap" : region.trim().toLowerCase();
            String v3Url = String.format("%s/v3/matches/%s/%s/%s?size=5&mode=%s",
                    BASE_API, initRegion, encode(cleanName), encode(cleanTag), encode(modeKey.toLowerCase()));
            HttpResponse<String> resp = sendAsyncGet(v3Url).join();
            if (resp != null && resp.statusCode() == 200) {
                return parseHenrikV3Json(resp.body(), cleanName, cleanTag, initRegion, playerPuuid, null,
                        latestActCode);
            }
        } catch (Exception ignored) {
        }
        return java.util.Collections.emptyList();
    }

    private CompletableFuture<HttpResponse<String>> sendAsyncGet(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Authorization", apiKey)
                .header("User-Agent", "ValorantTrackerGUI/2.0")
                .GET()
                .build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> null);
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ==================== BRACE-BALANCED JSON PARSERS (ZERO EXTERNAL JARS)
    // ====================

    private void parseAccountJson(String json, PlayerProfileData profile) {
        if (json == null || json.isEmpty())
            return;
        String dataObj = extractJsonObject(json, "data");
        String src = dataObj.isEmpty() ? json : dataObj;

        profile.puuid = extractStringField(src, "puuid", profile.puuid);
        profile.name = extractStringField(src, "name", profile.name);
        profile.tag = extractStringField(src, "tag", profile.tag);
        profile.region = extractStringField(src, "region", profile.region).toLowerCase();
        profile.accountLevel = extractIntField(src, "account_level", profile.accountLevel);
        profile.lastUpdate = extractStringField(src, "last_update", "Recently");

        String cardObj = extractJsonObject(src, "card");
        if (!cardObj.isEmpty()) {
            profile.smallCardUrl = extractStringField(cardObj, "small", "");
            profile.wideCardUrl = extractStringField(cardObj, "wide", "");
        }
    }

    private void parseMmrAndActsJson(String json, PlayerProfileData profile) {
        if (json == null || json.isEmpty())
            return;
        String dataObj = extractJsonObject(json, "data");
        String src = dataObj.isEmpty() ? json : dataObj;

        String curSub = extractJsonObject(src, "current_data");
        if (!curSub.isEmpty()) {
            profile.currentRankName = extractStringField(curSub, "currenttierpatched", "Unrated");
            int tierId = extractIntField(curSub, "currenttier", 0);
            int rr = extractIntField(curSub, "ranking_in_tier", 0);
            int diff = extractSignedIntField(curSub, "mmr_change_to_last_game", 0);
            int elo = extractIntField(curSub, "elo", 0);
            String diffStr = diff >= 0 ? "+" + diff : String.valueOf(diff);
            profile.currentRankRrText = String.format("%d RR • Last: %s RR%s",
                    rr, diffStr, elo > 0 ? " • ELO: " + elo : "");
            String imagesObj = extractJsonObject(curSub, "images");
            String largeImg = extractStringField(imagesObj, "large", "");
            profile.currentRankIconUrl = !largeImg.isEmpty() ? largeImg : getTierIconUrl(tierId, true);
        }

        String highSub = extractJsonObject(src, "highest_rank");
        if (!highSub.isEmpty()) {
            profile.peakRankName = extractStringField(highSub, "patched_tier", "N/A");
            String rawSeason = extractStringField(highSub, "season", "");
            profile.peakRankSeason = formatActCode(rawSeason, false);
        }

        // Parse by_season Episode // Act objects (e.g. "e11a6": { "wins": 101,
        // "number_of_games": 212, ... })
        profile.actsList.clear();
        String bySeasonObj = extractJsonObject(src, "by_season");
        String seasonSearch = bySeasonObj.isEmpty() ? src : bySeasonObj;

        Pattern actPattern = Pattern.compile("\"(e\\d+a\\d+)\"\\s*:\\s*\\{");
        Matcher matcher = actPattern.matcher(seasonSearch);
        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase();
            int openBraceIdx = matcher.end() - 1;
            String actBlock = extractBalancedBlock(seasonSearch, openBraceIdx, '{', '}');
            if (actBlock.isEmpty() || actBlock.contains("\"error\""))
                continue;

            int games = extractIntField(actBlock, "number_of_games", 0);
            if (games <= 0)
                continue;
            int wins = extractIntField(actBlock, "wins", 0);
            int losses = Math.max(0, games - wins);
            int finalRankId = extractIntField(actBlock, "final_rank", 0);
            String finalRankName = extractStringField(actBlock, "final_rank_patched", "Unrated");

            // Find peak tier in act_rank_wins
            int peakTier = finalRankId;
            String peakName = finalRankName;
            List<String> winObjs = extractJsonArrayObjects(actBlock, "act_rank_wins");
            int triangleCount = winObjs.size();
            for (String wObj : winObjs) {
                int t = extractIntField(wObj, "tier", 0);
                if (t > peakTier) {
                    peakTier = t;
                    peakName = extractStringField(wObj, "patched_tier", peakName);
                }
            }

            ActSeasonRecord rec = new ActSeasonRecord();
            rec.code = code;
            rec.longName = formatActCode(code, true);
            rec.shortName = formatActCode(code, false);
            rec.wins = wins;
            rec.losses = losses;
            rec.games = games;
            rec.winRate = (wins * 100.0) / games;
            rec.finalRankId = finalRankId;
            rec.finalRankName = finalRankName;
            rec.peakInActTier = peakTier;
            rec.peakInActName = peakName;
            rec.triangleWinsCount = triangleCount;
            rec.smallIconUrl = getTierIconUrl(finalRankId, true);
            rec.largeIconUrl = getTierIconUrl(finalRankId, true);
            profile.actsList.add(rec);
        }

        // Sort newest Episode & Act first
        profile.actsList.sort((a, b) -> Integer.compare(actSortScore(b.code), actSortScore(a.code)));
    }

    private int actSortScore(String code) {
        Matcher m = Pattern.compile("^e(\\d+)a(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(code);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2));
        }
        return 0;
    }

    private List<MatchStats> parseStoredMatchesJson(
            String json, String playerName, String playerTag, String region,
            String fallbackSeason, Map<String, String> seasonUuidToShort) {
        List<MatchStats> list = new ArrayList<>();
        if (json == null || json.isEmpty())
            return list;

        List<String> items = extractJsonArrayObjects(json, "data");
        for (String itemObj : items) {
            String metaObj = extractJsonObject(itemObj, "meta");
            String statsObj = extractJsonObject(itemObj, "stats");
            String teamsObj = extractJsonObject(itemObj, "teams");
            if (metaObj.isEmpty() || statsObj.isEmpty())
                continue;

            String matchId = extractStringField(metaObj, "id", "");
            if (matchId.isEmpty())
                continue;

            String mapObj = extractJsonObject(metaObj, "map");
            String mapName = extractStringField(mapObj, "name", "Unknown Map");
            String mode = extractStringField(metaObj, "mode", "Competitive");
            String startedAt = extractStringField(metaObj, "started_at", "");
            if (startedAt.length() >= 16) {
                startedAt = startedAt.substring(0, 16).replace('T', ' ');
            }
            String cluster = extractStringField(metaObj, "cluster", "");

            String seasonObj = extractJsonObject(metaObj, "season");
            String seasonUuid = extractStringField(seasonObj, "id", "");
            String seasonShort = extractStringField(seasonObj, "short", fallbackSeason).toLowerCase();
            if (!seasonUuid.isEmpty() && !seasonShort.isEmpty() && seasonUuidToShort != null) {
                seasonUuidToShort.put(seasonUuid.toLowerCase(), seasonShort);
            }

            String team = extractStringField(statsObj, "team", "Blue");
            String charObj = extractJsonObject(statsObj, "character");
            String charId = extractStringField(charObj, "id", "");
            String agentName = extractStringField(charObj, "name", "Unknown");

            int kills = extractIntField(statsObj, "kills", 0);
            int deaths = extractIntField(statsObj, "deaths", 0);
            int assists = extractIntField(statsObj, "assists", 0);
            int score = extractIntField(statsObj, "score", 0);
            int tier = extractIntField(statsObj, "tier", 0);

            String shotsObj = extractJsonObject(statsObj, "shots");
            int hs = extractIntField(shotsObj, "head", 0);
            int bs = extractIntField(shotsObj, "body", 0);
            int ls = extractIntField(shotsObj, "leg", 0);

            String dmgObj = extractJsonObject(statsObj, "damage");
            int dmgMade = extractIntField(dmgObj, "made", 0);
            int dmgRecv = extractIntField(dmgObj, "received", 0);

            int redRounds = extractIntField(teamsObj, "red", 0);
            int blueRounds = extractIntField(teamsObj, "blue", 0);

            boolean isDm = "deathmatch".equalsIgnoreCase(mode);
            int rWon;
            int rLost;
            boolean hasWon;
            if (!isDm && (!teamsObj.isEmpty())) {
                if ("blue".equalsIgnoreCase(team)) {
                    rWon = blueRounds;
                    rLost = redRounds;
                } else {
                    rWon = redRounds;
                    rLost = blueRounds;
                }
                hasWon = rWon > rLost;
            } else {
                rWon = kills;
                rLost = deaths;
                hasWon = kills >= 35;
            }

            int rPlayed = (!isDm && (rWon + rLost) > 0) ? (rWon + rLost) : 20;
            int acs = Math.round((float) score / Math.max(1, rPlayed));

            MatchStats m = new MatchStats(
                    matchId, playerName, playerTag, region,
                    mapName, agentName, mode, "—",
                    kills, deaths, assists, rWon, rLost,
                    hs, bs, ls, acs, startedAt);
            m.setSeasonCode(seasonShort);
            m.setCluster(cluster);
            m.setRoundsPlayed(rPlayed);
            m.setTotalScore(score);
            m.setDamageMade(dmgMade);
            m.setDamageReceived(dmgRecv);
            m.setDeathmatch(isDm);
            m.setHasWon(hasWon);
            m.setMatchTierPatched(tier > 0 ? "Tier " + tier : "Competitive");
            if (!charId.isEmpty()) {
                m.setAgentIconUrl("https://media.valorant-api.com/agents/" + charId + "/displayicon.png");
            }
            list.add(m);
        }
        return list;
    }

    /**
     * Parses HenrikDev v3 matches JSON response using brace-balanced object
     * extraction:
     * - Exact metadata per match (never shifted by matchid ordering!)
     * - Exact 10 players from players.all_players (never duplicated by red/blue
     * arrays!)
     * - Exact damage_made & damage_received (never truncated by 2200-char window!)
     * - Exact kills[] & rounds[].player_stats[].damage_events[] per weapon
     */
    public List<MatchStats> parseHenrikV3Json(String json, String targetName, String targetTag, String region) {
        return parseHenrikV3Json(json, targetName, targetTag, region, "", null, "");
    }

    public List<MatchStats> parseHenrikV3Json(
            String json, String targetName, String targetTag, String region,
            String targetPuuid, Map<String, String> seasonUuidToShort, String fallbackSeason) {
        List<MatchStats> results = new ArrayList<>();
        if (json == null || json.isEmpty())
            return results;

        List<String> matchObjects = extractJsonArrayObjects(json, "data");
        for (int i = 0; i < matchObjects.size(); i++) {
            String matchObj = matchObjects.get(i);
            String metaObj = extractJsonObject(matchObj, "metadata");
            if (metaObj.isEmpty())
                continue;

            String matchId = extractStringField(metaObj, "matchid", "VAL-LIVE-" + (100 + i));
            String mapName = extractStringField(metaObj, "map", "Unknown Map");
            String mode = extractStringField(metaObj, "mode", extractStringField(metaObj, "queue", "Standard"));
            String cluster = extractStringField(metaObj, "cluster", "");
            String gameStartPatched = extractStringField(metaObj, "game_start_patched", "");
            String seasonId = extractStringField(metaObj, "season_id", "").toLowerCase();
            String mappedSeason = (seasonUuidToShort != null && seasonUuidToShort.containsKey(seasonId))
                    ? seasonUuidToShort.get(seasonId)
                    : fallbackSeason;

            boolean isDm = "deathmatch".equalsIgnoreCase(mode);
            int rawRounds = extractIntField(metaObj, "rounds_played", 1);
            int roundsPlayed = (isDm || rawRounds <= 1) ? 20 : rawRounds;
            int gameLength = extractIntField(metaObj, "game_length", 0);
            int durationMins = gameLength > 100000 ? Math.round(gameLength / 60000f) : Math.round(gameLength / 60f);

            // Parse ONLY the 10 players inside players.all_players
            List<ScoreboardPlayer> allPlayers = parseAllPlayersFromMatchObj(
                    matchObj, targetName, targetTag, targetPuuid, roundsPlayed);
            if (allPlayers.isEmpty())
                continue;

            ScoreboardPlayer mePlayer = null;
            for (ScoreboardPlayer sp : allPlayers) {
                if (sp.isMe) {
                    mePlayer = sp;
                    break;
                }
            }
            if (mePlayer == null)
                continue;

            // Sort allPlayers by score descending (identical to web/index.html line 2809)
            allPlayers.sort((a, b) -> Integer.compare(b.score, a.score));
            int placementNum = 1;
            for (int idx = 0; idx < allPlayers.size(); idx++) {
                allPlayers.get(idx).rankPos = idx + 1;
                if (allPlayers.get(idx).isMe) {
                    placementNum = idx + 1;
                }
            }

            boolean isTeamMvp = false;
            for (ScoreboardPlayer sp : allPlayers) {
                if (sp.team != null && sp.team.equalsIgnoreCase(mePlayer.team)) {
                    isTeamMvp = sp.isMe;
                    break;
                }
            }

            String placementLabel;
            if (placementNum == 1)
                placementLabel = "\u2605 MATCH MVP";
            else if (isTeamMvp)
                placementLabel = "TEAM MVP";
            else if (placementNum == 2)
                placementLabel = "2nd Place";
            else if (placementNum == 3)
                placementLabel = "3rd Place";
            else
                placementLabel = placementNum + "th Place";

            int roundsWon = 0;
            int roundsLost = 0;
            boolean hasWon = false;

            String teamsObj = extractJsonObject(matchObj, "teams");
            String teamKey = mePlayer.team == null ? "blue" : mePlayer.team.toLowerCase();
            String myTeamObj = extractJsonObject(teamsObj, teamKey);

            if (!isDm && !myTeamObj.isEmpty() && myTeamObj.contains("\"rounds_won\"")) {
                roundsWon = extractIntField(myTeamObj, "rounds_won", 0);
                roundsLost = extractIntField(myTeamObj, "rounds_lost", 0);
                boolean teamWonFlag = myTeamObj.contains("\"has_won\":true") || myTeamObj.contains("\"has_won\": true");
                hasWon = teamWonFlag || (roundsWon > roundsLost);
            } else {
                roundsWon = mePlayer.kills;
                roundsLost = mePlayer.deaths;
                hasWon = placementNum <= 3;
            }

            // Extract real weapon kills & round-by-round hitbox damage events for this
            // player
            Map<String, WeaponEvent> weaponEvents = parseWeaponKillsAndHitsForPlayer(matchObj, mePlayer);
            String topWeapon = "—";
            String topWeaponIcon = "";
            int topWeaponKills = 0;
            for (WeaponEvent we : weaponEvents.values()) {
                if (we.kills > topWeaponKills) {
                    topWeaponKills = we.kills;
                    topWeapon = we.name;
                    topWeaponIcon = we.iconUrl;
                }
            }

            int acs = Math.round((float) mePlayer.score / Math.max(1, roundsPlayed));

            MatchStats m = new MatchStats(
                    matchId,
                    mePlayer.name.isEmpty() ? targetName : mePlayer.name,
                    mePlayer.tag.isEmpty() ? targetTag : mePlayer.tag,
                    region,
                    mapName, mePlayer.character, mode, topWeapon,
                    mePlayer.kills, mePlayer.deaths, mePlayer.assists,
                    roundsWon, roundsLost,
                    mePlayer.headshots, mePlayer.bodyshots, mePlayer.legshots,
                    acs, gameStartPatched);
            m.setSeasonCode(mappedSeason == null ? "" : mappedSeason);
            m.setCluster(cluster);
            m.setAgentIconUrl(mePlayer.agentIconUrl);
            m.setMatchTierPatched(mePlayer.tierPatched);
            m.setRoundsPlayed(roundsPlayed);
            m.setTotalScore(mePlayer.score);
            m.setDamageMade(mePlayer.damageMade);
            m.setDamageReceived(mePlayer.damageReceived);
            m.setDurationText(durationMins > 0 ? durationMins + "m" : "");
            m.setHasWon(hasWon);
            m.setDeathmatch(isDm);
            m.setPlacementNum(placementNum);
            m.setPlacementLabel(placementLabel);
            m.setTopWeaponKills(topWeaponKills);
            m.setTopWeaponIconUrl(topWeaponIcon);
            m.setWeaponEvents(weaponEvents);
            m.setScoreboard(allPlayers);
            results.add(m);
        }

        return results;
    }

    private List<ScoreboardPlayer> parseAllPlayersFromMatchObj(
            String matchObj, String targetName, String targetTag, String targetPuuid, int roundsPlayed) {
        List<ScoreboardPlayer> players = new ArrayList<>();
        String playersObj = extractJsonObject(matchObj, "players");
        if (playersObj.isEmpty())
            return players;

        List<String> allPlayerObjs = extractJsonArrayObjects(playersObj, "all_players");
        for (String pb : allPlayerObjs) {
            ScoreboardPlayer sp = new ScoreboardPlayer();
            sp.puuid = extractStringField(pb, "puuid", "");
            sp.name = extractStringField(pb, "name", "Player");
            sp.tag = extractStringField(pb, "tag", "");
            sp.team = extractStringField(pb, "team", "Neutral");
            sp.character = extractStringField(pb, "character", "Agent");
            sp.tierPatched = extractStringField(pb, "currenttier_patched", "Unrated");

            boolean puuidMatch = targetPuuid != null && !targetPuuid.isEmpty()
                    && targetPuuid.equalsIgnoreCase(sp.puuid);
            boolean nameAndTagMatch = sp.name.equalsIgnoreCase(targetName)
                    && (targetTag == null || targetTag.isEmpty() || sp.tag.equalsIgnoreCase(targetTag));
            boolean nameOnlyMatch = sp.name.equalsIgnoreCase(targetName);
            sp.isMe = puuidMatch || nameAndTagMatch || nameOnlyMatch;

            String assetsObj = extractJsonObject(pb, "assets");
            String agentObj = extractJsonObject(assetsObj, "agent");
            sp.agentIconUrl = extractStringField(agentObj, "small", "");

            String statsObj = extractJsonObject(pb, "stats");
            sp.score = extractIntField(statsObj, "score", 0);
            sp.kills = extractIntField(statsObj, "kills", 0);
            sp.deaths = extractIntField(statsObj, "deaths", 0);
            sp.assists = extractIntField(statsObj, "assists", 0);
            sp.headshots = extractIntField(statsObj, "headshots", 0);
            sp.bodyshots = extractIntField(statsObj, "bodyshots", 0);
            sp.legshots = extractIntField(statsObj, "legshots", 0);

            sp.damageMade = extractIntField(pb, "damage_made", 0);
            sp.damageReceived = extractIntField(pb, "damage_received", 0);

            int totShots = sp.headshots + sp.bodyshots + sp.legshots;
            sp.acs = Math.round((float) sp.score / Math.max(1, roundsPlayed));
            sp.adr = Math.round((float) sp.damageMade / Math.max(1, roundsPlayed));
            sp.hsPct = totShots > 0 ? (sp.headshots * 100.0) / totShots : 0.0;
            players.add(sp);
        }
        return players;
    }

    private Map<String, WeaponEvent> parseWeaponKillsAndHitsForPlayer(String matchObj, ScoreboardPlayer me) {
        Map<String, WeaponEvent> map = new LinkedHashMap<>();
        String myPuuid = me.puuid == null ? "" : me.puuid;
        String myFullDisplay = (me.name + "#" + me.tag).toLowerCase();
        String myNameLower = me.name.toLowerCase();

        // 1. Parse match.kills[] array
        List<String> killObjs = extractJsonArrayObjects(matchObj, "kills");
        for (String kb : killObjs) {
            String killerPuuid = extractStringField(kb, "killer_puuid", "");
            String killerDisp = extractStringField(kb, "killer_display_name", "").toLowerCase();
            boolean isMyKill = (!myPuuid.isEmpty() && myPuuid.equalsIgnoreCase(killerPuuid))
                    || killerDisp.equals(myFullDisplay)
                    || killerDisp.startsWith(myNameLower + "#");
            if (!isMyKill)
                continue;

            String wName = extractStringField(kb, "damage_weapon_name", "");
            if (wName.isEmpty())
                wName = "Ability / Melee";
            String wAssets = extractJsonObject(kb, "damage_weapon_assets");
            String wIcon = extractStringField(wAssets.isEmpty() ? kb : wAssets, "display_icon", "");
            boolean isAlt = kb.contains("\"secondary_fire_mode\":true") || kb.contains("\"secondary_fire_mode\": true");

            WeaponEvent we = map.computeIfAbsent(wName, k -> new WeaponEvent(k, wIcon));
            we.kills++;
            if (isAlt)
                we.altFireKills++;
            else
                we.hipFireKills++;
            if (we.iconUrl.isEmpty() && !wIcon.isEmpty()) {
                we.iconUrl = wIcon;
            }
        }

        // 2. Parse match.rounds[].player_stats[].damage_events[] for exact per-weapon
        // Head/Body/Leg hits
        List<String> roundObjs = extractJsonArrayObjects(matchObj, "rounds");
        for (String rndObj : roundObjs) {
            List<String> pStatsList = extractJsonArrayObjects(rndObj, "player_stats");
            for (String psObj : pStatsList) {
                String psPuuid = extractStringField(psObj, "player_puuid", "");
                String psDisp = extractStringField(psObj, "player_display_name", "").toLowerCase();
                boolean isMeRound = (!myPuuid.isEmpty() && myPuuid.equalsIgnoreCase(psPuuid))
                        || psDisp.equals(myFullDisplay)
                        || psDisp.startsWith(myNameLower + "#");
                if (!isMeRound)
                    continue;

                String roundWeaponName = "";
                String roundWeaponIcon = "";
                List<String> rndKills = extractJsonArrayObjects(psObj, "kill_events");
                if (!rndKills.isEmpty()) {
                    String firstKill = rndKills.get(0);
                    roundWeaponName = extractStringField(firstKill, "damage_weapon_name", "");
                    String wa = extractJsonObject(firstKill, "damage_weapon_assets");
                    roundWeaponIcon = extractStringField(wa.isEmpty() ? firstKill : wa, "display_icon", "");
                }
                if (roundWeaponName.isEmpty()) {
                    String econObj = extractJsonObject(psObj, "economy");
                    String wepObj = extractJsonObject(econObj, "weapon");
                    roundWeaponName = extractStringField(wepObj, "name", "");
                    String wa = extractJsonObject(wepObj, "assets");
                    roundWeaponIcon = extractStringField(wa, "display_icon", "");
                }

                if (!roundWeaponName.isEmpty() && map.containsKey(roundWeaponName)) {
                    WeaponEvent we = map.get(roundWeaponName);
                    List<String> dmgEvents = extractJsonArrayObjects(psObj, "damage_events");
                    for (String de : dmgEvents) {
                        we.headshots += extractIntField(de, "headshots", 0);
                        we.bodyshots += extractIntField(de, "bodyshots", 0);
                        we.legshots += extractIntField(de, "legshots", 0);
                    }
                    if (we.iconUrl.isEmpty() && !roundWeaponIcon.isEmpty()) {
                        we.iconUrl = roundWeaponIcon;
                    }
                }
                break;
            }
        }

        return map;
    }

    // ==================== DETERMINISTIC ACT-SPECIFIC MATCH & ACCURACY GENERATOR
    // ====================

    public static long hashActSeed(String str) {
        long h = 2166136261L;
        for (int i = 0; i < str.length(); i++) {
            h ^= str.charAt(i);
            h = (h * 16777619L) & 0xFFFFFFFFL;
        }
        return h;
    }

    public static List<MatchStats> buildActSpecificMatches(List<MatchStats> baseMatches, ActSeasonRecord actObj,
            int actIdx) {
        if (baseMatches == null || baseMatches.isEmpty() || actObj == null)
            return baseMatches;

        long seedBase = hashActSeed(actObj.code + ":" + actObj.wins + ":" + actObj.games + ":" + actObj.finalRankId);
        String[] knownMaps = { "Ascent", "Bind", "Haven", "Split", "Lotus", "Sunset", "Icebox", "Pearl", "Abyss" };

        List<String[]> playerAgents = new ArrayList<>();
        Set<String> seenAg = new HashSet<>();
        for (MatchStats m : baseMatches) {
            if (m.getAgentName() != null && !seenAg.contains(m.getAgentName())) {
                seenAg.add(m.getAgentName());
                playerAgents.add(new String[] { m.getAgentName(), m.getAgentIconUrl() });
            }
        }
        if (playerAgents.isEmpty()) {
            playerAgents.add(new String[] { "Jett", "" });
        }

        double targetWinRatio = Math.min(0.85, Math.max(0.20, actObj.winRate / 100.0));
        // Expected Competitive K/D in SBMM scales naturally around 1.00 with the Act's
        // official Win Rate
        double targetActKd = Math.max(0.82, Math.min(1.38, 0.96 + (targetWinRatio - 0.48) * 0.85));
        double hsBias = 0.78 + ((seedBase % 47) / 100.0)
                + ((actObj.peakInActTier > 0 ? actObj.peakInActTier : 9) - 9) * 0.025;
        double lsBias = 0.75 + (((seedBase >> 4) % 55) / 100.0);

        int n = baseMatches.size();
        int targetWinsInList = (int) Math.round(n * targetWinRatio);
        int shift = (int) ((actIdx + (seedBase % n)) % n);

        List<MatchStats> rotated = new ArrayList<>();
        rotated.addAll(baseMatches.subList(shift, n));
        rotated.addAll(baseMatches.subList(0, shift));

        List<MatchStats> generated = new ArrayList<>();
        for (int idx = 0; idx < rotated.size(); idx++) {
            MatchStats orig = rotated.get(idx);
            MatchStats copy = orig.copy();
            long mSeed = hashActSeed(actObj.code + ":m:" + idx + ":" + orig.getMatchId());

            boolean isVictory = idx < targetWinsInList ? ((mSeed % 5) != 0) : ((mSeed % 4) == 0);
            int rw = isVictory ? 13 : (int) (5 + (mSeed % 7));
            int rl = isVictory ? (int) (4 + ((mSeed >> 3) % 8)) : 13;
            int rPlayed = Math.max(12, rw + rl);

            String[] agChoice = playerAgents.get((int) ((idx + (mSeed % playerAgents.size())) % playerAgents.size()));
            String mapChoice = knownMaps[(int) ((idx * 3 + (mSeed % knownMaps.length)) % knownMaps.length)];

            int kVar = (int) ((mSeed % 7) - 3);
            int dVar = (int) (((mSeed >> 5) % 5) - 2);
            int newDeaths = Math.max(8, (int) Math.round(rPlayed * (isVictory ? 0.67 : 0.77) + dVar));
            double matchKdFactor = targetActKd * (isVictory ? 1.16 : 0.87);
            int newKills = Math.max(6, (int) Math.round(newDeaths * matchKdFactor + kVar));
            int newAssists = Math.max(2, (int) Math.round(rPlayed * 0.24 + ((mSeed >> 7) % 5) - 2));

            int newHs = Math.max(2, (int) Math.round(Math.max(6, orig.getHeadshots()) * hsBias + ((mSeed % 7) - 3)));
            int newBs = Math.max(10, (int) Math.round(
                    Math.max(25, orig.getBodyshots()) * (1.05 - (hsBias - 1.0) * 0.3) + (((mSeed >> 2) % 11) - 5)));
            int newLs = Math.max(1, (int) Math.round(Math.max(3, orig.getLegshots()) * lsBias + ((mSeed % 5) - 2)));

            int newDmgMade = Math.max(800, (int) Math.round(newKills * 152 + newAssists * 38 + ((mSeed % 400) - 200)));
            int newDmgRecv = Math.max(800, (int) Math.round(newDeaths * 148 + (((mSeed >> 4) % 350) - 175)));
            int newScore = Math.max(1200, newKills * 215 + newAssists * 45 + (isVictory ? 350 : 0));
            int newAcs = Math.round((float) newScore / rPlayed);

            String placeLbl;
            if (newAcs >= 265)
                placeLbl = "\u2605 MATCH MVP";
            else if (newAcs >= 225)
                placeLbl = "TEAM MVP";
            else if (newAcs >= 195)
                placeLbl = "3rd Place";
            else
                placeLbl = (4 + (mSeed % 5)) + "th Place";

            Map<String, WeaponEvent> newWeapons = new LinkedHashMap<>();
            int wIdx = 0;
            int wCount = Math.max(1, orig.getWeaponEvents().size());
            for (WeaponEvent w : orig.getWeaponEvents().values()) {
                WeaponEvent wc = w.copy();
                wc.kills = Math.max(1, (int) Math.round(w.kills * targetActKd + (((mSeed >> wIdx) % 3) - 1)));
                wc.hipFireKills = Math.max(1, wc.kills - w.altFireKills);
                wc.altFireKills = Math.min(wc.kills, w.altFireKills);
                wc.headshots = Math.max(1, (int) Math
                        .round((w.headshots > 0 ? w.headshots : Math.round((float) newHs / wCount)) * hsBias));
                wc.bodyshots = Math.max(2, w.bodyshots > 0 ? w.bodyshots : Math.round((float) newBs / wCount));
                wc.legshots = Math.max(0,
                        (int) Math.round((w.legshots > 0 ? w.legshots : Math.round((float) newLs / wCount)) * lsBias));
                newWeapons.put(wc.name, wc);
                wIdx++;
            }

            copy.setSeasonCode(actObj.code);
            copy.setMapName(mapChoice);
            copy.setAgentName(agChoice[0]);
            if (agChoice[1] != null && !agChoice[1].isEmpty())
                copy.setAgentIconUrl(agChoice[1]);
            copy.setMatchTierPatched(actObj.finalRankName);
            copy.setMatchDate(actObj.shortName + " \u2022 Match #" + Math.max(1, actObj.games - idx));
            copy.setRoundsPlayed(rPlayed);
            copy.setRoundsWon(rw);
            copy.setRoundsLost(rl);
            copy.setHasWon(isVictory);
            copy.setDeathmatch(false);
            copy.setKills(newKills);
            copy.setDeaths(newDeaths);
            copy.setAssists(newAssists);
            copy.setHeadshots(newHs);
            copy.setBodyshots(newBs);
            copy.setLegshots(newLs);
            copy.setDamageMade(newDmgMade);
            copy.setDamageReceived(newDmgRecv);
            copy.setTotalScore(newScore);
            copy.setAverageCombatScore(newAcs);
            copy.setPlacementLabel(placeLbl);
            copy.setWeaponEvents(newWeapons);
            generated.add(copy);
        }
        return generated;
    }

    // ==================== BRACE-BALANCED JSON EXTRACTOR HELPERS
    // ====================

    private String extractJsonObject(String json, String key) {
        if (json == null || json.isEmpty() || key == null)
            return "";
        Pattern p = OBJ_PATTERN_CACHE.computeIfAbsent(key,
                k -> Pattern.compile("\"" + Pattern.quote(k) + "\"\\s*:\\s*\\{"));
        Matcher m = p.matcher(json);
        if (!m.find())
            return "";
        return extractBalancedBlock(json, m.end() - 1, '{', '}');
    }

    private List<String> extractJsonArrayObjects(String json, String arrayKey) {
        List<String> list = new ArrayList<>();
        if (json == null || json.isEmpty() || arrayKey == null)
            return list;

        Pattern p = ARR_PATTERN_CACHE.computeIfAbsent(arrayKey,
                k -> Pattern.compile("\"" + Pattern.quote(k) + "\"\\s*:\\s*\\["));
        Matcher m = p.matcher(json);
        if (!m.find())
            return list;

        int arrayOpenIdx = m.end() - 1;
        String arrayBlock = extractBalancedBlock(json, arrayOpenIdx, '[', ']');
        if (arrayBlock.length() <= 2)
            return list;

        boolean inQuotes = false;
        boolean escaped = false;
        int braceDepth = 0;
        int objStart = -1;

        for (int i = 1; i < arrayBlock.length() - 1; i++) {
            char c = arrayBlock.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inQuotes) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes)
                continue;

            if (c == '{') {
                if (braceDepth == 0)
                    objStart = i;
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && objStart >= 0) {
                    list.add(arrayBlock.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
        return list;
    }

    private String extractBalancedBlock(String text, int openIdx, char openChar, char closeChar) {
        if (text == null || openIdx < 0 || openIdx >= text.length() || text.charAt(openIdx) != openChar) {
            return "";
        }
        boolean inQuotes = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inQuotes) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes)
                continue;

            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return text.substring(openIdx, i + 1);
                }
            }
        }
        return "";
    }

    private String extractStringField(String text, String fieldName, String defaultValue) {
        if (text == null || text.isEmpty())
            return defaultValue;
        Pattern p = STR_PATTERN_CACHE.computeIfAbsent(fieldName,
                k -> Pattern.compile("\"" + Pattern.quote(k) + "\"\\s*:\\s*\"([^\"]*)\""));
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }

    private int extractIntField(String text, String fieldName, int defaultValue) {
        if (text == null || text.isEmpty())
            return defaultValue;
        Pattern p = INT_PATTERN_CACHE.computeIfAbsent(fieldName,
                k -> Pattern.compile("\"" + Pattern.quote(k) + "\"\\s*:\\s*(\\d+)"));
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private int extractSignedIntField(String text, String fieldName, int defaultValue) {
        if (text == null || text.isEmpty())
            return defaultValue;
        Pattern p = SINT_PATTERN_CACHE.computeIfAbsent(fieldName,
                k -> Pattern.compile("\"" + Pattern.quote(k) + "\"\\s*:\\s*(-?\\d+)"));
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
