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
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.List;

/**
 * TrendChartPanel (Presentation / Visualization Layer)
 * -------------------------------------------------------------------------
 * Replicates the Tracker.gg Match-by-Match Trend Canvas and SVG Hitbox
 * Target Dummy silhouettes in pure vector Java 2D (Graphics2D + Path2D):
 *   1. Match-by-Match Trend Chart: K/D Ratio curve (Teal) with gradient fill,
 *      Headshot % curve (Crimson Red), and Round Win % rounded bars.
 *   2. HitboxDummyPanel: Smooth cubic-Bezier vector human target dummy for
 *      the Accuracy Card and Mini Weapon Row hitbox diagrams (zero pixelation).
 */
public class TrendChartPanel extends JPanel {

    private static final Color TRN_CARD = new Color(24, 29, 40);      // #181d28
    private static final Color TRN_BORDER = new Color(39, 46, 63);    // #272e3f
    private static final Color TRN_RED = new Color(255, 70, 85);      // #ff4655
    private static final Color TRN_TEAL = new Color(51, 226, 178);    // #33e2b2
    private static final Color TRN_GREEN = new Color(46, 213, 115);   // #2ed573
    private static final Color TEXT_SEC = new Color(154, 164, 184);
    private static final Color TEXT_MUTED = new Color(104, 115, 135);
    private static final Color GRID_LINE = new Color(34, 41, 56);

    private List<MatchStats> matches = Collections.emptyList();

    public TrendChartPanel() {
        setBackground(TRN_CARD);
        setPreferredSize(new Dimension(780, 225));
    }

