//package model.packets;
//
//import model.Line;
//import model.Packet;
//import model.Port;
//
//import java.awt.*;
//import java.io.Serializable;
//import java.util.ArrayList;
//import java.util.List;
//
//import static model.Type.*;
//
//public class SquarePacket extends Packet implements MessengerTag{
//    private List<Point>  path;                 // full list from Line#getPath
//    private List<Float>  segLen;               // length of each p[i] → p[i+1]
//    private int          segIdx     = 0;       // current segment index
//    private float        sInSeg     = 0f;
//    public SquarePacket() {
//        super();
//        size=2;
//        speed=0.4f;
//        type=SQUARE;
//    }
//    public void wrongPort(Port port){
//        if(!port.getType().equals(SQUARE)){
//            speed=speed/2;
//        }
//    }
//    public void moveForward() {
//
//        /* 0) Initialise the traversal on first call */
//        if (!isMoving) {
//            if(path==null) {
//                this.path = line.getPath(5);         // smoothness = 5 (your choice)
//                this.segLen = new ArrayList<>(path.size() - 1);
//                for (int i = 0; i < path.size() - 1; i++) {
//                    Point a = path.get(i), b = path.get(i + 1);
//                    segLen.add((float) a.distance(b));
//                }
//                segIdx = 0;
//                sInSeg = 0f;
//                point = path.get(0);             // start exactly at first point
//                isMoving = true;
//            }
//            else {
//                return;
//            }
//        }
//
//
//        /* 2) Compute how far we should travel this frame */
//        speed += acceleration * dt;            // if you later want acceleration
//        float remaining = speed * dt;
//
//        /* 3) March along the poly-line */
//        while (remaining > 0 && segIdx < segLen.size()) {
//            float segRemaining = segLen.get(segIdx) - sInSeg;
//
//            if (remaining < segRemaining) {
//                /* --- stay inside current segment --- */
//                sInSeg += remaining;
//                float t = sInSeg / segLen.get(segIdx); // 0 ≤ t < 1
//
//                Point p0 = path.get(segIdx);
//                Point p1 = path.get(segIdx + 1);
//                point = lerp(p0, p1, t);
//
//                remaining = 0;
//            } else {
//                /* --- finish this segment and continue --- */
//                remaining -= segRemaining;
//                segIdx++;
//                sInSeg = 0f;
//                point = path.get(Math.min(segIdx, path.size() - 1));
//            }
//        }
//
//        /* 4) Finished all segments? */
//        if (segIdx >= segLen.size()) {
//            isMoving = false;
//            line.removeMovingPacket();
//            line.getEnd().transferPacket(this);
//        }
//    }
//    @Override public void advance(float dt) { moveForward(); }
//}
package model.packets;

import model.Packet;
import model.Port;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.SQUARE;

public class SquarePacket extends Packet implements MessengerTag {

    /* ── per-wire state ─────────────────────────────────────────── */
    private List<Point>  path;     // poly-line from Line
    private List<Float>  segLen;   // segment lengths
    private int   segIdx = 0;
    private float sInSeg = 0f;

    public SquarePacket() {
        size = 2;
        speed = 3f;
        type  = SQUARE;
    }

    @Override public void wrongPort(Port p) {
        if (p.getType() != SQUARE) speed /= 2f;
    }

    /* ------------------------------------------------------------------ */
    @Override public void advance(float dt) { moveForward(dt); }

    /* main per-frame routine ------------------------------------------- */
    private void moveForward(float dt) {

        /* 0 ▸ ensure we have a path + segment table */
        if (path == null) initialisePath();

        /* 1 ▸ physics */
        speed += acceleration * dt;
        float remaining = speed * dt;

        /* 2 ▸ march along poly-line */
        while (remaining > 0f && segIdx < segLen.size()) {
            float segRemain = segLen.get(segIdx) - sInSeg;

            if (remaining < segRemain) {               // stay in segment
                sInSeg += remaining;
                remaining = 0f;
            } else {                                   // finish segment
                remaining -= segRemain;
                segIdx++;  sInSeg = 0f;
            }
            updateVisiblePoint();
        }

        /* 3 ▸ reached destination ? */
        if (segIdx >= segLen.size()) {
            line.removeMovingPacket();
            line.getEnd().transferPacket(this);
            setSystem(line.getEnd().getParentSystem());
            setLine(null);
            isMoving = false;
        }
    }

    /* ---------- helpers ---------- */

    private void initialisePath() {
        path   = line.getPath(6);
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0;
        sInSeg = 0f;
        point  = path.get(0);
        isMoving = true;
    }

    private void updateVisiblePoint() {
        if (segIdx >= segLen.size()) return;
        point = lerp(
                path.get(segIdx),
                path.get(segIdx + 1),
                sInSeg / segLen.get(segIdx));
    }
    @Override
    protected void resetPath() {
        path    = null;
        segLen  = null;
        segIdx  = 0;
        sInSeg  = 0f;
    }
}
