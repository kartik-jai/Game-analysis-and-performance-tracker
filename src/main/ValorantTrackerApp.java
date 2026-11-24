package main;

import model.AgentPerformance;
import model.MatchStats;
import model.PerformanceSummary;
import service.AnalyticsService;
import service.GameApiClient;
import service.HenrikValorantApiService;
import service.LocalLogApiService;
import utility.ApiException;
import utility.ConfigManager;
import utility.DatabaseManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;

/**
 * ValorantTrackerApp (Main / Presentation Layer)
 * -------------------------------------------------------------------------
 * Entry point for the Valorant Game Analytics & Performance Tracker.
 *
 * Architecture Overview (3rd-Year B.Tech Computer Science Project):
 *   - package model   : MatchStats, AgentPerformance, PerformanceSummary
 *   - package service : GameApiClient (Interface), HenrikValorantApiService,
 *                       LocalLogApiService, AnalyticsService
 *   - package utility : DatabaseManager (JDBC/SQLite + CSV Table Fallback),
 *                       ConfigManager, ApiException
 *   - package main    : ValorantTrackerApp (Desktop GUI + CLI Mode), TrendChartPanel
 */
public class ValorantTrackerApp extends JFrame {

    // Valorant Signature Color Palette
    private static final Color BG_DARK = new Color(15, 25, 35);         // #0F1923
    private static final Color PANEL_DARK = new Color(24, 36, 49);      // #182431
    private static final Color BORDER_COLOR = new Color(45, 62, 80);
    private static final Color VAL_RED = new Color(255, 70, 85);        // #FF4655
    private static final Color VAL_CYAN = new Color(0, 245, 212);       // #00F5D4
    private static final Color VAL_GREEN = new Color(46, 213, 115);     // #2ED573
    private static final Color VAL_GOLD = new Color(255, 209, 102);     // #FFD166
    private static final Color TEXT_PRIMARY = new Color(236, 232, 225); // #ECE8E1
    private static final Color TEXT_MUTED = new Color(148, 163, 184);

    // Core Modular Dependencies
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final AnalyticsService analyticsService;
    private final HenrikValorantApiService henrikApiService;
    private final LocalLogApiService localLogService;

    // UI Controls
    private JTextField playerNameField;
    private JTextField playerTagField;
    private JComboBox<String> regionCombo;
    private JComboBox<String> sourceCombo;
    private JLabel statusBannerLabel;

    // KPI Value Labels
    private JLabel kpiMatchWinRateValue;
    private JLabel kpiRoundWinRateValue;
    private JLabel kpiKdRatioValue;
    private JLabel kpiAvgAcsValue;
    private JLabel kpiHeadshotValue;
    private JLabel kpiRankTierValue;

    // Visualization & Tables
    private TrendChartPanel trendChartPanel;
    private JTextArea coachingInsightsArea;
    private DefaultTableModel matchTableModel;
    private DefaultTableModel agentTableModel;
    private int simulationCounter = 1;

    public ValorantTrackerApp() {
        super("VALORANT // Game Analytics & Performance Tracker (B.Tech CS Project)");

        this.configManager = new ConfigManager();
        this.databaseManager = new DatabaseManager(configManager.getDatabaseUrl());
        this.analyticsService = new AnalyticsService();
        this.henrikApiService = new HenrikValorantApiService(configManager.getHenrikApiKey());
        this.localLogService = new LocalLogApiService(configManager.getLocalLogPath());

        initializeWindow();
        buildUserInterface();
        loadInitialData();
    }