    public void updateChartData(List<MatchStats> chronologicalMatches, List<AgentPerformance> agentStats, PerformanceSummary summary) {
        this.matches = chronologicalMatches != null ? chronologicalMatches : Collections.emptyList();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        if (matches.isEmpty()) {
            g2.setColor(TEXT_SEC);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            String msg = "No matches available for selected filter.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        int padL = 46;
        int padR = 24;
        int padT = 24;
        int padB = 34;
        int plotW = Math.max(60, w - padL - padR);
        int plotH = Math.max(50, h - padT - padB);

        // Horizontal grid lines (0.0 to 3.0 K/D)
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        for (int i = 0; i <= 4; i++) {
            int y = padT + plotH - (i * plotH / 4);
            g2.setColor(GRID_LINE);
            g2.drawLine(padL, y, padL + plotW, y);
            g2.setColor(TEXT_MUTED);
            g2.drawString(String.format("%.1f", i * 0.75), 12, y + 4);
        }

        int total = matches.size();
        int startIdx = Math.max(0, total - 15);
        List<MatchStats> view = matches.subList(startIdx, total);
        int n = view.size();
        int slotW = Math.max(20, plotW / Math.max(1, n));

        double[] kdX = new double[n];
        double[] kdY = new double[n];
        double[] hsX = new double[n];
        double[] hsY = new double[n];

        for (int i = 0; i < n; i++) {
            MatchStats m = view.get(i);
            double x = n == 1 ? (padL + plotW / 2.0) : (padL + (i * (double) plotW) / (n - 1));

            double rwr = Math.min(100.0, Math.max(0.0, m.getRoundWinRate()));
            double barH = (rwr / 100.0) * plotH;
            double barW = Math.max(12, Math.min(26, slotW * 0.34));

            g2.setColor(m.isMatchWon() ? new Color(46, 213, 115, 38) : new Color(255, 70, 85, 34));
            g2.fill(new RoundRectangle2D.Double(x - barW / 2.0, padT + plotH - barH, barW, barH, 6, 6));

            double kdClamped = Math.min(3.0, Math.max(0.0, m.getKdRatio()));
            double hsClamped = Math.min(60.0, Math.max(0.0, m.getHeadshotPercentage()));

            kdX[i] = x;
            kdY[i] = padT + plotH - ((kdClamped / 3.0) * plotH);
            hsX[i] = x;
            hsY[i] = padT + plotH - ((hsClamped / 60.0) * plotH);

            g2.setColor(TEXT_SEC);
            String mapLabel = m.getMapName() != null && m.getMapName().length() >= 4
                    ? m.getMapName().substring(0, 4)
                    : (m.getMapName() != null ? m.getMapName() : "Map");
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(mapLabel, (int) Math.round(x - fm.stringWidth(mapLabel) / 2.0), padT + plotH + 19);
        }

        // Subtle Teal Area Gradient under K/D Line
        if (n > 1) {
            Path2D.Double area = new Path2D.Double();
            area.moveTo(kdX[0], padT + plotH);
            for (int i = 0; i < n; i++) {
                area.lineTo(kdX[i], kdY[i]);
            }
            area.lineTo(kdX[n - 1], padT + plotH);
            area.closePath();
            g2.setPaint(new GradientPaint(0, padT, new Color(51, 226, 178, 45), 0, padT + plotH, new Color(51, 226, 178, 0)));
            g2.fill(area);
        }

        // Draw Headshot % Line (Crimson Red)
        if (n > 1) {
            Path2D.Double hsPath = new Path2D.Double();
            hsPath.moveTo(hsX[0], hsY[0]);
            for (int i = 1; i < n; i++) {
                hsPath.lineTo(hsX[i], hsY[i]);
            }
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(TRN_RED);
            g2.draw(hsPath);
        }

        // Draw K/D Ratio Line (Teal)
        if (n > 1) {
            Path2D.Double kdPath = new Path2D.Double();
            kdPath.moveTo(kdX[0], kdY[0]);
            for (int i = 1; i < n; i++) {
                kdPath.lineTo(kdX[i], kdY[i]);
            }
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(TRN_TEAL);
            g2.draw(kdPath);
        }

        // Draw crisp vector nodes and K/D labels
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        for (int i = 0; i < n; i++) {
            MatchStats m = view.get(i);

            g2.setColor(TRN_CARD);
            g2.fill(new Ellipse2D.Double(hsX[i] - 4.0, hsY[i] - 4.0, 8.0, 8.0));
            g2.setColor(TRN_RED);
            g2.fill(new Ellipse2D.Double(hsX[i] - 3.0, hsY[i] - 3.0, 6.0, 6.0));

            g2.setColor(TRN_CARD);
            g2.fill(new Ellipse2D.Double(kdX[i] - 4.8, kdY[i] - 4.8, 9.6, 9.6));
            g2.setColor(TRN_TEAL);
            g2.fill(new Ellipse2D.Double(kdX[i] - 3.6, kdY[i] - 3.6, 7.2, 7.2));

            String kdStr = String.format("%.2f", m.getKdRatio());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(kdStr, (int) Math.round(kdX[i] - fm.stringWidth(kdStr) / 2.0), (int) Math.max(13, Math.round(kdY[i] - 8)));
        }

        g2.dispose();
    }

    /**
     * Pure Vector Java2D component that renders the Tracker.gg Human Target Hitbox Dummy
     * using smooth cubic Bezier curves (Path2D.Double) so it is 100% crisp at any DPI scale.
     */
    public static class HitboxDummyPanel extends JPanel {
        private final boolean miniMode;
        private double headPct = 22.0;
        private double bodyPct = 72.0;
        private double legPct = 6.0;

        public HitboxDummyPanel(boolean miniMode) {
            this.miniMode = miniMode;
            setOpaque(false);
            if (!miniMode) {
                setPreferredSize(new Dimension(90, 148));
            } else {
                setPreferredSize(new Dimension(28, 60));
            }
        }

        public void setPercentages(double h, double b, double l) {
            this.headPct = h;
            this.bodyPct = b;
            this.legPct = l;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();

            if (!miniMode) {
                g2.setColor(new Color(17, 21, 30));
                g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));
                g2.setColor(TRN_BORDER);
                g2.draw(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));

                // Scale vector SVG (viewBox 80 x 160) smoothly inside the box
                double sx = w / 80.0;
                double sy = h / 160.0;
                double sc = Math.min(sx, sy) * 0.90;
                double ox = (w - 80.0 * sc) / 2.0;
                double oy = (h - 160.0 * sc) / 2.0;
                g2.translate(ox, oy);
                g2.scale(sc, sc);

                // Head circle
                g2.setColor(TRN_RED);
                g2.fill(new Ellipse2D.Double(40 - 13.5, 22 - 13.5, 27, 27));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(new Ellipse2D.Double(40 - 13.5, 22 - 13.5, 27, 27));

                // Torso + Arms (exact SVG path from web/index.html: M20 42 C20 38, 60 38, 60 42 L64 92...)
                Path2D.Double torso = new Path2D.Double();
                torso.moveTo(20, 42);
                torso.curveTo(20, 38, 60, 38, 60, 42);
                torso.lineTo(64, 92);
                torso.lineTo(52, 92);
                torso.lineTo(52, 60);
                torso.lineTo(48, 60);
                torso.lineTo(48, 96);
                torso.lineTo(32, 96);
                torso.lineTo(32, 60);
                torso.lineTo(28, 60);
                torso.lineTo(28, 92);
                torso.lineTo(16, 92);
                torso.closePath();
                g2.setColor(TRN_TEAL);
                g2.fill(torso);

                // Legs
                g2.setColor(new Color(104, 115, 135));
                g2.fill(new RoundRectangle2D.Double(26, 100, 11, 52, 5, 5));
                g2.fill(new RoundRectangle2D.Double(43, 100, 11, 52, 5, 5));
            } else {
                // Mini Weapon Row Dummy (exact SVG viewBox 36 x 82 from web/index.html)
                Color hCol = headPct >= 30 ? Color.WHITE : new Color(108, 119, 139);
                Color bCol = bodyPct >= 40 ? Color.WHITE : new Color(154, 164, 184);
                Color lCol = legPct >= 20 ? Color.WHITE : new Color(108, 119, 139);

                double sx = w / 36.0;
                double sy = h / 82.0;
                double sc = Math.min(sx, sy) * 0.94;
                double ox = (w - 36.0 * sc) / 2.0;
                double oy = (h - 82.0 * sc) / 2.0;
                g2.translate(ox, oy);
                g2.scale(sc, sc);

                // Head
                g2.setColor(hCol);
                g2.fill(new Ellipse2D.Double(18 - 6.5, 9 - 6.5, 13, 13));

                // Torso
                Path2D.Double torso = new Path2D.Double();
                torso.moveTo(8, 20);
                torso.curveTo(8, 17, 28, 17, 28, 20);
                torso.lineTo(31, 46);
                torso.lineTo(25, 46);
                torso.lineTo(24, 28);
                torso.lineTo(23, 28);
                torso.lineTo(23, 47);
                torso.lineTo(13, 47);
                torso.lineTo(13, 28);
                torso.lineTo(12, 28);
                torso.lineTo(11, 46);
                torso.lineTo(5, 46);
                torso.closePath();
                g2.setColor(bCol);
                g2.fill(torso);

                // Left Leg
                Path2D.Double legL = new Path2D.Double();
                legL.moveTo(12, 49);
                legL.lineTo(17, 49);
                legL.lineTo(15, 79);
                legL.lineTo(10, 79);
                legL.closePath();
                g2.setColor(lCol);
                g2.fill(legL);

                // Right Leg
                Path2D.Double legR = new Path2D.Double();
                legR.moveTo(19, 49);
                legR.lineTo(24, 49);
                legR.lineTo(26, 79);
                legR.lineTo(21, 79);
                legR.closePath();
                g2.fill(legR);
            }

            g2.dispose();
        }
    }
}
