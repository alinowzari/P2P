// src/main/java/view/GamePanel.java
package view;

import model.*;
import model.packets.*;
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
    private static final int PACKET_R = 8;
    private final SystemManager model;
    private final JLabel statusLabel  = new JLabel("Ready: false");
    private final JLabel coinLabel   = new JLabel("Coins: 0");
    /* dashed rubber-band preview during drag */
    private Point previewA, previewB;
    private Point hMid, hA, hB;
    public GamePanel(SystemManager model) {
        this.model = model;
        setBackground(Color.WHITE);
        setLayout(null);
        statusLabel.setBounds(10,10,120,20);
        coinLabel.setBounds(10,30,120,20);
        add(statusLabel);
        add(coinLabel);
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
        // ── update our two labels ──────────────────────────────────────
        statusLabel.setText("Ready: " + model.isReady());
        coinLabel.setText("Coins: " + model.coinCount);
        //if crashed remove this line
//        repaint();

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
        for (var pkt : model.allPackets) {       // or model.getAllPackets()
            drawPacket(g2, pkt);
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
        String qty = String.valueOf(sys.countPackets());
        int w = fm.stringWidth(qty);
        g2.setColor(Color.BLACK);
        g2.drawString(qty, x0 + SYS_W - w - 4, y0 + fm.getAscent());
        // ports
        paintPorts(g2, sys.getInputPorts(),  x0, y0, true);
        paintPorts(g2, sys.getOutputPorts(), x0, y0, false);
        drawQueuedPackets(g2, sys, x0, y0 + SYS_H + 4);
    }
//    private void drawPacket(Graphics2D g2, Packet p) {
//        if(p.getPoint()==null) return;
//        int cx = p.getPoint().x, cy = p.getPoint().y;
//        int s  = p.getSize();              // treat as “radius” or half-side
//
//        switch (p) {                       // JDK 22 instance-pattern switch
//            case InfinityPacket inf -> {
//                g2.setColor(Color.MAGENTA);
//                drawInfinity(g2, cx, cy, s);
//            }
//            case SquarePacket sq -> {
//                g2.setColor(Color.BLUE);
//                g2.fillRect(cx - s, cy - s, 2 * s, 2 * s);
//            }
//            case TrianglePacket tri -> {
//                g2.setColor(Color.ORANGE);
//                int[] xs = {cx, cx - s, cx + s};
//                int[] ys = {cy - s, cy + s, cy + s};
//                g2.fillPolygon(xs, ys, 3);
//            }
//            case BigPacket big -> {
//                g2.setColor(colorForId(big.getColorId()));
//                g2.fillOval(cx - s, cy - s, 2 * s, 2 * s);
//            }
//            case BitPacket bit -> {
//                g2.setColor(colorForId(bit.getColorId()));
//                g2.fillOval(cx - s, cy - s, 2 * s, 2 * s);
//                // optional index label
//                g2.setColor(Color.WHITE);
//                g2.setFont(getFont().deriveFont(Font.BOLD, s));
//                String idx = String.valueOf(bit.getFragmentIdx());
//                int w = g2.getFontMetrics().stringWidth(idx);
//                g2.drawString(idx, cx - w / 2, cy + s / 2);
//            }
//            case ProtectedPacket<?> prot -> {
//                g2.setColor(new Color(0x55_99FF));
//                g2.fillOval(cx - s, cy - s, 2 * s, 2 * s);
//                g2.setColor(Color.WHITE);
//                g2.setStroke(new BasicStroke(2));
//                g2.drawOval(cx - s, cy - s, 2 * s, 2 * s);
//            }
//            case SecretPacket2<?> sec -> {
//                g2.setColor(Color.BLACK);
//                g2.fillRect(cx - s, cy - s, 2 * s, 2 * s);
//                g2.setColor(new Color(0x88_FF88));
//                int pad = s / 2;
//                g2.fillRect(cx - s + pad, cy - s + pad,
//                        2 * (s - pad), 2 * (s - pad));
//            }
//            default -> {                   // graceful fallback
//                g2.setColor(Color.GRAY);
//                g2.drawOval(cx - s, cy - s, 2 * s, 2 * s);
//            }
//        }
//    }
/* ---------- draws one travelling packet ---------- */
private void drawPacket(Graphics2D g2, Packet p) {
    if (p.getPoint() == null) return;

    int cx = p.getPoint().x, cy = p.getPoint().y;
    int r  = PACKET_R;                    // fixed draw radius

    switch (p) {
        case InfinityPacket inf -> {
            g2.setColor(Color.MAGENTA);
            drawInfinity(g2, cx, cy, r);
        }
        case SquarePacket sq -> {
            g2.setColor(Color.BLUE);
            g2.fillRect(cx - r, cy - r, 2 * r, 2 * r);
        }
        case TrianglePacket tri -> {
            g2.setColor(Color.ORANGE);
            int[] xs = {cx, cx - r, cx + r};
            int[] ys = {cy - r, cy + r, cy + r};
            g2.fillPolygon(xs, ys, 3);
        }
        case BigPacket big -> drawCircle(g2, cx, cy, r, colorForId(big.getColorId()), null);
        case BitPacket bit -> {
            drawCircle(g2, cx, cy, r, colorForId(bit.getColorId()),
                    String.valueOf(bit.getFragmentIdx()));
        }
        case ProtectedPacket<?> prot -> {
            g2.setColor(new Color(0x5599FF));
            g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        }
        case SecretPacket2<?> sec -> {
            g2.setColor(Color.BLACK);
            g2.fillRect(cx - r, cy - r, 2 * r, 2 * r);
            g2.setColor(new Color(0x88FF88));
            int pad = r / 2;
            g2.fillRect(cx - r + pad, cy - r + pad,
                    2 * (r - pad), 2 * (r - pad));
        }
        default -> {
            g2.setColor(Color.GRAY);
            g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        }
    }
}
    private void drawCircle(Graphics2D g2, int cx, int cy, int r,
                            Color fill, String annotation) {
        g2.setColor(fill);
        g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);

        if (annotation != null) {
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 8f));
            int w = g2.getFontMetrics().stringWidth(annotation);
            g2.drawString(annotation, cx - w / 2, cy + 3);
        }
    }

    private void drawInfinity(Graphics2D g2, int cx, int cy, int r) {
        int d = r;                     // horizontal radius of each loop
        int w = r / 2;                 // stroke thickness

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // left loop
        g2.drawOval(cx - 2 * d, cy - d, 2 * d, 2 * d);
        // right loop
        g2.drawOval(cx,         cy - d, 2 * d, 2 * d);

        g2.setStroke(old);             // restore
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
    private void drawQueuedPackets(Graphics2D g2,
                                   model.System sys,
                                   int startX, int baseY) {

        int gap  = 8;                  // horizontal spacing between icons
        int size = 6;                  // radius / half-side of mini icon
        int x    = startX;

        for (Packet p : sys.getPackets()) {
            switch (p) {
                case SquarePacket sq  -> { g2.setColor(Color.BLUE);   g2.fillRect(x-size, baseY-size, 2*size, 2*size); }
                case TrianglePacket tr-> { g2.setColor(Color.ORANGE); int[] xs={x, x-size, x+size}; int[] ys={baseY-size, baseY+size, baseY+size}; g2.fillPolygon(xs,ys,3); }
                case InfinityPacket inf->{ g2.setColor(Color.MAGENTA); g2.drawOval(x-2*size, baseY-size, 2*size,2*size); g2.drawOval(x, baseY-size, 2*size,2*size); }
                case BigPacket big     -> { g2.setColor(colorForId(big.getColorId())); g2.fillOval(x-size, baseY-size, 2*size,2*size); }
                default                -> { g2.setColor(Color.GRAY);  g2.fillOval(x-size, baseY-size, 2*size,2*size); }
            }
            x += 2*size + gap;
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
    private static Color colorForId(int id) {
        return switch (id % 6) {
            case 0 -> new Color(0xEF5350);   // red
            case 1 -> new Color(0x42A5F5);   // blue
            case 2 -> new Color(0x66BB6A);   // green
            case 3 -> new Color(0xFFB74D);   // orange
            case 4 -> new Color(0xAB47BC);   // purple
            default -> new Color(0x26A69A);  // teal
        };
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
