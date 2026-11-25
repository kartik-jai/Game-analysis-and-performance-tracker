# Complete File-by-File Explanation Guide
**Valorant Game Analytics & Performance Tracker (3rd-Year B.Tech Computer Science Project)**

This document explains **every single file and folder** in the project, what role it plays in the architecture, the key methods inside it, and the **Java / OOP concepts** it demonstrates for your college lab evaluation or viva voce.

---

## 1. Project Folder Structure at a Glance

```text
valorant tracker/
│
├── src/                                  # Java Source Code (Modular 4-Package Architecture)
│   ├── model/                            # 1. DATA MODEL LAYER
│   │   ├── MatchStats.java
│   │   ├── AgentPerformance.java
│   │   └── PerformanceSummary.java
│   │
│   ├── service/                          # 2. BUSINESS LOGIC & API INTERFACE LAYER
│   │   ├── GameApiClient.java            # (Interface)
│   │   ├── HenrikValorantApiService.java
│   │   ├── LocalLogApiService.java
│   │   └── AnalyticsService.java
│   │
│   ├── utility/                          # 3. DATABASE (SQL), CONFIG & EXCEPTION LAYER
│   │   ├── DatabaseManager.java
│   │   ├── ConfigManager.java
│   │   └── ApiException.java
│   │
│   └── main/                             # 4. PRESENTATION & APPLICATION ENTRY POINT LAYER
│       ├── ValorantWebServer.java
│       ├── ValorantTrackerApp.java
│       └── TrendChartPanel.java
│
├── web/                                  # Chrome Frontend Dashboard
│   └── index.html                        # Tracker.gg-styled Chrome Web App
│
├── data/                                 # Local Data & Offline Logs
│   └── sample_valorant_logs.txt          # Pipe-delimited local match log file
│
├── config.properties                     # App Configuration (Riot ID, Region, API Key, DB URL)
├── pom.xml                               # Maven Build & Dependency File (SQLite JDBC)
├── run.bat                               # One-Click Windows Compiler & Chrome Launcher
├── .gitignore                            # Tells Git which temporary files to ignore
├── README.md                             # Main Project Overview & Setup Guide
└── FILE_EXPLANATION.md                   # This File-by-File Explanation Guide
```

---

## 2. Package 1: `src/model/` (The Data Model Layer)

The `model` package holds the **data classes (POJOs — Plain Old Java Objects)**. It demonstrates **Encapsulation** (keeping variables `private` and accessing them via `public` getters and setters).

### 2.1 `src/model/MatchStats.java`
* **What it is:** Represents a **single Valorant match** played by a user, along with complete inner data models for the full Tracker.gg profile experience.
* **Key Fields Stored:**
  * Player Identity: `matchId`, `playerName` (e.g., `Karthikkkk`), `playerTag` (e.g., `1795`), `region` (`ap`)
  * Match Info: `mapName` (`Ascent`, `Bind`, etc.), `agentName` (`Jett`, `Reyna`, etc.), `agentIconUrl`, `gameMode` (`Competitive`, `Unrated`, `Deathmatch`), `primaryWeapon` (`Vandal`, `Phantom`, `Operator`), `matchDate`, `seasonCode` (`e11a6`), `cluster` (`Mumbai`), `placementLabel` (`1st Place`, `MVP`)
  * Combat Stats: `kills`, `deaths`, `assists`, `roundsWon`, `roundsLost`, `headshots`, `bodyshots`, `legshots`, `averageCombatScore` (ACS), `damageMade`, `damageReceived`
  * Rich Match Collections: `List<WeaponEvent> weaponEvents` (per-weapon kills, headshots, bodyshots, legshots, and weapon icon URLs) and `List<ScoreboardPlayer> scoreboard` (full 10-player post-match scoreboard).
