package model.packets;

import model.Line;
import model.Packet;
import model.Port;
import model.System;          // <-- your own class

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.INFINITY;

/**
 * InfinityPacket: accelerates (+a) by default.  If routed through the wrong
 * port, it begins decelerating (-a).  Speed may become negative; in that case
 * the packet walks back toward the origin system.  Upon entering either end,
 * it is inserted into that system’s packet–queue and removed from the line.
 */
public class InfinityPacket extends Packet implements MessengerTag {

    /* ── movement bookkeeping ─────────────────────────────────────────── */
    private List<Point>  path;               // full poly-line from Line
    private List<Float>  segLen;             // lengths of each segment
    private int          segIdx   = 0;       // current segment index
    private float        sInSeg   = 0f;      // distance progressed along segIdx

    private final float  maxSpeed = 5f;      // optional speed cap

    /* Which systems sit at each end of the line */
    private System originSystem;
    private System destinationSystem;

    public InfinityPacket() {
        super();
        size         = 1;
        speed        = 1f;        // initial pixels / second
        acceleration = 0.3f;      // default constant acceleration
        type         = INFINITY;
        isMoving     = false;
    }

    /* ── wrong-port reaction: reverse acceleration (gentle slowdown) ──── */
    @Override
    public void wrongPort(Port port) {
        if (!port.getType().equals(INFINITY)) {
            acceleration = -0.1f;            // start decelerating
        }
    }

    /* ── Call once per frame (e.g. from an AnimationTimer) ────────────── */
    public void moveForward() {

        /* 0) First-time initialisation */
        if (!isMoving) {
            if(path==null) {
                this.path = line.getPath(5);                // smoothness = 5
                this.segLen = new ArrayList<>(path.size() - 1);
                for (int i = 0; i < path.size() - 1; i++) {
                    segLen.add((float) path.get(i).distance(path.get(i + 1)));
                }
                this.segIdx = 0;
                this.sInSeg = 0f;
                this.point = path.get(0);
                this.isMoving = true;
            }
            else{
                return;
            }
        }

        /* Already arrived (previous frame)? */

        /* 1) Physics update */
        speed += acceleration * dt;
        // Clamp so we never explode nor move backwards faster than maxSpeed
        if (speed >  maxSpeed) speed =  maxSpeed;
        if (speed < -maxSpeed) speed = -maxSpeed;

        float remaining = speed * dt;        // can be negative!

        /* 2) March along the poly-line in either direction */
        while (remaining != 0f &&
                segIdx >= 0 &&
                segIdx < segLen.size()) {

            if (remaining > 0f) {            // ── FORWARD ──
                float segRemaining = segLen.get(segIdx) - sInSeg;

                if (remaining < segRemaining) {
                    sInSeg += remaining;
                    remaining = 0f;
                } else {
                    remaining -= segRemaining;
                    segIdx++;                // into next segment
                    sInSeg = 0f;
                }

            } else {                         // ── BACKWARD ──
                float segPassed = sInSeg;    // how much we’ve already travelled
                if (-remaining < segPassed) {
                    sInSeg += remaining;     // remaining is negative
                    remaining = 0f;
                } else {
                    remaining += segPassed;  // still negative
                    segIdx--;                // back to previous segment
                    if (segIdx >= 0) {       // if we’re still on the line
                        sInSeg = segLen.get(segIdx);
                    }
                }
            }

            /* Update visible position */
            if (segIdx >= 0 && segIdx < segLen.size()) {
                point = Packet.lerp(
                        path.get(segIdx),
                        path.get(segIdx + 1),
                        sInSeg / segLen.get(segIdx)
                );
            }
        }

        /* 3) Handle arrival at either end */

        // --- reached DESTINATION (forward) ---
        if (segIdx >= segLen.size()) {
            finishTraversal(line.getEnd().getParentSystem());
            return;
        }

        // --- returned to ORIGIN (backwards) ---
        if (segIdx < 0) {
            finishTraversal(line.getStart().getParentSystem());
        }
    }

    /* helper: wrap-up procedure once the packet leaves the line */
    private void finishTraversal(System targetSystem) {
        isMoving = false;
        line.removeMovingPacket();
        targetSystem.addPacket(this);
        setSystem(targetSystem);
        setLine(null);
    }
}
