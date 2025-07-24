package view;

import model.Line;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class LineView extends JComponent {
    private final Line line;
    public LineView(Line l) {
        this.line = l;
        setOpaque(false);
        setBounds(0,0,9999,9999);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2));
        List<Point> pts = line.getPath(0);
        for (int i = 0; i < pts.size()-1; i++) {
            Point a = pts.get(i), b = pts.get(i+1);
            g2.drawLine(a.x, a.y, b.x, b.y);
        }
    }
}