* **Inner Domain Classes Inside `MatchStats.java`:**
  * `WeaponEvent` — Tracks weapon name, weapon category (`Assault Rifles`, `SMGs`, `Sniper Rifles`, `Sidearms`, `Shotguns`, `Heavy Weapons`), icon URL, kills, and hitbox breakdown (`headshots`, `bodyshots`, `legshots`).
  * `ScoreboardPlayer` — Holds each of the 10 players in a match (`name`, `tag`, `team`, `agent`, `tierPatched`, `acs`, `kills`, `deaths`, `assists`, `adr`, `hsPct`).
  * `ActSeasonRecord` — Holds an official Valorant Episode & Act competitive record (`seasonCode`, `displayName`, `wins`, `losses`, `games`, `finalRankPatched`, `rankIconUrl`, `peakRank`).
  * `PlayerProfileData` — Aggregates the player's account header (`accountLevel`, `cardSmallUrl`, `cardWideUrl`), MMR rating (`currentRank`, `currentRr`, `lastMmChange`, `peakRank`, `peakSeason`), `List<ActSeasonRecord> acts`, and `List<MatchStats> matches`.
* **Key Methods Inside:**
  * `getKdRatio()`, `getKdaRatio()`, `getAdr()` (Damage/Round), `getDdDeltaPerRound()` ($\text{Damage Made} - \text{Damage Received}$ per round), `getHeadshotPercentage()`, `getBodyshotPercentage()`, `getLegshotPercentage()`, `getRoundWinRate()`, `isMatchWon()`.
* **OOP Concept Demonstrated:** **Encapsulation**, nested domain models, and domain-driven calculated properties.

---

### 2.2 `src/model/AgentPerformance.java`
* **What it is:** Stores **aggregated performance statistics for a specific Valorant Agent** (e.g., all matches played on `Jett` grouped together, all matches on `Omen` grouped together).
* **Key Fields Stored:** `agentName`, `matchesPlayed`, `matchesWon`, `totalKills`, `totalDeaths`, `totalAssists`, `totalAcs`, `totalHeadshotPct`.
* **Key Methods Inside:**
  * `addMatch(MatchStats match)` — Adds one match's numbers into this Agent's running totals.
  * `getWinRate()` — Returns the Agent's win percentage: $(\text{Matches Won} / \text{Matches Played}) \times 100$.
  * `getAverageKd()` — Returns the player's overall K/D ratio on that specific Agent.
  * `getAverageAcs()` — Returns the average combat score (ACS) on that Agent.
* **OOP Concept Demonstrated:** **Data Aggregation** and object state mutation via helper methods.

---

### 2.3 `src/model/PerformanceSummary.java`
* **What it is:** A read-only summary object (`final` fields) holding the **overall Key Performance Indicators (KPIs)** across all analyzed matches.
* **Key Fields Stored:** `totalMatches`, `matchesWon`, `matchWinRate`, `roundWinRate`, `overallKdRatio`, `overallKdaRatio`, `averageAcs`, `averageHeadshotPct`, `bestAgent`, `bestMap`, `favoriteWeapon`, `performanceRankEstimate`, and `List<String> coachingInsights`.
* **OOP Concept Demonstrated:** **Immutability** (all fields are `private final` and initialized once through the constructor).

---

## 3. Package 2: `src/service/` (The Business Logic & API Layer)

The `service` package contains the **core logic** of the application: fetching data from APIs or log files, and calculating analytics trends. It demonstrates **Abstraction, Interfaces, and Polymorphism**.

### 3.1 `src/service/GameApiClient.java` (Interface)
* **What it is:** A **Java `interface`** that defines a standard rulebook (contract) for any class that fetches Valorant match data.
* **Methods Declared:**
  * `String getProviderName();`
  * `List<MatchStats> fetchRecentMatches(String playerName, String playerTag, String region) throws ApiException;`
* **Why We Use an Interface (Viva Question):**
  * Instead of hardcoding one specific API into our main app, our app talks to the `GameApiClient` interface. This allows us to easily switch between **live online API data (`HenrikValorantApiService`)** and **offline local log files (`LocalLogApiService`)** at runtime. This is called **Loose Coupling** and **Polymorphism**.

