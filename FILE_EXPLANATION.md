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
* **What it is:** Represents a **single Valorant match** played by a user.
* **Key Fields Stored:**
  * Player Identity: `matchId`, `playerName` (e.g., `Karthikkkk`), `playerTag` (e.g., `1795`), `region` (`ap`)
  * Match Info: `mapName` (`Ascent`, `Bind`, etc.), `agentName` (`Jett`, `Reyna`, etc.), `gameMode` (`Competitive`, `Unrated`, `Deathmatch`), `primaryWeapon` (`Vandal`, `Phantom`, `Operator`), `matchDate`
  * Combat Stats: `kills`, `deaths`, `assists`, `roundsWon`, `roundsLost`, `headshots`, `bodyshots`, `legshots`, `averageCombatScore` (ACS)
* **Key Methods Inside:**
  * `getKdRatio()` — Calculates $\text{Kills} / \text{Deaths}$ (safely handles `deaths == 0` so the program never crashes with a `DivideByZero` error).
  * `getKdaRatio()` — Calculates $(\text{Kills} + \text{Assists}) / \text{Deaths}$.
  * `getHeadshotPercentage()`, `getBodyshotPercentage()`, `getLegshotPercentage()` — Calculates what percentage of total shots hit the head, body, or legs.
  * `getRoundWinRate()` — Calculates $(\text{Rounds Won} / \text{Total Rounds}) \times 100$.
  * `isMatchWon()` — Returns `true` if `roundsWon > roundsLost`.
* **OOP Concept Demonstrated:** **Encapsulation** and domain-driven calculated properties.

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
* **What it is:** A concrete class that `implements GameApiClient` to fetch **live Valorant match history over the internet** using the HenrikDev Valorant REST API (`https://api.henrikdev.xyz/valorant/v3/matches/{region}/{name}/{tag}`).
* **Key Features & Methods:**
  * Uses **Java 11+ `HttpClient`, `HttpRequest`, and `HttpResponse`** with a 15-second timeout and `Authorization` header carrying your `HDEV-...` API key.
  * **Robust Network Exception Handling:** Checks HTTP status codes:
    * `200 OK` -> Parses the JSON response into a `List<MatchStats>`.
    * `401 / 403` -> Throws `ApiException` for an invalid or missing API key.
    * `404` -> Throws `ApiException` if the Riot ID or Region is not found.
    * `429` -> Throws `ApiException` if the API rate limit is exceeded.
    * Also catches `HttpTimeoutException` and `ConnectException` (when internet is disconnected).
  * `parseHenrikV3Json(...)` — Extracts match metadata, player kills/deaths/assists, headshots/bodyshots/legshots, and round scores from the JSON response without requiring third-party JSON JAR libraries.

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
* **What it is:** The **native Java Swing Desktop GUI (`extends JFrame`)** and **CLI (Command-Line Interface)** entry point.
* **What it does:**
  * Builds a Valorant-themed dark desktop interface with KPI cards, a Riot ID input bar, an API provider switcher dropdown, a `JTabbedPane` with 3 tabs, and styled `JTable` views for SQL match history and Agent performance.
  * Also supports a terminal mode (`java -cp out main.ValorantTrackerApp --cli`) that prints a complete formatted analytics report to the console.

---

### 5.3 `src/main/TrendChartPanel.java`
* **What it is:** A custom Java Swing component (`extends JPanel`) that overrides `paintComponent(Graphics g)` and uses **`java.awt.Graphics2D`** with anti-aliasing to draw custom charts in the native desktop app:
  1. K/D Ratio & Headshot % Multi-Line Trend Graph
  2. Round Win Rate % Bar Chart (green for wins, crimson red for losses)
  3. Horizontal Stacked Weapon Hit Accuracy Bar (Headshots vs Bodyshots vs Legshots)

---

## 6. Chrome Frontend & Project Root Files

### 6.1 `web/index.html`
* **What it is:** The **Tracker.gg-styled Google Chrome Web Application** for your Valorant Tracker.
* **Key Features Inside:**
  * **Live API Integration:** Connects directly to the HenrikDev Valorant endpoints (`/v1/account`, `/v2/mmr`, `/v1/stored-matches`, `/v3/matches`, and `/v2/match/{id}`) using your configured API key.
  * **5 Interactive Tabs:**
    1. **`Overview`**: Left sidebar with Competitive Rating, Act Ranks history, Hitbox Dummy Silhouette (`Head %`, `Body %`, `Legs %`), and Top Weapons (with monochrome weapon silhouettes & mini body hit dummies); Right column with Giant 4 + 8 Overview KPIs, HTML5 `<canvas>` Trend Graph, Played Agents strip, and Recent Matches feed.
    2. **`Matches`**: Match Record & W/L Streak pills, Match Averages, Peak Match Performance card, and expandable match rows that open the **full 10-player Post-Match Scoreboard**.
    3. **`Acts & Seasons`**: Tracks every Valorant Episode & Act played (`by_season`), Career Competitive Record across all Acts, Peak Rating, and Act-by-Act progression cards.
    4. **`Agents`**: Full table comparing Win Rate %, Kills, Deaths, Assists, K/D, ADR, ACS, and HS% across every Agent played.
    5. **`Weapons`**: Signature Weapon Hero card, Total Arsenal Kills, Hip-Fire vs ADS breakdown, Weapon Showcase Cards with mini hitbox dummies, and a Detailed Weapon Analytics table.
  * **11 Game Mode Filters & Act Selector:** Allows filtering and fetching live data across `All Modes`, `Competitive`, `Unrated`, `Swiftplay`, `Deathmatch`, `Team Deathmatch`, `Spike Rush`, `Escalation`, `Replication`, `Premier`, and `Custom`, as well as switching between `All Acts / Career` and individual Acts.

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

### 6.5 `run.bat`
* **What it is:** A one-click Windows batch script that opens the Chrome Web Dashboard (`web/index.html`) in Google Chrome and compiles/starts the Java backend (`javac` -> `java -cp out main.ValorantWebServer`).

---

### 6.6 `.gitignore` & `README.md`
* **`.gitignore`:** Tells Git not to upload compiled `.class` files, the `out/` folder, or temporary IDE folders to GitHub.
* **`README.md`:** The main front-page documentation shown on your GitHub repository.
