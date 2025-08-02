package controller;

import model.BendPoint;
import model.Line;
import model.SystemManager;
import model.ports.InputPort;
import model.ports.OutputPort;
import view.GamePanel;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Handles:
 *  • left-drag from an OutputPort → InputPort to make wires
 *  • right-click on a wire to show “Add bend…” / “Remove line”
 *  • 3-click bend FSM: middle, footA, footB → calls line.addBendPoint(...)
 */
public class ConnectionController extends MouseInputAdapter {
    private enum Mode { IDLE, WAIT_MIDDLE, WAIT_FOOT_A, WAIT_FOOT_B,  DRAG_MIDDLE}
    private Mode mode = Mode.IDLE;

    private final SystemManager model;
    private final GamePanel     canvas;
    private OutputPort          dragSource;
    private Line                editLine;
//    private Point               bendMiddle, bendFootA;
    private Point hMid;   // yellow control handle
    private Point hA;     // first red foot
    private Point hB;     // second red foot
    private final JPopupMenu    lineMenu;
    private BendPoint dragBend;
    public ConnectionController(SystemManager model, GamePanel canvas) {
        this.model  = model;
        this.canvas = canvas;

        lineMenu = new JPopupMenu();
        initPopup();

        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
    }

    private void initPopup() {
        JMenuItem bend   = new JMenuItem("Add bend…");
        JMenuItem remove = new JMenuItem("Remove line");
        bend.addActionListener(e -> {
            if (editLine != null) {
                mode = Mode.WAIT_MIDDLE;
                // optionally show instructions: JOptionPane.showMessageDialog(...)
            }
        });
        remove.addActionListener(e -> {
            if (editLine != null) {
                editLine.getStart().setLine(null);
                editLine.getEnd()  .setLine(null);
                model.removeLine(editLine);
                editLine = null;
                mode = Mode.IDLE;
                clearHandles();          // <<< hide dots
                canvas.repaint();
            }
        });
        lineMenu.add(bend);
        lineMenu.add(remove);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();

        // 1) right-click → show menu if over a line
        if (SwingUtilities.isRightMouseButton(e)) {
            for (Line l : model.allLines) {
                if (l.hit(p, 5)) {         // assumes a hit() helper in Line
                    editLine = l;
                    lineMenu.show(canvas, p.x, p.y);
                    return;
                }
            }
        }

        // 2) bend FSM consumes left-clicks when active
        if (mode != Mode.IDLE && SwingUtilities.isLeftMouseButton(e)) {
            handleBendClick(p);
            return;
        }

        // 3) normal left-click wiring
        if (SwingUtilities.isLeftMouseButton(e)) {
            OutputPort port=findOutputAt(p);
            if (port != null && port.getLine() == null) {
                dragSource = port;
                canvas.showPreview(dragSource.getCenter(), p);
            }
            else{
                dragSource = null;
            }
        }
        if (mode == Mode.IDLE &&
                SwingUtilities.isLeftMouseButton(e) &&
                hMid != null && hMid.distance(p) < 6) {
            mode = Mode.DRAG_MIDDLE;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (mode == Mode.IDLE && dragSource != null) {
            canvas.showPreview(dragSource.getCenter(), e.getPoint());
        }
        if (mode == Mode.DRAG_MIDDLE) {
            hMid = e.getPoint();
            if (dragBend != null) {
                dragBend.setMiddle(hMid);      // directly mutate the right BendPoint
            }
            canvas.setHandles(hMid, hA, hB);   // redraw dots + line
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (mode == Mode.DRAG_MIDDLE) {
            mode = Mode.IDLE;
            clearHandles();      // or keep them if you want persistent dots
            return;
        }
        // if we were bending, ignore the release
        if (mode != Mode.IDLE) return;

        if (dragSource != null) {
            canvas.hidePreview();
            InputPort target = findInputAt(e.getPoint());

            if (target != null &&
                    target.getType() == dragSource.getType()) {

                // 1) create the wire
                Line wire = new Line(dragSource, target);

                // 2) attach to ports
                dragSource.setLine(wire);
                target    .setLine(wire);

                // 3) put in the model
                model.addLine(wire);
            }
            dragSource = null;
            canvas.repaint();
        }
    }
    private void handleBendClick(Point p) {
        switch (mode) {
            case WAIT_MIDDLE -> {
                hMid = p;   hA = hB = null;
                canvas.setHandles(hMid, null, null);
                mode = Mode.WAIT_FOOT_A;
            }
            case WAIT_FOOT_A -> {
                hA = p;
                canvas.setHandles(hMid, hA, null);
                mode = Mode.WAIT_FOOT_B;
            }
            case WAIT_FOOT_B -> {
                hB = p;
                try {
                    dragBend = editLine.addBendPoint(hA, hMid, hB);   // keep reference
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(canvas, ex.getMessage(),
                            "Cannot add bend", JOptionPane.ERROR_MESSAGE);
                    clearHandles();
                    mode = Mode.IDLE;
                    return;
                }
                canvas.setHandles(hMid, hA, hB);   // keep dots visible
                mode = Mode.IDLE;                  // stay editable
            }
        }
    }
    /* ─── hit-testing helpers ─── */
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
    private void clearHandles() {
        hMid = hA = hB = null;
        dragBend = null;
        canvas.clearHandles();
    }
}