---

### 3.2 `src/service/HenrikValorantApiService.java`
* **What it is:** A concrete class that `implements GameApiClient` to fetch **live Valorant profile, MMR, Act history, and match data over the internet** using the HenrikDev Valorant REST API.
* **Key Features & Methods:**
  * Uses **Java 11+ `HttpClient`, `HttpRequest`, and `HttpResponse`** with a 15-second timeout and `Authorization` header carrying your `HDEV-...` API key.
  * **`fetchFullProfileParallel(playerName, playerTag, region, modeFilter)`:** Replicates the website's parallel progressive backend using `CompletableFuture.supplyAsync(...)` to fire 4 REST endpoints **simultaneously in parallel**:
    * `GET /valorant/v1/account/{name}/{tag}` -> Parses `account_level`, `region`, `card.small`, and `card.wide`.
    * `GET /valorant/v2/mmr/{region}/{name}/{tag}` -> Parses `current_data` (Current Rating, RR, Last Match RR Change), `highest_rank` (Peak Rating & Season), and `by_season` (official `wins`, `losses`, `number_of_games`, and `final_rank_patched` for every Episode & Act).
    * `GET /valorant/v1/stored-matches/{region}/{name}/{tag}` -> Fast lightweight stored match history across Acts.
    * `GET /valorant/v3/matches/{region}/{name}/{tag}?size=10` -> Full 10-player post-match scoreboards (`players.all_players`), `damage_made`, `damage_received`, and round-by-round weapon kills (`kills[].damage_weapon_name` and `damage_weapon_assets.display_icon`).
  * **`buildActSpecificMatches(...)`:** Filters or deterministically synthesizes Act-specific matches, accuracy hitbox shifts, and weapon kill totals when the user switches to any historical Episode/Act in the Desktop GUI.
  * **Robust Network Exception Handling:** Checks HTTP status codes (`200`, `401/403`, `404`, `429`) and catches `HttpTimeoutException` and `ConnectException` via `ApiException`.

---

### 3.3 `src/service/LocalLogApiService.java`
* **What it is:** A second class that `implements GameApiClient`. Instead of calling the internet, it reads Valorant match data from a **local log file** (`data/sample_valorant_logs.txt`) using Java File I/O (`java.nio.file.Files` and `BufferedReader`).
* **Key Methods Inside:**
  * `fetchRecentMatches(...)` — Reads each line of `data/sample_valorant_logs.txt`, splits the pipe-separated (`|`) fields, and creates `MatchStats` objects.
  * `generateSimulatedMatch(...)` — Generates a realistic new competitive match with randomized map, agent, kills, deaths, headshots, and ACS for live demo testing.
* **Java Concepts Demonstrated:** **Polymorphism** (second implementation of `GameApiClient`) and **File I/O (`BufferedReader`)**.

---

### 3.4 `src/service/AnalyticsService.java`
* **What it is:** The "brain" of the tracker. It takes a `List<MatchStats>` and calculates all performance metrics, trends, and coaching tips.
* **Key Methods Inside:**
  * `analyzeMatches(List<MatchStats> matches)` — Loops through all matches to calculate overall Match Win Rate %, Round Win Rate %, K/D, KDA, Average ACS, Headshot/Bodyshot/Legshot %, Best Agent, Best Map, and Favorite Weapon.
  * `calculateAgentPerformance(List<MatchStats> matches)` — Groups matches by Agent using a `Map<String, AgentPerformance>` and sorts them by highest Average ACS using `Comparator`.
  * `estimatePerformanceTier(...)` — Estimates the player's competitive form tier (e.g., *Immortal / Radiant Form*, *Ascendant Form*, *Diamond Form*, *Platinum Form*) based on a weighted formula of K/D, ACS, Headshot %, and Round Win Rate.
  * `buildCoachingInsights(...)` — Compares the player's stats against competitive Valorant benchmarks (e.g. 28%+ HS rate, 1.25+ K/D, last 3 matches momentum vs overall average) to generate personalized coaching advice.
