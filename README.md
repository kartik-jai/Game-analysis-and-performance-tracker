# Valorant Game Analytics & Performance Tracker
**3rd-Year B.Tech Computer Science — Modular Java Desktop Project**

A desktop application built in **Java (Swing + Graphics2D + JDBC SQL + Java 11 `HttpClient`)** that tracks, persists, and visualizes **Valorant** competitive gaming match statistics (Match Win Rate, Round Win Rate, K/D Ratio, Average Combat Score (ACS), Agent Performance, and Headshot/Bodyshot/Legshot weapon accuracy).

---

## 1. Where to Get Your Valorant API Key (Step-by-Step)

### Why HenrikDev API instead of Riot's Official Valorant API?
Riot Games locks their official `VAL-MATCH-V1` endpoint behind a **Production API Key** that requires a registered company and legal business verification (their free Personal Development Key only works for League of Legends / TFT, **not** Valorant match history).

Therefore, almost all student and open-source Valorant trackers use the **HenrikDev Valorant API (`https://api.henrikdev.xyz`)**, which provides **free instant API keys** for students:

1. Visit **[https://docs.henrikdev.xyz](https://docs.henrikdev.xyz)** or join the official **HenrikDev Systems Discord**: **[https://discord.gg/henrikdev-systems](https://discord.gg/henrikdev-systems)**
2. Open the **`#get-a-key`** channel in Discord, click **Generate Key**, and select the free **Basic Key**.
3. You will receive a key starting with `HDEV-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.
4. **Where to paste the API key in this project** (choose either way):
   - **Inside the Desktop App GUI**: Click the gold **"API Key Setup & Guide"** button in the top bar and paste your `HDEV-...` key.
   - **In `config.properties`**: Open `config.properties` and set:
     ```properties
     henrik.api.key=HDEV-your-api-key-here
     ```
   - **Offline / College Lab Demo Mode (No Key Needed!)**: Even without an API key or internet connection, the app works immediately out of the box using **`LocalLogApiService`** (`data/sample_valorant_logs.txt`) and the **`+ Simulate Match`** button!

---

## 2. Clean Modular Package Architecture

```text
valorant tracker/
├── config.properties                    # Stores API Key, Default Riot ID (Name#Tag), Region, DB URL
├── pom.xml                              # Maven configuration (with sqlite-jdbc dependency)
├── run.bat                              # One-click Windows compiler & launcher script
├── data/
│   └── sample_valorant_logs.txt         # Local Valorant match log file for offline parsing
└── src/
    ├── model/                           # [DATA MODEL PACKAGE]
    │   ├── MatchStats.java              # Encapsulates a single Valorant match & computed KPIs
    │   ├── AgentPerformance.java        # Aggregated stats per Agent (Jett, Omen, Reyna, Sova...)
    │   └── PerformanceSummary.java      # Overall summary metrics + coaching insights
    │
    ├── service/                         # [BUSINESS LOGIC & API INTERFACE PACKAGE]
    │   ├── GameApiClient.java           # Interface defining contract for any game data source
    │   ├── HenrikValorantApiService.java# Live HTTP client for HenrikDev Valorant API v3
    │   ├── LocalLogApiService.java      # Reads local Valorant match logs & simulates matches
    │   └── AnalyticsService.java        # Calculates win rates, accuracy %, trends & coaching tips
    │
    ├── utility/                         # [DATABASE, CONFIG & EXCEPTION PACKAGE]
    │   ├── DatabaseManager.java         # JDBC SQLite (CREATE, INSERT, SELECT, DELETE) + fallback
    │   ├── ConfigManager.java           # Manages config.properties
    │   └── ApiException.java            # Custom checked exception with HTTP status & tips
    │
    └── main/                            # [PRESENTATION / GUI & ENTRY POINT PACKAGE]
        ├── ValorantTrackerApp.java      # Dark Valorant-themed Swing Desktop GUI + CLI mode
        └── TrendChartPanel.java         # Custom Graphics2D Trend Line & Bar Charts
```

---

## 3. How to Compile & Run

### Option A: One-Click Run on Windows (Pure JDK — Zero External Setup Needed)
Double-click `run.bat` or run in PowerShell / Command Prompt:
```powershell
.\run.bat
```

### Option B: Manual `javac` & `java` Commands
```powershell
mkdir out
javac -d out src/model/*.java src/utility/*.java src/service/*.java src/main/*.java

# Launch Desktop GUI:
java -cp out main.ValorantTrackerApp

# Or run CLI / Terminal Analytics Report:
java -cp out main.ValorantTrackerApp --cli
```

### Option C: Run with Maven (Includes Native SQLite JDBC Driver)
```powershell
mvn compile exec:java
```

---

## 4. Key OOP & Java Concepts Demonstrated (For B.Tech Viva Voce)

1. **Interfaces & Polymorphism (`service.GameApiClient`)**:
   - `ValorantTrackerApp` uses a polymorphic reference `GameApiClient selectedApiClient;` which switches at runtime between `HenrikValorantApiService` (REST API) and `LocalLogApiService` (File I/O).
2. **Encapsulation (`model.MatchStats`)**:
   - Private fields with getters/setters and domain calculation methods (`getKdRatio()`, `getHeadshotPercentage()`, `getRoundWinRate()`).
3. **Custom Exception Handling (`utility.ApiException`)**:
   - Catches `HttpTimeoutException`, `ConnectException`, and HTTP `401 / 403 / 404 / 429` errors and converts them into meaningful user guidance without crashing the GUI.
4. **SQL Persistence with JDBC (`utility.DatabaseManager`)**:
   - Uses parameterized `PreparedStatement` queries (`INSERT OR REPLACE INTO valorant_matches VALUES (?, ?, ...)`) to prevent SQL injection, plus an automatic table backup (`data/matches_sql_table.csv`) so the program works even on lab PCs without external JDBC JARs.
5. **Custom 2D Data Visualization (`main.TrendChartPanel`)**:
   - Uses `java.awt.Graphics2D` with anti-aliasing to plot K/D Ratio trend lines, Headshot % trend lines, Round Win Rate bar charts, and Weapon Shot Accuracy distributions.
