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
    private enum Mode { IDLE, WAIT_MIDDLE, WAIT_FOOT_A, WAIT_FOOT_B,  DRAG_MIDDLE, MOVING_SYSTEM }
    private static final int SYS_W = 90, SYS_H = 70;     // your box size
    private static final int MOVE_RADIUS = 120;
    private Mode mode = Mode.IDLE;
    private model.System contextSystem;
    private final SystemManager model;
    private final GamePanel     canvas;
    private OutputPort          dragSource;
    private Line                editLine;
//    private Point               bendMiddle, bendFootA;
    private Point hMid;
    private Point hA;
    private Point hB;
    private model.System movingSystem = null;
    private Point moveAnchorCenter = null;
    private final JPopupMenu    lineMenu;
    private final JPopupMenu systemMenu;
    private BendPoint dragBend;
    private Point lastContextPoint;
    public ConnectionController(SystemManager model, GamePanel canvas) {
        this.model  = model;
        this.canvas = canvas;

        lineMenu = new JPopupMenu();
        systemMenu = new JPopupMenu();
        initPopup();
        initSystemPopup();

        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
    }

    private void initSystemPopup() {
        JMenuItem move = new JMenuItem("Move system (within radius)");
        move.addActionListener(e -> {
            if (contextSystem != null) {
                if (!model.spendTotalCoins(15)) {
                    JOptionPane.showMessageDialog(canvas,
                            "Not enough coins to move this system.",
                            "Insufficient coins", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                movingSystem     = contextSystem;
                moveAnchorCenter = systemCenter(movingSystem);
                mode = Mode.MOVING_SYSTEM;
                contextSystem = null;
            }
        });
        systemMenu.add(move);
    }

    private void initPopup() {
        JMenuItem bend    = new JMenuItem("Add bend…");
        JMenuItem remove  = new JMenuItem("Remove line");
        JMenuItem center  = new JMenuItem("Bring Back to Center (20s)");
        JMenuItem zeroAcc = new JMenuItem("Zero Acceleration (20s)"); // NEW

        bend.addActionListener(e -> { if (editLine != null) mode = Mode.WAIT_MIDDLE; });

        remove.addActionListener(e -> {
            if (editLine != null) {
                editLine.getStart().setLine(null);
                editLine.getEnd().setLine(null);
                model.removeLine(editLine);
                editLine = null;
                mode = Mode.IDLE;
                clearHandles();
                canvas.repaint();
            }
        });

// initPopup()
        center.addActionListener(e -> {
            if (editLine != null && lastContextPoint != null) {
                if (!model.spendTotalCoins(10)) {
                    JOptionPane.showMessageDialog(canvas,
                            "Not enough coins for Bring Back to Center.",
                            "Insufficient coins", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                editLine.addChangeCenter(lastContextPoint);   // 20s point
                canvas.repaint();
            }
        });

        zeroAcc.addActionListener(e -> {
            if (editLine != null && lastContextPoint != null) {
                if (!model.spendTotalCoins(20)) {
                    JOptionPane.showMessageDialog(canvas,
                            "Not enough coins for Zero Acceleration.",
                            "Insufficient coins", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                editLine.addZeroAccelPoint(lastContextPoint); // 20s point
                canvas.repaint();
            }
        });


        lineMenu.add(bend);
        lineMenu.add(remove);
        lineMenu.add(center);
        lineMenu.add(zeroAcc); // NEW
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        if (SwingUtilities.isRightMouseButton(e)) {
            model.System sys = findSystemAt(p);
            if (sys != null) {
                lastContextPoint = p;
                contextSystem = sys;                 // <-- keep context only
                systemMenu.show(canvas, p.x, p.y);
                return;
            }

            for (Line l : model.allLines) {
                if (l.hit(p, 5)) {
                    editLine = l;
                    lastContextPoint = p;              // <- save click point
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
        if (mode == Mode.MOVING_SYSTEM && movingSystem != null && moveAnchorCenter != null) {
            // clamp desired center to circle around anchor
            Point desiredCenter = e.getPoint();
            Point clamped = clampToCircle(desiredCenter, moveAnchorCenter, MOVE_RADIUS);
            // convert center to top-left location
            Point newLoc = new Point(clamped.x - SYS_W/2, clamped.y - SYS_H/2);
            movingSystem.setLocation(newLoc);
            canvas.repaint();
            return;
        }

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
        if (mode == Mode.MOVING_SYSTEM) {
            mode = Mode.IDLE;
            movingSystem = null;
            moveAnchorCenter = null;
            canvas.repaint();
            return;
        }

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
    private model.System findSystemAt(Point p) {
        for (var sys : model.getAllSystems()) {
            Point loc = sys.getLocation();
            Rectangle r = new Rectangle(loc.x, loc.y, SYS_W, SYS_H);
            if (r.contains(p)) return sys;
        }
        return null;
    }
    private Point systemCenter(model.System s) {
        Point loc = s.getLocation();
        return new Point(loc.x + SYS_W/2, loc.y + SYS_H/2);
    }
    private static Point clampToCircle(Point p, Point c, int r) {
        double dx = p.x - c.x, dy = p.y - c.y;
        double d  = Math.hypot(dx, dy);
        if (d == 0 || d <= r) return p;
        double k = r / d;
        return new Point((int)Math.round(c.x + dx*k),
                (int)Math.round(c.y + dy*k));
    }
}
