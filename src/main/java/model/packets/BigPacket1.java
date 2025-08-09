// src/main/java/model/packets/BigPacket1.java
package model.packets;

import model.Packet;
import model.Port;
import model.Type;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BigPacket1 extends BigPacket {

    /* ===== immutable identity ===== */
    private static final float BASE_SPEED  = 2f;   // straight-line cruise
    private static final float BEND_BOOST  = 1.2f; // Δv per second on bends
    private static final float MAX_SPEED   = 5f;
    private static final double BEND_EPS   = Math.cos(Math.toRadians(10)); // angle threshold

    private final int originalSize = 8;
    private final int colorId;

    /* ===== per-wire caches ===== */
    private List<Point> path;
    private List<Float> segLen;
    private int   segIdx = 0;
    private float sInSeg = 0f;

    public BigPacket1(int colorId) {
        this.colorId = colorId;
        size         = originalSize;
        type         = Type.BIG;
        speed        = BASE_SPEED;
        acceleration = 0f;              // updated dynamically in advance()
    }

    public int getOriginalSize() { return originalSize; }
    public int getColorId()      { return colorId; }

    @Override public void wrongPort(Port p) { /* always compatible */ }

    /* ================================================================= */
    @Override
    public void advance(float dt) {
        if (line == null) return;          // not travelling

        if (path == null) initialisePath();   // caches geometry

        /* ----- 1: determine if we are on a bend ----- */
        acceleration = 0f;                  // default
        if (segIdx > 0 && segIdx < segLen.size() - 1) {
            Point a = path.get(segIdx - 1);
            Point b = path.get(segIdx);
            Point c = path.get(segIdx + 1);

            double ux = b.x - a.x, uy = b.y - a.y;
            double vx = c.x - b.x, vy = c.y - b.y;
            double uLen = Math.hypot(ux, uy);
            double vLen = Math.hypot(vx, vy);

            if (uLen > 0 && vLen > 0) {
                double cos = (ux * vx + uy * vy) / (uLen * vLen);
                if (cos < BEND_EPS) {           // i.e. angle > ~10°
                    acceleration = BEND_BOOST;  // give it a push
                }
            }
        }

        /* ----- 2: physics ----- */
        speed = Math.min(speed + acceleration * dt, MAX_SPEED);
        float remaining = speed * dt;

        /* ----- 3: march along the poly-line ----- */
        while (remaining > 0f && segIdx < segLen.size()) {
            float segRemain = segLen.get(segIdx) - sInSeg;

            if (remaining < segRemain) {               // stay inside segment
                sInSeg += remaining;
                remaining = 0f;
            } else {                                   // finish segment
                remaining -= segRemain;
                segIdx++;  sInSeg = 0f;
            }
            updatePoint();
        }

        /* ----- 4: arrival at destination port ----- */
        if (segIdx >= segLen.size()) {
            isMoving = false;
            getLine().getEnd().getParentSystem().receivePacket(this);
        }
    }

    /* ===== helpers ===== */
    private void initialisePath() {
        path   = line.getPath(6);                 // smoothness = 6 samples
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0;
        sInSeg = 0f;
        point  = path.get(0);
        isMoving = true;
    }

    private void updatePoint() {
        if (segIdx >= segLen.size()) return;
        point = lerp(
                path.get(segIdx),
                path.get(segIdx + 1),
                sInSeg / segLen.get(segIdx));
    }
    public ArrayList<BitPacket> split() {
        ArrayList<BitPacket> list = new ArrayList<>();
        for (int i = 0; i < originalSize; i++)
            list.add(new BitPacket(this, i));
        return list;
    }
    @Override protected void resetPath() {
        path   = null;
        segLen = null;
        segIdx = 0;
        sInSeg = 0f;
    }
}
