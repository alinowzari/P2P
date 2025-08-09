package model.packets;

import model.Line;
import model.Packet;
import model.Port;
import model.System;
import model.systems.DistributionSystem;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.BIG;

/**
 * “Big-2” packet:
 * • size 8, carries a colour id (so resulting bits share a colour)
 * • moves along the wire like the other packets (segIdx / sInSeg scheme)
 * • never splits in flight; splits ONLY when the destination system is a
 *   DistributionSystem (handled in advance()).
 * • rendered with a gentle perpendicular wiggle so its centre drifts
 *   a few pixels left/right of the line, simulating an impact.
 */
public class BigPacket2 extends BigPacket {

    /* ---------- immutable meta-data ---------- */
    private final int originalSize = 10;
    private final int colorId;

    /* ---------- constructor ---------- */
    public BigPacket2(int colorId) {
        this.colorId   = colorId;
        this.size      = originalSize;
        this.type      = BIG;

        this.speed        = 3f;   // feel free to tweak
        this.acceleration = 0f;
    }

    /* ---------- per-wire caches ---------- */
    private List<Point> path;        // line poly-line
    private List<Float> segLen;      // segment lengths
    private int   segIdx  = 0;
    private float sInSeg  = 0f;

    /* ---------- visual wiggle ---------- */
    private static final float WIGGLE_AMPL = 3f;      // ± pixels
    private static final float WIGGLE_FREQ = 6f;      // rad · s⁻¹   (≈ 1 Hz)
    private float wigglePhase = 0f;

    /* ========== API ========== */
    public int getOriginalSize() { return originalSize; }
    public int getColorId()      { return colorId; }

    @Override public void wrongPort(Port p) { /* compatibility irrelevant */ }

    /* ========== per-frame update ========== */
    @Override
    public void advance(float dt) {
        if (line == null) return;
        if (path == null) initialisePath();

        /* 1 ▸ integrate arc-length ------------------------------------------------ */
        float remaining = speed * dt;
        while (remaining > 0f && segIdx < segLen.size()) {
            float segRemain = segLen.get(segIdx) - sInSeg;

            if (remaining < segRemain) {
                sInSeg += remaining;
                remaining = 0f;
            } else {
                remaining -= segRemain;
                segIdx++;  sInSeg = 0f;
            }
            updatePoint();           // on-wire point (before wiggle)
        }

        /* 2 ▸ apply visual sway --------------------------------------------------- */
        wigglePhase += WIGGLE_FREQ * dt;
        applyWiggle();

        /* 3 ▸ arrival check ------------------------------------------------------- */
        if (segIdx >= segLen.size()) {
            isMoving = false;
            line.getEnd().getParentSystem().receivePacket(this);
        }
    }

    /* ---------- helper: build path / tables once ---------- */
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

    /* ---------- helper: update on-line point ---------- */
    private void updatePoint() {
        if (segIdx >= segLen.size()) return;
        point = lerp(
                path.get(segIdx),
                path.get(segIdx + 1),
                sInSeg / segLen.get(segIdx));
    }

    /* ---------- helper: apply perpendicular wiggle ---------- */
    private void applyWiggle() {
        if (segIdx >= segLen.size()) return;          // no active segment

        Point a = path.get(segIdx);
        Point b = path.get(segIdx + 1);

        double dx = b.x - a.x, dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        if (len == 0) return;
        dx /= len;  dy /= len;                        // unit direction

        // left-hand normal
        double nx = -dy, ny = dx;

        double offset = WIGGLE_AMPL * Math.sin(wigglePhase);
        point = new Point(
                (int) Math.round(point.x + nx * offset),
                (int) Math.round(point.y + ny * offset));
    }

    /* ---------- clear caches when packet put onto a new line ---------- */
    @Override
    protected void resetPath() {
        path = null; segLen = null;
        segIdx = 0; sInSeg = 0f;
        wigglePhase = 0f;
    }

    /* ---------- splitting logic used by DistributionSystem ---------- */
    public ArrayList<BitPacket> split() {
        ArrayList<BitPacket> list = new ArrayList<>();
        for (int i = 0; i < originalSize; i++)
            list.add(new BitPacket(this, i));
        return list;
    }
}
