// src/main/java/view/GamePanel.java
package view;

import model.Line;
import model.Port;
import model.SystemManager;
import model.Type;
import model.ports.InputPort;
import model.ports.OutputPort;
import model.systems.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.util.List;

/**
 * Pure canvas. On each repaint it:
 *   • loops over model.getAllLines()  → draws every wire
 *   • loops over model.getAllSystems()→ draws every box & its ports
 * The controller mutates the model and then simply calls repaint().
 */
public class GamePanel extends JPanel {

    /* ---------- constants shared with System box drawing ---------- */
    private static final int SYS_W = 90, SYS_H = 70, PORT = 12, RND = 16;

    private final SystemManager model;

    /* dashed rubber-band preview during drag */
    private Point previewA, previewB;
    private Point hMid, hA, hB;
    public GamePanel(SystemManager model) {
        this.model = model;
        setBackground(Color.WHITE);
        setLayout(null);          // absolute coords, though we draw manually
    }

    /* ========== preview helpers called by controller ========== */
    public void showPreview(Point a, Point b) { previewA = a; previewB = b; repaint(); }
    public void hidePreview() { previewA = previewB = null; repaint(); }

//    public SystemManager getModel() { return model; }
    public void setHandles(Point mid, Point a, Point b) {
        this.hMid = mid; this.hA = a; this.hB = b;
        repaint();
    }
    public void clearHandles() {
        hMid = hA = hB = null;
        repaint();
    }
    /* =========================================================== */

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        /* 1 ▸ draw wires */
        if (model.allLines != null)
            for (Line l : model.allLines) {
                g2.setStroke(new BasicStroke(2));
                g2.setColor(Color.BLACK);
                List<Point> pts = l.getPath(6);
                for (int i = 0; i < pts.size() - 1; i++) {
                    Point a = pts.get(i), b = pts.get(i + 1);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
            }

        /* 2 ▸ dashed preview */
        if (previewA != null && previewB != null) {
            g2.setColor(new Color(0, 0, 0, 128));
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{6, 6}, 0));
            g2.drawLine(previewA.x, previewA.y, previewB.x, previewB.y);
        }

        /* 3 ▸ systems & ports */
        for (var sys : model.getAllSystems()) {
            drawSystem(g2, sys);
        }
        /* 2b ▸ draw bend handles */
        if (hMid != null) {
            g2.setColor(Color.YELLOW);
            g2.fillOval(hMid.x-4, hMid.y-4, 8, 8);
        }
        if (hA != null && hB != null) {
            g2.setColor(Color.RED);
            g2.fillOval(hA.x-3, hA.y-3, 6, 6);
            g2.fillOval(hB.x-3, hB.y-3, 6, 6);
        }
    }

    /* ---------- helpers ---------- */
    private void drawSystem(Graphics2D g2, model.System sys) {
        Point loc = sys.getLocation();
        int x0 = loc.x, y0 = loc.y;

        // box
        g2.setColor(colorFor(sys));
        g2.fillRoundRect(x0, y0, SYS_W, SYS_H, RND, RND);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(x0, y0, SYS_W, SYS_H, RND, RND);

        // label
        String label = sys.getClass().getSimpleName().replace("System", "");
        FontMetrics fm = g2.getFontMetrics();
        int tx = x0 + (SYS_W - fm.stringWidth(label)) / 2;
        int ty = y0 + (SYS_H + fm.getAscent()) / 2 - 4;
        g2.drawString(label, tx, ty);

        // ports
        paintPorts(g2, sys.getInputPorts(),  x0, y0, true);
        paintPorts(g2, sys.getOutputPorts(), x0, y0, false);
    }

    private void paintPorts(Graphics2D g2,
                            List<? extends Port> ports,
                            int x0, int y0,
                            boolean inputs)
    {
        int n = ports.size();
        for (int i = 0; i < n; i++) {
            Port p = ports.get(i);

            int cx = x0 + (inputs ? 0 : SYS_W);
            int cy = y0 + (i + 1) * SYS_H / (n + 1);

            /* ✱ NEW: keep model in sync with where we actually draw ✱ */
            p.setCenter(new Point(cx, cy));

            drawShape(g2, p.getType(), cx, cy);
        }
    }

    private void drawShape(Graphics2D g2, Type t, int cx, int cy) {
        switch (t) {
            case SQUARE -> {
                g2.setColor(Color.BLUE);
                g2.fillRect(cx - PORT / 2, cy - PORT / 2, PORT, PORT);
            }
            case TRIANGLE -> {
                g2.setColor(Color.GREEN.darker());
                int[] xs = {cx, cx - PORT / 2, cx + PORT / 2};
                int[] ys = {cy - PORT / 2, cy + PORT / 2, cy + PORT / 2};
                g2.fillPolygon(xs, ys, 3);
            }
            case INFINITY -> {
                g2.setColor(Color.MAGENTA);
                g2.setStroke(new BasicStroke(2));
                g2.draw(new CubicCurve2D.Float(cx - PORT / 2f, cy,
                        cx - PORT / 4f, cy - PORT / 2f,
                        cx + PORT / 4f, cy - PORT / 2f,
                        cx + PORT / 2f, cy));
                g2.draw(new CubicCurve2D.Float(cx - PORT / 2f, cy,
                        cx - PORT / 4f, cy + PORT / 2f,
                        cx + PORT / 4f, cy + PORT / 2f,
                        cx + PORT / 2f, cy));
            }
        }
    }

    private static Color colorFor(model.System s) {
        return switch (s) {
            case ReferenceSystem    ignore -> new Color(100, 149, 237);
            case NormalSystem       ignore -> new Color(144, 238, 144);
            case SpySystem          ignore -> Color.LIGHT_GRAY;
            case VpnSystem          ignore -> new Color(255, 228, 181);
            case AntiTrojanSystem   ignore -> new Color(255, 182, 193);
            case DestroyerSystem    ignore -> new Color(240, 128, 128);
            case DistributionSystem ignore -> new Color(255, 250, 205);
            case MergerSystem       ignore -> new Color(216, 191, 216);
            default                        -> Color.GRAY;
        };
    }
}
