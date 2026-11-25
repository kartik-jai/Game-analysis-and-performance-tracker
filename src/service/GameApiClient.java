package service;

import model.MatchStats;
import utility.ApiException;

import java.util.List;

/**
 * GameApiClient (Service Layer - Interface)
 * -------------------------------------------------------------------------
 * Defines a common contract for fetching Valorant match data regardless of
 * the underlying data source (HenrikDev Public API, Riot Games Official API,
 * or Local Valorant Match Log Files).
 *
 * Educational Highlight for 3rd-Year B.Tech CS:
 * Demonstrates Abstraction, Loose Coupling, and Polymorphism in Java.
 * The GUI/Controller talks to this interface rather than hardcoding a single API.
 */
public interface GameApiClient {

    /**
     * Returns the display name of this API / Data Provider.
     */
    String getProviderName();

    /**
     * Fetches recent Valorant matches for a given Riot ID (playerName#playerTag) and region.
     *
     * @param playerName Riot ID Game Name (e.g., "TenZ")
     * @param playerTag  Riot ID Tag Line without '#' (e.g., "SEN")
     * @param region     Valorant Shard/Region ("ap", "na", "eu", "kr", "latam", "br")
     * @return List of parsed MatchStats objects
     * @throws ApiException if a network error, missing API key, rate limit, or 404 occurs
     */
    List<MatchStats> fetchRecentMatches(String playerName, String playerTag, String region) throws ApiException;
}