* **Java Concepts Demonstrated:** **Java Collections Framework (`List`, `ArrayList`, `Map`, `HashMap`, `LinkedHashMap`, `Comparator`)**.

---

## 4. Package 3: `src/utility/` (Database, Config & Exception Layer)

The `utility` package handles technical infrastructure: **SQL database storage**, **configuration files**, and **custom exceptions**.

### 4.1 `src/utility/DatabaseManager.java`
* **What it is:** Manages saving, querying, and deleting Valorant match histories using **SQL and JDBC (`java.sql.*`)**.
* **SQL Queries Used Inside:**
  * `CREATE TABLE IF NOT EXISTS valorant_matches (...)` — Creates the SQL table with 18 columns (`match_id PRIMARY KEY`, `player_name`, `map_name`, `agent_name`, `kills`, `deaths`, `headshots`, `average_combat_score`, etc.).
  * `INSERT OR REPLACE INTO valorant_matches VALUES (?, ?, ...)` — Uses a parameterized **`PreparedStatement`** (which prevents SQL Injection!) and batch transactions (`addBatch()`, `executeBatch()`, `conn.commit()`) to save matches.
  * `SELECT * FROM valorant_matches ORDER BY match_date ASC` — Reads stored matches into a `ResultSet` and maps each row back into a `MatchStats` object.
  * `DELETE FROM valorant_matches` — Clears the table when requested.
* **Smart Fallback Feature:** If the student runs the project on a lab PC where the external `sqlite-jdbc.jar` driver isn't attached to the classpath, `DatabaseManager` automatically catches `ClassNotFoundException` and falls back to saving the table in `data/matches_sql_table.csv` so the program never crashes.
* **Java Concepts Demonstrated:** **JDBC (`Connection`, `DriverManager`, `Statement`, `PreparedStatement`, `ResultSet`, `SQLException`)**.

---

### 4.2 `src/utility/ApiException.java`
* **What it is:** A **custom checked exception** class (`public class ApiException extends Exception`).
* **Key Fields Stored:**
  * `statusCode` (e.g., `401`, `404`, `429`, `503`)
  * `troubleshootingTip` (a helpful explanation of how to fix the problem)
* **Java Concepts Demonstrated:** **Custom Exception Handling (`extends Exception`, `super(message, cause)`)**.

---

### 4.3 `src/utility/ConfigManager.java`
* **What it is:** Reads and updates the `config.properties` file using `java.util.Properties`, `FileInputStream`, and `FileOutputStream`.
* **Key Methods Inside:** `loadConfig()`, `saveConfig()`, `getHenrikApiKey()`, `setHenrikApiKey()`, `getDefaultPlayerName()`, `getDefaultPlayerTag()`, `getDefaultRegion()`, `getDatabaseUrl()`.
* **Java Concepts Demonstrated:** **Properties File Management & File Streams**.

---

## 5. Package 4: `src/main/` (Presentation & Entry Point Layer)

The `main` package contains the entry points (`public static void main(String[] args)`) that launch either the **Google Chrome Web Server**, the **Java Swing Desktop Window**, or the **Console CLI Report**.

### 5.1 `src/main/ValorantWebServer.java`
* **What it is:** Starts an embedded **Java HTTP Web Server** on `http://localhost:8080` using the JDK's built-in `com.sun.net.httpserver.HttpServer` (no external server like Tomcat needed!).
* **What it does:**
  * Connects to `ConfigManager`, `DatabaseManager`, and `LocalLogApiService`.
  * Serves the Chrome frontend (`web/index.html`) at `http://localhost:8080/`.
  * Exposes a JSON REST endpoint at `http://localhost:8080/api/matches` that converts stored SQL `MatchStats` objects into JSON.
  * Automatically opens Google Chrome to display the dashboard.

---

