//package model.packets;
//
//import model.Line;
//import model.Packet;
//import model.Port;
//import model.System;          // <-- your own class
//
//import java.awt.*;
//import java.util.ArrayList;
//import java.util.List;
//
//import static model.Type.INFINITY;
//
///**
// * InfinityPacket: accelerates (+a) by default.  If routed through the wrong
// * port, it begins decelerating (-a).  Speed may become negative; in that case
// * the packet walks back toward the origin system.  Upon entering either end,
// * it is inserted into that system’s packet–queue and removed from the line.
// */
//public class InfinityPacket extends Packet implements MessengerTag {
//
//    /* ── movement bookkeeping ─────────────────────────────────────────── */
//    private List<Point>  path;               // full poly-line from Line
//    private List<Float>  segLen;             // lengths of each segment
//    private int          segIdx   = 0;       // current segment index
//    private float        sInSeg   = 0f;      // distance progressed along segIdx
//
//    private final float  maxSpeed = 5f;      // optional speed cap
//
//    /* Which systems sit at each end of the line */
//    private System originSystem;
//    private System destinationSystem;
//
//    public InfinityPacket() {
//        super();
//        size         = 1;
//        speed        = 1f;        // initial pixels / second
//        acceleration = 0.3f;      // default constant acceleration
//        type         = INFINITY;
//        isMoving     = false;
//    }
//
//    /* ── wrong-port reaction: reverse acceleration (gentle slowdown) ──── */
//    @Override
//    public void wrongPort(Port port) {
//        if (!port.getType().equals(INFINITY)) {
//            acceleration = -0.1f;            // start decelerating
//        }
//    }
//
//    /* ── Call once per frame (e.g. from an AnimationTimer) ────────────── */
//    public void moveForward() {
//
//        /* 0) First-time initialisation */
//        if (!isMoving) {
//            if(path==null) {
//                this.path = line.getPath(5);                // smoothness = 5
//                this.segLen = new ArrayList<>(path.size() - 1);
//                for (int i = 0; i < path.size() - 1; i++) {
//                    segLen.add((float) path.get(i).distance(path.get(i + 1)));
//                }
//                this.segIdx = 0;
//                this.sInSeg = 0f;
//                this.point = path.get(0);
//                this.isMoving = true;
//            }
//            else{
//                return;
//            }
//        }
//
//        /* Already arrived (previous frame)? */
//
//        /* 1) Physics update */
//        speed += acceleration * dt;
//        // Clamp so we never explode nor move backwards faster than maxSpeed
//        if (speed >  maxSpeed) speed =  maxSpeed;
//        if (speed < -maxSpeed) speed = -maxSpeed;
//
//        float remaining = speed * dt;        // can be negative!
//
//        /* 2) March along the poly-line in either direction */
//        while (remaining != 0f &&
//                segIdx >= 0 &&
//                segIdx < segLen.size()) {
//
//            if (remaining > 0f) {            // ── FORWARD ──
//                float segRemaining = segLen.get(segIdx) - sInSeg;
//
//                if (remaining < segRemaining) {
//                    sInSeg += remaining;
//                    remaining = 0f;
//                } else {
//                    remaining -= segRemaining;
//                    segIdx++;                // into next segment
//                    sInSeg = 0f;
//                }
//
//            } else {                         // ── BACKWARD ──
//                float segPassed = sInSeg;    // how much we’ve already travelled
//                if (-remaining < segPassed) {
//                    sInSeg += remaining;     // remaining is negative
//                    remaining = 0f;
//                } else {
//                    remaining += segPassed;  // still negative
//                    segIdx--;                // back to previous segment
//                    if (segIdx >= 0) {       // if we’re still on the line
//                        sInSeg = segLen.get(segIdx);
//                    }
//                }
//            }
//
//            /* Update visible position */
//            if (segIdx >= 0 && segIdx < segLen.size()) {
//                point = Packet.lerp(
//                        path.get(segIdx),
//                        path.get(segIdx + 1),
//                        sInSeg / segLen.get(segIdx)
//                );
//            }
//        }
//
//        /* 3) Handle arrival at either end */
//
//        // --- reached DESTINATION (forward) ---
//        if (segIdx >= segLen.size()) {
//            finishTraversal(line.getEnd().getParentSystem());
//            return;
//        }
//
//        // --- returned to ORIGIN (backwards) ---
//        if (segIdx < 0) {
//            finishTraversal(line.getStart().getParentSystem());
//        }
//    }
//
//    /* helper: wrap-up procedure once the packet leaves the line */
//    private void finishTraversal(System targetSystem) {
//        isMoving = false;
//        line.removeMovingPacket();
//        targetSystem.addPacket(this);
//        setSystem(targetSystem);
//        setLine(null);
//    }
//    @Override public void advance(float dt) { moveForward(); }
//}
package model.packets;

