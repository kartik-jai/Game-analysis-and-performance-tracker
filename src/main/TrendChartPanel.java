package main;

import model.AgentPerformance;
import model.MatchStats;
import model.PerformanceSummary;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;

/**
 * TrendChartPanel (Presentation / Visualization Layer)
 * -------------------------------------------------------------------------
 * Custom Java Swing JPanel that renders anti-aliased data visualizations
 * using Java 2D Graphics (Graphics2D):
 *   1. K/D Ratio & Headshot % Multi-Line Trend Chart across Matches
 *   2. Round Win Rate % Bar Chart per Match (Victory vs Defeat coloring)
 *   3. Weapon Shot Accuracy Breakdown (Headshot / Bodyshot / Legshot) & Top Agents
 */
public class TrendChartPanel extends JPanel {

    // Valorant Dark UI Theme Colors
    private static final Color BG_DARK = new Color(15, 25, 35);         // #0F1923
    private static final Color CARD_BG = new Color(24, 36, 49);         // #182431
    private static final Color CARD_BORDER = new Color(45, 62, 80);
    private static final Color VAL_RED = new Color(255, 70, 85);        // #FF4655
    private static final Color VAL_CYAN = new Color(0, 245, 212);       // #00F5D4
    private static final Color VAL_GOLD = new Color(255, 209, 102);     // #FFD166
    private static final Color VAL_GREEN = new Color(46, 213, 115);     // #2ED573
    private static final Color TEXT_LIGHT = new Color(236, 232, 225);   // #ECE8E1
    private static final Color TEXT_MUTED = new Color(148, 163, 184);
    private static final Color GRID_LINE = new Color(40, 55, 71);

    private List<MatchStats> matches = Collections.emptyList();
    private List<AgentPerformance> agentStats = Collections.emptyList();
    private PerformanceSummary summary;

    public TrendChartPanel() {
        setBackground(BG_DARK);
        setPreferredSize(new Dimension(980, 440));
    }

    public void updateChartData(List<MatchStats> matches, List<AgentPerformance> agentStats, PerformanceSummary summary) {
        this.matches = matches != null ? matches : Collections.emptyList();
        this.agentStats = agentStats != null ? agentStats : Collections.emptyList();
        this.summary = summary;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int pad = 12;

        if (matches.isEmpty()) {
            drawEmptyState(g2, w, h);
            g2.dispose();
            return;
        }

        int leftWidth = (int) (w * 0.56) - pad * 2;
        int rightWidth = w - leftWidth - pad * 3;
        int fullHeight = h - pad * 2;
        int halfHeight = (fullHeight - pad) / 2;

        // 1. Left Card: K/D Ratio & Headshot % Trend Over Matches
        drawKdAndHsTrendChart(g2, pad, pad, leftWidth, fullHeight);

        // 2. Right Top Card: Round Win Rate % Bar Chart per Match
        drawRoundWinRateBarChart(g2, pad * 2 + leftWidth, pad, rightWidth, halfHeight);

        // 3. Right Bottom Card: Shot Accuracy Breakdown & Agent Win Rate Bars
        drawShotAccuracyAndAgentsCard(g2, pad * 2 + leftWidth, pad * 2 + halfHeight, rightWidth, fullHeight - halfHeight - pad);

        g2.dispose();
    }

    private void drawEmptyState(Graphics2D g2, int w, int h) {
        g2.setColor(CARD_BG);
        g2.fillRoundRect(20, 20, w - 40, h - 40, 14, 14);
        g2.setColor(CARD_BORDER);
        g2.drawRoundRect(20, 20, w - 40, h - 40, 14, 14);

        g2.setColor(TEXT_LIGHT);
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        String msg = "No Valorant Match Data Loaded Yet";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2 - 10);