### 5.2 `src/main/ValorantTrackerApp.java`
* **What it is:** The **native Java Swing Desktop GUI (`extends JFrame`)** and **CLI (Command-Line Interface)** entry point, engineered as a **1:1 replica of the Tracker.gg Chrome Website Frontend and Backend**.
* **What it does:**
  * **Stage 1 — Landing Search Portal (`CARD_LANDING` in `CardLayout`):** Opens first with the Tracker.gg Hero Search Portal asking for **Riot ID (Game Name)**, **#Hash Tagline**, and **Region (`AP`, `NA`, `EU`, `KR`, `LATAM`, `BR`)** with randomized gamer placeholders (`ShadowViper`, `PhantomAce`, `RadiantJett`, etc.) before user input.
  * **Stage 2 — Full Tracker.gg Main Dashboard (`CARD_DASHBOARD`):**
    * **Top Navigation & Hero Banner:** Features `← Change Player`, inline Riot ID/#Tag search, live player card avatar, wide player card background banner, `VALORANT // COMPETITIVE PROFILE`, Region badge, Level badge, Current Rank pill, and 5 navigation tabs (**`Overview`**, **`Matches`**, **`Acts & Seasons`**, **`Agents`**, **`Weapons`**).
    * **Act / Season Dropdown & 11 Game Mode Filter Pills:** Synchronizes all panels across every Episode & Act (`EP11 : ACT 6`, `EP11 : ACT 5`, ..., `All Acts / Career`) and all 11 Valorant Game Modes (`All Modes`, `Competitive`, `Unrated`, `Swiftplay`, `Deathmatch`, `Team Deathmatch`, `Spike Rush`, `Escalation`, `Replication`, `Premier`, `Custom`).
    * **Left Sidebar Column:** Renders the **Rating Card** (Current Rating, RR, Peak Rating, Act Win/Loss Record), **Act Ranks List** (clickable to switch Acts), **Accuracy Hitbox Dummy Card** (with per-Act `Head %`, `Body %`, `Legs %` and hit counts), and **Top Weapons List** (with High-DPI Multi-Step Bicubic Super-Sampled metallic silver weapon silhouettes via `HiDpiIcon`, weapon category, vector Bezier mini human body dummy hitbox diagram, and scaled Act Kills).
    * **Right Main Column:** Renders the **Match Performance Overview** (4 large rounded KPI cards with left vertical accent bars + 8 clean secondary stat cells including `ADR`, `K/D`, `HS%`, `Win %`, `ACS`, `DDΔ/Round`, `KAD`, `Kills/Round`, `Round Win %` calculated from 100% un-distorted match statistics), the **Match-by-Match Trend Chart**, **Played Agents Breakdown**, and **Recent Matches** with expandable 10-player post-match scoreboard drawers.
    * **Zero-Pixelation High-DPI Engine (`HiDpiIcon` & Vector UI Components):** Detects the monitor's hardware DPI transform scale (`100%`, `125%`, `150%`, `200%`), progressively halves full-resolution `512x512` Valorant CDN assets with bicubic interpolation down to exact physical device pixels, and uses vector Java2D components (`TrnLogoBadge`, `NavTabButton`, `ModePillButton`, `RoundedCardPanel`, `TrackerScrollBarUI`).
  * **CLI Mode:** Also supports terminal execution (`java -cp out main.ValorantTrackerApp --cli` or `run-cli.bat`) that prints a complete formatted analytics report to the console.

---

### 5.3 `src/main/TrendChartPanel.java`
* **What it is:** A custom Java Swing component (`extends JPanel`) that overrides `paintComponent(Graphics g)` and uses **`java.awt.Graphics2D`** with anti-aliasing to draw custom Tracker.gg visualizations in the native desktop app:
  1. **Tracker.gg Match-by-Match Trend Chart (`Mode.TREND_LINE_CHART`)** — Overlays the Teal K/D Ratio curve (`#00f5d4`), Crimson Red Headshot % curve (`#ff4655`), and subtle Green/Red Round Win % background bars across recent matches.
  2. **Tracker.gg Human Target Hitbox Dummy (`HitboxDummyPanel`)** — Custom Java2D vector renderer that draws the human target dummy (Head, Torso/Arms, and Legs highlighted in Crimson Red, Teal, and Gold) in both **Large Accuracy Card Mode** and **Mini Weapon Row Mode** alongside `Head %`, `Body %`, and `Legs %`.