import model.Packet;
import model.Port;
import model.System;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.INFINITY;

/**  ∞-packet that can accelerate / decelerate and even reverse direction. */
public class InfinityPacket extends Packet implements MessengerTag {

    /* ── movement bookkeeping ──────────────────────────────────────── */
    private List<Point> path;        // full poly-line from Line
    private List<Float> segLen;      // length of each segment
    private int   segIdx  = 0;       // current segment index
    private float sInSeg  = 0f;      // distance progressed inside segIdx
    private final float maxSpeed = 5f;

    /* ctor */
    public InfinityPacket() {
        super();
        size         = 1;
        speed        = 3f;
        acceleration = 0.3f;
        type         = INFINITY;
    }

    @Override                     /* wrong-port reaction */
    public void wrongPort(Port p) {
        if (p.getType() != INFINITY)
            acceleration = -0.1f;
    }

    /* ---------- main per-frame update ---------- */
    @Override
    public void advance(float dt) {
        if (line == null) {
            isMoving = false;
            return;
        }      // not on a wire
        /* 0 ▸ ensure path + segment table exist */
        if (path == null) initialisePathAndSegments();

        /* 1 ▸ physics */
        speed += acceleration * dt;
        speed = Math.max(-maxSpeed, Math.min(maxSpeed, speed));

        float remaining = speed * dt;    // may be negative

        /* 2 ▸ march along poly-line */
        while (remaining != 0f && segIdx >= 0 && segIdx < segLen.size()) {

            if (remaining > 0f) {                    // → forward
                float segRemain = segLen.get(segIdx) - sInSeg;
                if (remaining < segRemain) {
                    sInSeg += remaining;
                    remaining = 0f;
                }
                else {
                    remaining -= segRemain;
                    segIdx++;  sInSeg = 0f;
                }
            }
            else {                                   // ← backward
                float segPassed = sInSeg;
                if (-remaining < segPassed) {
                    sInSeg += remaining;             // remaining < 0
                    remaining = 0f;
                } else {
                    remaining += segPassed;
                    segIdx--;                        // to previous segment
                    if (segIdx >= 0)
                        sInSeg = segLen.get(segIdx);
                }
            }

            /* update visible point */
            if (segIdx >= 0 && segIdx < segLen.size()) {
                point = Packet.lerp(
                        path.get(segIdx),
                        path.get(segIdx + 1),
                        sInSeg / segLen.get(segIdx));
            }
        }

        /* 3 ▸ arrival handling */
        if (segIdx >= segLen.size()) {                       // reached end
            line.getEnd().getParentSystem().receivePacket(this);
        } else if (segIdx < 0) {                             // returned home
            line.getStart().getParentSystem().receivePacket(this);
        }
    }

    /* helper: build path[] and segLen[] exactly once */
    private void initialisePathAndSegments() {
        path   = line.getPath(6);                     // smoothness = 5
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0;
        sInSeg = 0f;
        point  = path.get(0);
        isMoving = true;
    }

    @Override
    protected void resetPath() {
        path    = null;
        segLen  = null;
        segIdx  = 0;
        sInSeg  = 0f;
    }
}