        g2.setColor(TEXT_MUTED);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String sub = "Click 'Load Local Log / Demo' or 'Fetch Live API' above to visualize K/D, Headshot %, and Round Win Rate trends.";
        FontMetrics fm2 = g2.getFontMetrics();
        g2.drawString(sub, (w - fm2.stringWidth(sub)) / 2, h / 2 + 18);
    }

    /**
     * Draws the K/D Ratio & Headshot % dual trend chart over chronological matches.
     */
    private void drawKdAndHsTrendChart(Graphics2D g2, int x, int y, int w, int h) {
        drawCardContainer(g2, x, y, w, h, "PERFORMANCE TRENDS OVER TIME (K/D RATIO & HEADSHOT %)");

        int plotLeft = x + 48;
        int plotRight = x + w - 24;
        int plotTop = y + 54;
        int plotBottom = y + h - 48;
        int plotW = Math.max(50, plotRight - plotLeft);
        int plotH = Math.max(50, plotBottom - plotTop);

        // Legend
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(VAL_CYAN);
        g2.fillOval(plotRight - 220, y + 16, 10, 10);
        g2.drawString("K/D Ratio (x20 scale)", plotRight - 205, y + 25);

        g2.setColor(VAL_RED);
        g2.fillOval(plotRight - 85, y + 16, 10, 10);
        g2.drawString("Headshot %", plotRight - 70, y + 25);

        // Horizontal grid lines (0% to 60% / 0.0 to 3.0 K/D)
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int i = 0; i <= 5; i++) {
            int gy = plotBottom - (i * plotH / 5);
            g2.setColor(GRID_LINE);
            g2.drawLine(plotLeft, gy, plotRight, gy);

            g2.setColor(TEXT_MUTED);
            String label = String.format("%.1f / %d%%", i * 0.6, i * 12);
            g2.drawString(label, x + 6, gy + 4);
        }

        int n = matches.size();
        int startIndex = Math.max(0, n - 12); // Show up to last 12 matches clearly
        List<MatchStats> view = matches.subList(startIndex, n);
        int count = view.size();

        int[] kdPointsX = new int[count];
        int[] kdPointsY = new int[count];
        int[] hsPointsX = new int[count];
        int[] hsPointsY = new int[count];

        for (int i = 0; i < count; i++) {
            MatchStats m = view.get(i);
            int px = count == 1 ? (plotLeft + plotW / 2) : (plotLeft + (i * plotW) / (count - 1));

            double kdClamped = Math.min(3.0, Math.max(0.0, m.getKdRatio()));
            double hsClamped = Math.min(60.0, Math.max(0.0, m.getHeadshotPercentage()));

            int ky = plotBottom - (int) ((kdClamped / 3.0) * plotH);
            int hy = plotBottom - (int) ((hsClamped / 60.0) * plotH);

            kdPointsX[i] = px;
            kdPointsY[i] = ky;
            hsPointsX[i] = px;
            hsPointsY[i] = hy;

            // X-axis match label (Map + Agent code)
            g2.setColor(TEXT_MUTED);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String mLabel = "M" + (startIndex + i + 1);
            String agShort = m.getAgentName().length() > 3 ? m.getAgentName().substring(0, 3) : m.getAgentName();
            g2.drawString(mLabel, px - 8, plotBottom + 16);
            g2.drawString(agShort, px - 10, plotBottom + 29);
        }

        // Draw connecting lines
        g2.setStroke(new BasicStroke(2.4f));
        for (int i = 0; i < count - 1; i++) {
            g2.setColor(VAL_RED);
            g2.drawLine(hsPointsX[i], hsPointsY[i], hsPointsX[i + 1], hsPointsY[i + 1]);

            g2.setColor(VAL_CYAN);
            g2.drawLine(kdPointsX[i], kdPointsY[i], kdPointsX[i + 1], kdPointsY[i + 1]);
        }

        // Draw nodes and value badges
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        for (int i = 0; i < count; i++) {
            MatchStats m = view.get(i);

            // HS% point
            g2.setColor(VAL_RED);
            g2.fillOval(hsPointsX[i] - 4, hsPointsY[i] - 4, 8, 8);
            g2.drawString(String.format("%.0f%%", m.getHeadshotPercentage()), hsPointsX[i] - 10, hsPointsY[i] - 8);

            // K/D point
            g2.setColor(VAL_CYAN);
            g2.fillOval(kdPointsX[i] - 4, kdPointsY[i] - 4, 8, 8);
            g2.drawString(String.format("%.2f", m.getKdRatio()), kdPointsX[i] - 10, kdPointsY[i] + 15);
        }
    }

    /**
     * Draws Round Win Rate % per Match as a color-coded bar chart.
     */
    private void drawRoundWinRateBarChart(Graphics2D g2, int x, int y, int w, int h) {
        drawCardContainer(g2, x, y, w, h, "ROUND WIN RATE % BY MATCH (GREEN = WIN, RED = LOSS)");

        int plotLeft = x + 36;
        int plotRight = x + w - 16;
        int plotTop = y + 42;
        int plotBottom = y + h - 28;
        int plotW = Math.max(40, plotRight - plotLeft);
        int plotH = Math.max(30, plotBottom - plotTop);

        // 50% threshold dashed line
        int midY = plotBottom - plotH / 2;
        g2.setColor(VAL_GOLD);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        g2.drawLine(plotLeft, midY, plotRight, midY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.drawString("50%", x + 8, midY + 4);
        g2.setStroke(new BasicStroke(1.0f));

        int n = matches.size();
        int startIndex = Math.max(0, n - 12);
        List<MatchStats> view = matches.subList(startIndex, n);
        int count = view.size();
        if (count == 0) return;

        int slotW = plotW / count;
        int barW = Math.max(10, slotW - 8);

        for (int i = 0; i < count; i++) {
            MatchStats m = view.get(i);
            double rwr = Math.min(100.0, Math.max(0.0, m.getRoundWinRate()));
            int barH = (int) ((rwr / 100.0) * plotH);
            int bx = plotLeft + i * slotW + (slotW - barW) / 2;
            int by = plotBottom - barH;

            g2.setColor(m.isMatchWon() ? VAL_GREEN : VAL_RED);
            g2.fillRoundRect(bx, by, barW, barH, 6, 6);

            g2.setColor(TEXT_LIGHT);
            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            String pctText = String.format("%.0f%%", rwr);
            g2.drawString(pctText, bx, Math.max(plotTop + 8, by - 4));

            g2.setColor(TEXT_MUTED);
            String mapCode = m.getMapName().length() >= 3 ? m.getMapName().substring(0, 3) : m.getMapName();
            g2.drawString(mapCode, bx, plotBottom + 14);
        }
    }

    /**
     * Draws Weapon Hit Accuracy distribution (Head/Body/Legs) and Top Agents Win Rate bars.
     */
    private void drawShotAccuracyAndAgentsCard(Graphics2D g2, int x, int y, int w, int h) {
        drawCardContainer(g2, x, y, w, h, "WEAPON HIT ACCURACY & TOP AGENT PERFORMANCE");

        if (summary == null) return;

        int innerX = x + 18;
        int innerW = w - 36;
        int barY = y + 42;
        int barH = 22;

        double hs = summary.getAverageHeadshotPct();
        double bs = summary.getAverageBodyshotPct();
        double ls = summary.getAverageLegshotPct();

        int hsW = (int) ((hs / 100.0) * innerW);
        int bsW = (int) ((bs / 100.0) * innerW);
        int lsW = Math.max(0, innerW - hsW - bsW);

        // Draw horizontal stacked hit accuracy bar
        g2.setColor(VAL_RED);
        g2.fillRoundRect(innerX, barY, Math.max(4, hsW), barH, 6, 6);

        g2.setColor(VAL_CYAN);
        g2.fillRect(innerX + hsW, barY, Math.max(4, bsW), barH);

        g2.setColor(VAL_GOLD);
        g2.fillRoundRect(innerX + hsW + bsW, barY, Math.max(4, lsW), barH, 6, 6);

        // Labels under stacked bar
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        int labelY = barY + barH + 17;
        g2.setColor(VAL_RED);
        g2.drawString(String.format("Headshots: %.1f%%", hs), innerX, labelY);

        g2.setColor(VAL_CYAN);
        g2.drawString(String.format("Bodyshots: %.1f%%", bs), innerX + innerW / 3, labelY);

        g2.setColor(VAL_GOLD);
        g2.drawString(String.format("Legshots: %.1f%%", ls), innerX + (innerW * 2) / 3, labelY);

        // Top 3 Agents mini progress bars
        int startAgentY = labelY + 22;
        int maxAgents = Math.min(3, agentStats.size());
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));

        for (int i = 0; i < maxAgents; i++) {
            AgentPerformance ap = agentStats.get(i);
            int rowY = startAgentY + i * 24;
            if (rowY + 14 > y + h - 6) break;

            g2.setColor(TEXT_LIGHT);
            String label = String.format("%-8s | %.2f K/D | %.0f ACS",
                    ap.getAgentName(), ap.getAverageKd(), ap.getAverageAcs());
            g2.drawString(label, innerX, rowY + 12);

            int meterX = innerX + 185;
            int meterW = Math.max(40, innerW - 235);
            g2.setColor(GRID_LINE);
            g2.fillRoundRect(meterX, rowY + 2, meterW, 12, 6, 6);

            int fillW = (int) ((Math.min(100.0, ap.getWinRate()) / 100.0) * meterW);
            g2.setColor(VAL_GREEN);
            g2.fillRoundRect(meterX, rowY + 2, fillW, 12, 6, 6);

            g2.setColor(TEXT_LIGHT);
            g2.drawString(String.format("%.0f%% WR", ap.getWinRate()), meterX + meterW + 6, rowY + 12);
        }
    }

    private void drawCardContainer(Graphics2D g2, int x, int y, int w, int h, String title) {
        g2.setColor(CARD_BG);
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(CARD_BORDER);
        g2.drawRoundRect(x, y, w, h, 12, 12);

        g2.setColor(VAL_RED);
        g2.fillRect(x, y + 10, 4, 18);

        g2.setColor(TEXT_LIGHT);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(title, x + 14, y + 24);
    }
}