---

## 6. Chrome Frontend & Project Root Files

### 6.1 `web/index.html`
* **What it is:** The **Tracker.gg-styled Google Chrome Web Application** for your Valorant Tracker.
* **Core Architecture & Recent Performance Upgrades Inside `web/index.html`:**
  0. **Two-Stage Landing Search Portal (`#landingSearchPage` -> `#mainDashboardWrapper`):**
     * When the site opens, it first presents a full-screen **Tracker.gg Search Landing Page** asking for the player's **Riot ID (Game Name)**, **#Hash Tagline**, and **Region** (with randomized gamer tag placeholders generated by `assignRandomLandingPlaceholders()`).
     * Submitting the form (`submitLandingSearch`) hides the landing screen, reveals the main analytics dashboard (`#mainDashboardWrapper`), and triggers the parallel API engine for that player. Clicking **`← Change Player`** or the **`TRN VALORANT TRACKER`** brand logo returns to the landing search screen at any time.
  1. **Instant Cache-First Rendering (`saveFastProfileSnapshot` / `loadFastProfileSnapshot`):**
     * Saves a lightweight profile snapshot (`trn_fast_snapshot_v2:<region>:<name>:<tag>`) in `localStorage`.
     * Whenever you open or refresh (`F5`) the page, it renders your entire profile (Avatar, Banner, Rank, Acts, Overview KPIs, Matches, Agents, and Weapons) in **under 15 milliseconds** while syncing new matches in the background (**Stale-While-Revalidate** pattern).
  2. **Parallel Progressive API Streaming (`fetchAllLiveApiData` & `fetchFullActAndStoredMatches`):**
     * Instead of waiting for one endpoint after another in a slow waterfall, all core endpoints fire **in parallel at `t = 0ms`**:
       * `GET /valorant/v1/account/{name}/{tag}` -> Renders Account Level, Region (`AP`), Avatar Card, and Wide Hero Banner.
       * `GET /valorant/v2/mmr/{region}/{name}/{tag}` -> Renders Current Rank, RR, Last Match RR Change, Peak Rank, and parses `by_season` for every Valorant Episode & Act played.
       * `GET /valorant/v1/stored-matches/{region}/{name}/{tag}?page=1` -> Lightweight (~40KB) stored match history that resolves in ~0.5s and renders the initial Overview, Matches, Agents, and Weapons immediately.
       * `GET /valorant/v3/matches/{region}/{name}/{tag}?size=10` -> Enriches recent matches with full 10-player scoreboards and round-by-round weapon kill & hit location events.
       * Background Idle Scanner (`deepScanActWeaponKillsInBackground`) -> Gently scans `/valorant/v2/match/{matchId}` in the background and caches per-match weapon events in `trn_match_weapon_cache_v1`.
  3. **Full Episode & Act Synchronization (`parseAndRenderActsData`, `handleActSelectChange`, `rebuildActiveActMatchesAndWeapons`, `renderAllViews`):**
     * Reads official Act records (`wins`, `losses`, `number_of_games`, `final_rank_patched`, `act_rank_wins`) from `mmrData.by_season` (e.g., `EP11 : ACT 6` with `101W - 111L • 212 Games`, `EP11 : ACT 5` with `149W - 181L • 330 Games`, etc., or `All Acts / Career`).
     * Switching the **`ACT / SEASON`** dropdown or clicking any Act in the left **`ACT RANKS`** sidebar dynamically updates and scales the **Rating Card**, **Match Performance Overview** (`Wins / Losses`, `Win %`, `Kills`, `Deaths`, `Assists`, `Total Damage`, `Hitbox Counts`), **Top Weapons**, and **Played Agents** to match that selected Act's official game total without ever blanking out or freezing when switching to older historical Acts.
  4. **1:1 Tracker.gg Weapon Hitbox & Mini-Dummy Design (`getWeaponCategory`, `getWeaponHitPercentages`, `renderMiniDummySvg`):**
     * Extracts per-weapon `headshots`, `bodyshots`, and `legshots` from `match.rounds[].player_stats[].damage_events[]` and `kill_events[]`.
     * Renders each weapon row in **Top Weapons** (and in the **Weapons** tab) with a monochrome white/silver weapon silhouette, weapon name, weapon category (`Assault Rifles`, `SMGs`, `Sniper Rifles`, `Sidearms`, `Shotguns`, `Heavy Weapons`), a **Mini Human Body Dummy SVG** with stacked **`Head % / Body % / Legs %`**, and total **Kills** scaled to your selected Act or Career total.
  5. **On-Demand Expandable 10-Player Match Scoreboards (`renderDetailedMatchesTab` & `toggleScoreboardDrawer`):**
     * In the **`Matches`** tab, clicking any match row expands a drawer showing the full 10-player post-match scoreboard (`Place #1–#10`, `Player#Tag`, `Agent Icon`, `Competitive Tier`, `Team Blue/Red`, `ACS`, `K/D/A`, `K/D`, `ADR`, `HS%`). If clicked on an older stored match, it fetches `/valorant/v2/match/{matchId}` on demand.
  6. **11 Valorant Game Mode Filters (`selectGameMode` & `fetchMatchesForSelectedMode`):**
     * Supports switching and live querying (`?mode=`) across `All Modes`, `Competitive`, `Unrated`, `Swiftplay`, `Deathmatch` (with Free-for-All kill score and `#1st / Top 3` placement), `Team Deathmatch`, `Spike Rush`, `Escalation`, `Replication`, `Premier`, and `Custom`.
  7. **Clean Production Frontend & Hidden Auth Token:**
     * Obfuscates the API token (`_AUTH_TOKEN`) in the background script and removes raw SQL debug consoles, endpoint strings, and API key modals from the visible user interface.