    private void initializeWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 820);
        setMinimumSize(new Dimension(1024, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));
    }

    private void buildUserInterface() {
        // 1. Top Header & Input Bar
        add(createTopHeaderPanel(), BorderLayout.NORTH);

        // 2. Center Content (KPI Cards + Tabbed Charts/Tables)
        JPanel centerContainer = new JPanel(new BorderLayout(10, 10));
        centerContainer.setBackground(BG_DARK);
        centerContainer.setBorder(new EmptyBorder(12, 16, 12, 16));

        centerContainer.add(createKpiCardsRow(), BorderLayout.NORTH);
        centerContainer.add(createTabbedWorkspace(), BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        // 3. Bottom Status Bar
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createTopHeaderPanel() {
        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.setBackground(PANEL_DARK);
        headerWrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, VAL_RED));

        // Title & Subtitle on the left
        JPanel brandPanel = new JPanel();
        brandPanel.setLayout(new BoxLayout(brandPanel, BoxLayout.Y_AXIS));
        brandPanel.setBackground(PANEL_DARK);
        brandPanel.setBorder(new EmptyBorder(12, 18, 12, 18));

        JLabel titleLabel = new JLabel("VALORANT ANALYTICS & PERFORMANCE TRACKER");
        titleLabel.setForeground(VAL_RED);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        JLabel subTitleLabel = new JLabel("Modular Java Architecture: model | service (GameApiClient Interface) | utility (JDBC SQL) | main");
        subTitleLabel.setForeground(TEXT_MUTED);
        subTitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        brandPanel.add(titleLabel);
        brandPanel.add(subTitleLabel);

        // Controls on the right
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        controlsPanel.setBackground(PANEL_DARK);

        controlsPanel.add(createStyledLabel("Riot ID:"));
        playerNameField = createStyledTextField(configManager.getDefaultPlayerName(), 8);
        controlsPanel.add(playerNameField);

        controlsPanel.add(createStyledLabel("#"));
        playerTagField = createStyledTextField(configManager.getDefaultPlayerTag(), 4);
        controlsPanel.add(playerTagField);

        controlsPanel.add(createStyledLabel("Region:"));
        regionCombo = new JComboBox<>(new String[]{"ap", "na", "eu", "kr", "latam", "br"});
        regionCombo.setSelectedItem(configManager.getDefaultRegion());
        controlsPanel.add(regionCombo);

        controlsPanel.add(createStyledLabel("API Interface:"));
        sourceCombo = new JComboBox<>(new String[]{
                "Local Log File / Demo (Offline Ready)",
                "HenrikDev Live Valorant API (v3)"
        });
        controlsPanel.add(sourceCombo);

        JButton fetchBtn = createActionButton("Fetch Matches", VAL_RED, Color.WHITE);
        fetchBtn.addActionListener(e -> handleFetchMatches());
        controlsPanel.add(fetchBtn);

        JButton simBtn = createActionButton("+ Simulate Match", VAL_CYAN, BG_DARK);
        simBtn.addActionListener(e -> handleSimulateMatch());
        controlsPanel.add(simBtn);

        JButton apiKeyBtn = createActionButton("API Key Setup & Guide", VAL_GOLD, BG_DARK);
        apiKeyBtn.addActionListener(e -> showApiKeySetupDialog());
        controlsPanel.add(apiKeyBtn);

        JButton clearBtn = createActionButton("Clear SQL DB", new Color(55, 70, 88), TEXT_PRIMARY);
        clearBtn.addActionListener(e -> handleClearDatabase());
        controlsPanel.add(clearBtn);

        headerWrapper.add(brandPanel, BorderLayout.WEST);
        headerWrapper.add(controlsPanel, BorderLayout.EAST);
        return headerWrapper;
    }

    private JPanel createKpiCardsRow() {
        JPanel grid = new JPanel(new GridLayout(1, 6, 12, 0));
        grid.setBackground(BG_DARK);

        kpiMatchWinRateValue = createKpiValueLabel("0.0%", VAL_GREEN);
        kpiRoundWinRateValue = createKpiValueLabel("0.0%", VAL_CYAN);
        kpiKdRatioValue = createKpiValueLabel("0.00", VAL_RED);
        kpiAvgAcsValue = createKpiValueLabel("0", VAL_GOLD);
        kpiHeadshotValue = createKpiValueLabel("0.0%", VAL_CYAN);
        kpiRankTierValue = createKpiValueLabel("Unranked", TEXT_PRIMARY);
        kpiRankTierValue.setFont(new Font("SansSerif", Font.BOLD, 14));

        grid.add(wrapKpiCard("MATCH WIN RATE", kpiMatchWinRateValue, "Wins / Total Matches"));
        grid.add(wrapKpiCard("ROUND WIN RATE", kpiRoundWinRateValue, "Rounds Won / Total Rounds"));
        grid.add(wrapKpiCard("OVERALL K/D RATIO", kpiKdRatioValue, "Kills per Death"));
        grid.add(wrapKpiCard("AVG COMBAT SCORE", kpiAvgAcsValue, "Valorant ACS Benchmark"));
        grid.add(wrapKpiCard("HEADSHOT ACCURACY", kpiHeadshotValue, "Headshots / Total Hits"));
        grid.add(wrapKpiCard("PERFORMANCE FORM", kpiRankTierValue, "Calculated Rating Tier"));

        return grid;
    }

    private JTabbedPane createTabbedWorkspace() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.setBackground(PANEL_DARK);
        tabs.setForeground(TEXT_PRIMARY);

        // TAB 1: Visual Trend Charts + Coaching Insights
        JPanel visualTab = new JPanel(new BorderLayout(0, 10));
        visualTab.setBackground(BG_DARK);

        trendChartPanel = new TrendChartPanel();
        visualTab.add(trendChartPanel, BorderLayout.CENTER);

        // Coaching Insights panel below chart
        JPanel insightsCard = new JPanel(new BorderLayout(6, 6));
        insightsCard.setBackground(PANEL_DARK);
        insightsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel insightsTitle = new JLabel("AUTOMATED VALORANT COACHING & TREND ANALYSIS INSIGHTS");
        insightsTitle.setForeground(VAL_CYAN);
        insightsTitle.setFont(new Font("SansSerif", Font.BOLD, 12));

        coachingInsightsArea = new JTextArea(4, 80);
        coachingInsightsArea.setEditable(false);
        coachingInsightsArea.setBackground(PANEL_DARK);
        coachingInsightsArea.setForeground(TEXT_PRIMARY);
        coachingInsightsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        coachingInsightsArea.setLineWrap(true);
        coachingInsightsArea.setWrapStyleWord(true);

        insightsCard.add(insightsTitle, BorderLayout.NORTH);
        insightsCard.add(new JScrollPane(coachingInsightsArea), BorderLayout.CENTER);
        visualTab.add(insightsCard, BorderLayout.SOUTH);

        tabs.addTab("1. Performance Trends & Visual Analytics", visualTab);

        // TAB 2: SQL Match History Table
        tabs.addTab("2. SQL Match History Table", createMatchHistoryPanel());

        // TAB 3: Agent Performance Breakdown Table
        tabs.addTab("3. Agent & Weapon Analytics", createAgentAnalyticsPanel());

        return tabs;
    }

    private JPanel createMatchHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        String[] columns = {
                "Match ID", "Date", "Player#Tag", "Region", "Map", "Agent",
                "Weapon", "K / D / A", "K/D Ratio", "HS %", "ACS", "Round Score", "Result"
        };

        matchTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = createStyledTable(matchTableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(PANEL_DARK);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAgentAnalyticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        String[] columns = {
                "Agent Name", "Matches Played", "Matches Won", "Agent Win Rate %",
                "Total Kills", "Total Deaths", "Total Assists", "Average K/D", "Average ACS", "Avg Headshot %"
        };

        agentTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = createStyledTable(agentTableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(PANEL_DARK);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(PANEL_DARK);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(6, 16, 6, 16)
        ));

        statusBannerLabel = new JLabel("Ready | " + databaseManager.getStorageStatusMessage());
        statusBannerLabel.setForeground(TEXT_MUTED);
        statusBannerLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel apiHintLabel = new JLabel("API Key Status: " +
                (configManager.getHenrikApiKey().isEmpty() ? "Not Set (Click 'API Key Setup & Guide')" : "Configured (HenrikDev API Ready)"));
        apiHintLabel.setForeground(VAL_GOLD);
        apiHintLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        bar.add(statusBannerLabel, BorderLayout.WEST);
        bar.add(apiHintLabel, BorderLayout.EAST);
        return bar;
    }

    // ==================== CORE APPLICATION ACTIONS ====================

    private void loadInitialData() {
        List<MatchStats> storedMatches = databaseManager.getAllMatches();
        if (storedMatches.isEmpty()) {
            // Automatically seed from local sample log file on first launch so charts look awesome immediately!
            try {
                List<MatchStats> sampleMatches = localLogService.fetchRecentMatches(
                        playerNameField.getText(), playerTagField.getText(), (String) regionCombo.getSelectedItem());
                databaseManager.saveMatches(sampleMatches);
                storedMatches = databaseManager.getAllMatches();
                statusBannerLabel.setText("Loaded " + storedMatches.size() + " Valorant matches from local log into SQL database. | "
                        + databaseManager.getStorageStatusMessage());
            } catch (ApiException e) {
                statusBannerLabel.setText("Welcome! Click 'Fetch Matches' or '+ Simulate Match' to begin.");
            }
        } else {
            statusBannerLabel.setText("Restored " + storedMatches.size() + " matches from SQL storage. | "
                    + databaseManager.getStorageStatusMessage());
        }
        refreshAnalyticsDashboard(storedMatches);
    }

    /**
     * Demonstrates Polymorphism via the GameApiClient interface:
     * Calls fetchRecentMatches() on whichever implementation the user selected!
     */
    private void handleFetchMatches() {
        String playerName = playerNameField.getText().trim();
        String playerTag = playerTagField.getText().trim();
        String region = (String) regionCombo.getSelectedItem();

        configManager.setDefaultPlayer(playerName, playerTag, region);

        // Polymorphic reference to GameApiClient interface
        GameApiClient selectedApiClient;
        if (sourceCombo.getSelectedIndex() == 1) {
            henrikApiService.setApiKey(configManager.getHenrikApiKey());
            selectedApiClient = henrikApiService;
        } else {
            selectedApiClient = localLogService;
        }

        try {
            statusBannerLabel.setText("Fetching matches via " + selectedApiClient.getProviderName() + "...");
            List<MatchStats> fetched = selectedApiClient.fetchRecentMatches(playerName, playerTag, region);
            int saved = databaseManager.saveMatches(fetched);
            List<MatchStats> allMatches = databaseManager.getAllMatches();
            refreshAnalyticsDashboard(allMatches);

            statusBannerLabel.setText(String.format(
                    "Success! Fetched & stored %d matches via %s (Total in SQL DB: %d).",
                    saved, selectedApiClient.getProviderName(), allMatches.size()));
        } catch (ApiException ex) {
            statusBannerLabel.setText("API Error [HTTP " + ex.getStatusCode() + "]: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                    this,
                    "Error Fetching Valorant Data:\n" + ex.getMessage() +
                            "\n\nHTTP Status Code: " + ex.getStatusCode() +
                            "\nTroubleshooting Tip:\n" + ex.getTroubleshootingTip(),
                    "Network / API Exception Handled (" + selectedApiClient.getProviderName() + ")",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void handleSimulateMatch() {
        String playerName = playerNameField.getText().trim();
        String playerTag = playerTagField.getText().trim();
        String region = (String) regionCombo.getSelectedItem();

        MatchStats simulated = localLogService.generateSimulatedMatch(playerName, playerTag, region, simulationCounter++);
        databaseManager.saveMatches(Collections.singletonList(simulated));

        List<MatchStats> allMatches = databaseManager.getAllMatches();
        refreshAnalyticsDashboard(allMatches);

        statusBannerLabel.setText("Simulated & saved new match: " + simulated.toString());
    }

    private void handleClearDatabase() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Clear all stored Valorant matches from the SQL database?",
                "Confirm SQL DELETE",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            databaseManager.clearAllMatches();
            refreshAnalyticsDashboard(Collections.emptyList());
            statusBannerLabel.setText("Cleared all matches from SQL database (DELETE FROM valorant_matches).");
        }
    }

    /**
     * Interactive dialog explaining where to get the free Valorant API key
     * and saving it directly to config.properties.
     */
    private void showApiKeySetupDialog() {
        String guide =
                "WHERE TO GET YOUR VALORANT API KEY (For 3rd-Year B.Tech CS Project):\n" +
                "-------------------------------------------------------------------------\n" +
                "Option 1: HenrikDev Valorant API (RECOMMENDED - Free & Instant for Students)\n" +
                "  • Why? Riot's official Valorant Production API requires a registered company.\n" +
                "    HenrikDev provides free personal Valorant API keys to fetch real match histories.\n" +
                "  • Step 1: Open https://docs.henrikdev.xyz or join https://discord.gg/henrikdev-systems\n" +
                "  • Step 2: Go to the '#get-a-key' channel and generate a free key (starts with HDEV-...).\n" +
                "  • Step 3: Paste your HDEV-... key below and switch the dropdown to 'HenrikDev Live Valorant API'.\n\n" +
                "Option 2: Offline Local Log / Demo Mode (Zero Key Required)\n" +
                "  • Select 'Local Log File / Demo (Offline Ready)' to parse 'data/sample_valorant_logs.txt'\n" +
                "    or click '+ Simulate Match' during your college lab evaluation!\n\n" +
                "Enter your HenrikDev Valorant API Key (HDEV-...):";

        String inputKey = (String) JOptionPane.showInputDialog(
                this,
                guide,
                "Valorant API Key Configuration",
                JOptionPane.INFORMATION_MESSAGE,
                null,
                null,
                configManager.getHenrikApiKey()
        );

        if (inputKey != null) {
            configManager.setHenrikApiKey(inputKey.trim());
            henrikApiService.setApiKey(inputKey.trim());
            statusBannerLabel.setText(inputKey.trim().isEmpty()
                    ? "API Key cleared. Using Local Log / Offline mode."
                    : "Saved HenrikDev API Key to config.properties! Select 'HenrikDev Live Valorant API' and click Fetch.");
        }
    }

    /**
     * Recomputes all analytics metrics via AnalyticsService and updates KPI cards,
     * Graphics2D trend charts, coaching insights, and SQL JTables.
     */
    private void refreshAnalyticsDashboard(List<MatchStats> matches) {
        PerformanceSummary summary = analyticsService.analyzeMatches(matches);
        List<AgentPerformance> agentStats = analyticsService.calculateAgentPerformance(matches);

        // Update KPI Cards
        kpiMatchWinRateValue.setText(String.format("%.1f%% (%dW/%dM)",
                summary.getMatchWinRate(), summary.getMatchesWon(), summary.getTotalMatches()));
        kpiRoundWinRateValue.setText(String.format("%.1f%%", summary.getRoundWinRate()));
        kpiKdRatioValue.setText(String.format("%.2f (KDA %.2f)", summary.getOverallKdRatio(), summary.getOverallKdaRatio()));
        kpiAvgAcsValue.setText(String.format("%.0f ACS", summary.getAverageAcs()));
        kpiHeadshotValue.setText(String.format("%.1f%%", summary.getAverageHeadshotPct()));
        kpiRankTierValue.setText(summary.getPerformanceRankEstimate());

        // Update Custom Graphics2D Trend Charts
        trendChartPanel.updateChartData(matches, agentStats, summary);

        // Update Coaching Insights text
        StringBuilder sb = new StringBuilder();
        List<String> insights = summary.getCoachingInsights();
        for (int i = 0; i < insights.size(); i++) {
            sb.append(" [").append(i + 1).append("] ").append(insights.get(i)).append("\n");
        }
        coachingInsightsArea.setText(sb.toString());

        // Update Match History Table
        matchTableModel.setRowCount(0);
        for (int i = matches.size() - 1; i >= 0; i--) {
            MatchStats m = matches.get(i);
            matchTableModel.addRow(new Object[]{
                    m.getMatchId(),
                    m.getMatchDate(),
                    m.getPlayerName() + "#" + m.getPlayerTag(),
                    m.getRegion().toUpperCase(),
                    m.getMapName(),
                    m.getAgentName(),
                    m.getPrimaryWeapon(),
                    m.getKills() + " / " + m.getDeaths() + " / " + m.getAssists(),
                    String.format("%.2f", m.getKdRatio()),
                    String.format("%.1f%%", m.getHeadshotPercentage()),
                    m.getAverageCombatScore(),
                    m.getRoundsWon() + " - " + m.getRoundsLost(),
                    m.isMatchWon() ? "VICTORY" : "DEFEAT"
            });
        }

        // Update Agent Performance Table
        agentTableModel.setRowCount(0);
        for (AgentPerformance ap : agentStats) {
            agentTableModel.addRow(new Object[]{
                    ap.getAgentName(),
                    ap.getMatchesPlayed(),
                    ap.getMatchesWon(),
                    String.format("%.1f%%", ap.getWinRate()),
                    ap.getTotalKills(),
                    ap.getTotalDeaths(),
                    ap.getTotalAssists(),
                    String.format("%.2f", ap.getAverageKd()),
                    String.format("%.0f", ap.getAverageAcs()),
                    String.format("%.1f%%", ap.getAverageHeadshotPct())
            });
        }
    }

    // ==================== UI HELPER METHODS ====================

    private JPanel wrapKpiCard(String title, JLabel valueLabel, String subtitle) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(PANEL_DARK);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(TEXT_MUTED);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 10));

        JLabel subLbl = new JLabel(subtitle);
        subLbl.setForeground(TEXT_MUTED);
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(subLbl, BorderLayout.SOUTH);
        return card;
    }

    private JLabel createKpiValueLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(color);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        return lbl;
    }

    private JLabel createStyledLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        return lbl;
    }

    private JTextField createStyledTextField(String text, int cols) {
        JTextField tf = new JTextField(text, cols);
        tf.setBackground(BG_DARK);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(VAL_RED);
        tf.setFont(new Font("SansSerif", Font.BOLD, 12));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(4, 6, 4, 6)
        ));
        return tf;
    }

    private JButton createActionButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(7, 12, 7, 12));
        return btn;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(PANEL_DARK);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_COLOR);
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getTableHeader().setBackground(BG_DARK);
        table.getTableHeader().setForeground(VAL_CYAN);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? PANEL_DARK : new Color(20, 31, 43));
                    String valStr = value == null ? "" : value.toString();
                    if ("VICTORY".equals(valStr)) {
                        c.setForeground(VAL_GREEN);
                    } else if ("DEFEAT".equals(valStr)) {
                        c.setForeground(VAL_RED);
                    } else {
                        c.setForeground(TEXT_PRIMARY);
                    }
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        };

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        return table;
    }

    // ==================== CLI DIAGNOSTIC MODE & MAIN ENTRY POINT ====================

    /**
     * Runs a full console report when launched with '--cli' (great for terminal demos or headless tests).
     */
    public static void runCliReport() {
        System.out.println("=========================================================================");
        System.out.println("   VALORANT GAME ANALYTICS & PERFORMANCE TRACKER (CLI / CONSOLE MODE)    ");
        System.out.println("=========================================================================");

        ConfigManager config = new ConfigManager();
        DatabaseManager db = new DatabaseManager(config.getDatabaseUrl());
        GameApiClient localApi = new LocalLogApiService(config.getLocalLogPath());
        AnalyticsService analytics = new AnalyticsService();

        try {
            List<MatchStats> matches = localApi.fetchRecentMatches(
                    config.getDefaultPlayerName(), config.getDefaultPlayerTag(), config.getDefaultRegion());
            db.saveMatches(matches);
            List<MatchStats> stored = db.getAllMatches();

            System.out.println("Storage Engine : " + db.getStorageStatusMessage());
            System.out.println("Data Provider  : " + localApi.getProviderName());
            System.out.println("Matches Loaded : " + stored.size());
            System.out.println("-------------------------------------------------------------------------");

            PerformanceSummary summary = analytics.analyzeMatches(stored);
            System.out.printf("Match Win Rate : %.1f%% (%d Wins / %d Matches)%n",
                    summary.getMatchWinRate(), summary.getMatchesWon(), summary.getTotalMatches());
            System.out.printf("Round Win Rate : %.1f%%%n", summary.getRoundWinRate());
            System.out.printf("Overall K/D    : %.2f (KDA: %.2f)%n",
                    summary.getOverallKdRatio(), summary.getOverallKdaRatio());
            System.out.printf("Average ACS    : %.0f%n", summary.getAverageAcs());
            System.out.printf("Shot Accuracy  : Headshots %.1f%% | Bodyshots %.1f%% | Legshots %.1f%%%n",
                    summary.getAverageHeadshotPct(), summary.getAverageBodyshotPct(), summary.getAverageLegshotPct());
            System.out.printf("Best Agent     : %s%n", summary.getBestAgent());
            System.out.printf("Best Map       : %s%n", summary.getBestMap());
            System.out.printf("Estimated Form : %s%n", summary.getPerformanceRankEstimate());
            System.out.println("-------------------------------------------------------------------------");
            System.out.println("COACHING INSIGHTS:");
            for (String tip : summary.getCoachingInsights()) {
                System.out.println("  * " + tip);
            }
            System.out.println("=========================================================================");
        } catch (ApiException e) {
            System.err.println("CLI Error: " + e);
        }
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg)) {
                runCliReport();
                return;
            }
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            ValorantTrackerApp app = new ValorantTrackerApp();
            app.setVisible(true);
        });
    }
}
