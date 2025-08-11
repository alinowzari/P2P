package model.packets;

import model.Line;
import model.Packet;
import model.Port;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.BIG;

/**
 * BigPacket2
 * - Moves along the wire like other packets (segIdx / sInSeg marching).
 * - Adds a sideways wiggle (perpendicular to the segment).
 * - Every STEP_INTERVAL pixels of arc-length, the packet permanently rises
 *   by RISE_STEP pixels in screen space (negative Y).
 */
public class BigPacket2 extends BigPacket {

    /* ===== immutable identity ===== */
    private final int  originalSize = 10;
    private final int  colorId;

    public BigPacket2(int colorId) {
        this.colorId = colorId;
        this.size    = originalSize;
        this.type    = BIG;

        this.speed        = 3f;
        this.acceleration = 0f;
    }

    /* ===== per-wire caches ===== */
    private List<Point> path;      // smoothed polyline from the Line
    private List<Float> segLen;    // lengths of each segment
    private int   segIdx  = 0;     // current segment index
    private float sInSeg  = 0f;    // arc-length traveled inside current segment

    /* ===== wiggle ===== */
    private static final float WIGGLE_AMPL = 3f;   // ± px
    private static final float WIGGLE_FREQ = 6f;   // rad/s
    private float wigglePhase = 0f;

    /* ===== upward "stairs" ===== */
    private static final float STEP_INTERVAL = 50f; // travel distance between rises
    private static final float RISE_STEP     = 4f;  // pixels to go up each step (screen y–)
    private float totalS        = 0f;               // total distance traveled across segments
    private float nextRiseAt    = STEP_INTERVAL;    // next threshold to trigger a rise
    private float verticalOffset = 0f;              // accumulated upward offset (applied to y)

    /* ===== rendering helpers ===== */
    private Point basePoint;                        // on-wire point before wiggle/offset

    /* ===== API ===== */
    public int  getOriginalSize() { return originalSize; }
    public int  getColorId()      { return colorId; }
    @Override public void wrongPort(Port p) { /* irrelevant for big */ }

    /* ================================================================ */
    @Override
    public void advance(float dt) {
        if (line == null) return;
        if (path == null) initialisePath();

        // 1) Integrate along the wire
        float remaining = speed * dt;
        while (remaining > 0f && segIdx < segLen.size()) {
            float segRemain = segLen.get(segIdx) - sInSeg;

            if (remaining < segRemain) {
                sInSeg    += remaining;
                totalS    += remaining;
                remaining  = 0f;
            } else {
                sInSeg     = 0f;
                totalS    += segRemain;
                remaining -= segRemain;
                segIdx++;
            }
            updateBasePoint();

            // Apply periodic upward “stairs”
            while (totalS >= nextRiseAt) {
                verticalOffset -= RISE_STEP;     // up = negative screen Y
                nextRiseAt     += STEP_INTERVAL;
            }
        }

        // 2) Wiggle around the base point + apply global vertical offset
        wigglePhase += WIGGLE_FREQ * dt;
        applyWiggle();

        // 3) Arrival at destination
        if (segIdx >= segLen.size()) {
            // hand off to system; its receivePacket will clear the line & queue the packet
            isMoving = false;
            line.getEnd().getParentSystem().receivePacket(this);
        }
    }

    /* ===== helpers ===== */
    private void initialisePath() {
        path   = line.getPath(6);                         // same smoothness as others
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0; sInSeg = 0f;
        totalS = 0f; nextRiseAt = STEP_INTERVAL; verticalOffset = 0f;
        wigglePhase = 0f;

        basePoint = path.get(0);
        point     = basePoint;
        isMoving  = true;
    }

    private void updateBasePoint() {
        if (segIdx >= segLen.size()) return;
        float len = segLen.get(segIdx);
        float t   = (len == 0f) ? 0f : (sInSeg / len);
        basePoint = lerp(path.get(segIdx), path.get(segIdx + 1), t);
        point     = basePoint; // will be adjusted by wiggle()
    }
    private void applyWiggle() {
        if (segIdx >= segLen.size() || basePoint == null) return;

        Point a = path.get(segIdx);
        Point b = path.get(segIdx + 1);

        double dx = b.x - a.x, dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        if (len == 0) {
            point = new Point(basePoint.x, (int) Math.round(basePoint.y + verticalOffset));
            return;
        }
        dx /= len; dy /= len;

        // left-hand normal
        double nx = -dy, ny = dx;

        double sway = WIGGLE_AMPL * Math.sin(wigglePhase);
        point = new Point(
                (int) Math.round(basePoint.x + nx * sway),
                (int) Math.round(basePoint.y + ny * sway + verticalOffset)
        );
    }

    /* Clear caches when put onto a new line */
    @Override
    protected void resetPath() {
        path = null; segLen = null;
        segIdx = 0; sInSeg = 0f;

        basePoint = null;

        // reset decorations
        wigglePhase = 0f;
        totalS = 0f; nextRiseAt = STEP_INTERVAL; verticalOffset = 0f;
    }

    /* Splitting into bits (used by DistributionSystem / mergers) */
    public ArrayList<BitPacket> split() {
        ArrayList<BitPacket> list = new ArrayList<>();
        for (int i = 0; i < originalSize; i++)
            list.add(new BitPacket(this, i));
        return list;
    }
    @Override
    public void resetCenterDrift() {
        // snap to on-wire point (no wiggle/offset) and clear decorations
        updateBasePoint();           // recompute basePoint from segIdx/sInSeg
        verticalOffset = 0f;
        wigglePhase    = 0f;
        point          = basePoint;
    }
}