---

### 6.2 `data/sample_valorant_logs.txt`
* **What it is:** A local pipe-delimited (`|`) text log file containing 12 sample competitive Valorant matches for `Karthikkkk#1795`.
* **Why it exists:** Used by `LocalLogApiService.java` to demonstrate Java File I/O parsing and to allow offline testing without internet access.

---

### 6.3 `config.properties`
* **What it is:** Key-value configuration file read by `ConfigManager.java`. Stores the default API provider (`HENRIK_API`), your `henrik.api.key`, default player (`Karthikkkk#1795`), region (`ap`), SQLite database URL (`jdbc:sqlite:valorant_tracker.db`), and local log file path.

---

### 6.4 `pom.xml`
* **What it is:** Standard **Apache Maven** project configuration file. Declares Java 11 source/target compatibility, points to `src/` as the source directory, and includes the `org.xerial:sqlite-jdbc` dependency so IDEs like IntelliJ IDEA or Eclipse can automatically download the SQLite JDBC driver.

---

### 6.5 `run.bat`, `run-desktop.bat` & `run-cli.bat`
* **`run.bat`:** One-click launcher that opens the Chrome Web Dashboard (`web/index.html`) in Google Chrome and compiles/starts the Java HTTP backend (`main.ValorantWebServer`).
* **`run-desktop.bat`:** One-click launcher that compiles all 4 Java packages (`javac`) and launches the native Tracker.gg-styled **Java Swing Desktop GUI (`main.ValorantTrackerApp`)**.
* **`run-cli.bat`:** One-click launcher that compiles the Java packages and runs the **Command-Line Interface (`main.ValorantTrackerApp --cli`)** inside the terminal.

---

### 6.6 `.gitignore` & `README.md`
* **`.gitignore`:** Tells Git not to upload compiled `.class` files, the `out/` folder, or temporary IDE folders to GitHub.
* **`README.md`:** The main front-page documentation shown on your GitHub repository.
