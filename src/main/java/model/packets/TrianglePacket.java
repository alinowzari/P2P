//package model.packets;
//
//import model.Packet;
//import model.Port;
//import model.ports.InputPort;
//import java.awt.*;
//import java.io.Serializable;
//import java.util.ArrayList;
//import java.util.List;
//import static model.Type.TRIANGLE;
//import model.ports.*;
//
//
//public class TrianglePacket extends Packet implements MessengerTag{
//    private List<Point>  path;                 // Line#getPath(…)
//    private List<Float>  segLen;               // length of each segment
//    private int          segIdx   = 0;         // which segment we’re in
//    private float        sInSeg   = 0f;        // distance progressed along segIdx
//    private final float  maxSpeed = 7f;        // optional cap (tweak if needed)
//    public TrianglePacket() {
//        super();
//        size=3;
//        speed=0.25f;
//        type=TRIANGLE;
//    }
//    public void wrongPort(Port port){
//        if(!port.getType().equals(TRIANGLE)){
//            speed = Math.min(speed + acceleration * dt, maxSpeed);
//        }
//    }
//    public void moveForward() {
//
//        /* 0) Initialise on the very first frame */
//        if (!isMoving) {
//            if(path==null) {
//                path = line.getPath(5);               // same smoothness as square
//                segLen = new ArrayList<>(path.size() - 1);
//                for (int i = 0; i < path.size() - 1; i++) {
//                    segLen.add((float) path.get(i).distance(path.get(i + 1)));
//                }
//                segIdx = 0;
//                sInSeg = 0f;
//                point = path.get(0);
//                isMoving = true;
//            }
//            else {
//                return;
//            }
//        }
//
//        /* 1) Physics update – TRIANGLE accelerates every frame */
//        speed = Math.min(speed + acceleration * dt, maxSpeed);   // v = v₀ + a·dt
//        float remaining = speed * dt;                            // Δs this frame
//
//        /* 2) March along the poly-line */
//        while (remaining > 0 && segIdx < segLen.size()) {
//            float segRemaining = segLen.get(segIdx) - sInSeg;
//
//            if (remaining < segRemaining) {
//                // inside current segment
//                sInSeg += remaining;
//                float t = sInSeg / segLen.get(segIdx);
//
//                Point a = path.get(segIdx);
//                Point b = path.get(segIdx + 1);
//                point  = lerp(a, b, t);
//
//                remaining = 0;
//            } else {
//                // finish this segment and continue
//                remaining -= segRemaining;
//                segIdx++;
//                sInSeg = 0f;
//                point  = path.get(Math.min(segIdx, path.size() - 1));
//            }
//        }
//
//        /* 3) Reached the end? */
//        if (segIdx >= segLen.size()) {
//            isMoving = false;
//            line.removeMovingPacket();
//            line.getEnd().transferPacket(this);
//            // TODO: fire an “arrived” event here if you need one
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

import static model.Type.TRIANGLE;

public class TrianglePacket extends Packet implements MessengerTag {

    /* ── per-wire state ───────────────────────────────────────────── */
    private List<Point> path;      // poly-line from Line
    private List<Float> segLen;    // segment lengths
    private int   segIdx = 0;
    private float sInSeg = 0f;
    private final float maxSpeed = 7f;

    public TrianglePacket() {
        size  = 3;
        speed = 2.5f;
        type  = TRIANGLE;
    }

    @Override public void wrongPort(Port p) {
        if (p.getType() != TRIANGLE)
            speed = Math.min(speed + acceleration, maxSpeed);
    }

    /* ---------------------------------------------------------------- */
    @Override public void advance(float dt) {if (path == null) initialisePath();

        /* 1 ▸ physics */
        speed  = Math.min(speed + acceleration * dt, maxSpeed);
        float remaining = speed * dt;

        /* 2 ▸ march along poly-line */
        while (remaining > 0f && segIdx < segLen.size()) {
            float segRemain = segLen.get(segIdx) - sInSeg;

            if (remaining < segRemain) {            // stay in segment
                sInSeg += remaining;
                remaining = 0f;
            } else {                                // finish segment
                remaining -= segRemain;
                segIdx++;  sInSeg = 0f;
            }
            updateVisiblePoint();
        }

        /* 3 ▸ reached destination? */
        if (segIdx >= segLen.size()) {
            line.getEnd().getParentSystem().receivePacket(this);
        }
    }


    /* helpers -------------------------------------------------------- */
    private void initialisePath() {
        path   = line.getPath(5);                 // smoothness = 5
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0;  sInSeg = 0f;
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
