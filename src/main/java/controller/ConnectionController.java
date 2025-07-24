package controller;

import model.Line;
import model.SystemManager;
import model.ports.InputPort;
import model.ports.OutputPort;
import view.GamePanel;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Drag-and-drop wiring:
 *   • press on an OutputPort starts a drag
 *   • dashed preview follows cursor
 *   • release on a matching InputPort commits a Line
 */
public class ConnectionController extends MouseInputAdapter {

    private final SystemManager model;
    private final GamePanel     canvas;

    private OutputPort dragSource = null;

    public ConnectionController(SystemManager model, GamePanel panel) {
        this.model  = model;
        this.canvas = panel;

        panel.addMouseListener(this);
        panel.addMouseMotionListener(this);
    }

    /* ---------- mouse ---------- */

    @Override public void mousePressed(MouseEvent e) {
        dragSource = findOutputAt(e.getPoint());
        if (dragSource != null) {
            canvas.showPreview(dragSource.getCenter(), e.getPoint());
        }
    }

    @Override public void mouseDragged(MouseEvent e) {
        if (dragSource != null) {
            canvas.showPreview(dragSource.getCenter(), e.getPoint());
        }
    }

    @Override public void mouseReleased(MouseEvent e) {
        if (dragSource == null) return;

        canvas.hidePreview();

        InputPort target = findInputAt(e.getPoint());
        if (target != null && target.getType() == dragSource.getType()) {
            Line wire = new Line(dragSource, target);
            model.addLine(wire);   // keep the model in sync
        }

        dragSource = null;
        canvas.repaint();          // draw new wire (if any)
    }

    /* ---------- hit-testing in the MODEL ---------- */

    private OutputPort findOutputAt(Point p) {
        for (var sys : model.getAllSystems())
            for (OutputPort op : sys.getOutputPorts())
                if (op.contains(p)) return op;
        return null;
    }

    private InputPort findInputAt(Point p) {
        for (var sys : model.getAllSystems())
            for (InputPort ip : sys.getInputPorts())
                if (ip.contains(p)) return ip;
        return null;
    }
}
