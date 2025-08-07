package model.packets;

import model.Packet;
import model.Port;
import model.System;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.BIT;
public class BitPacket extends Packet implements MessengerTag {

    /* ---------- immutable meta ---------- */
    private final int parentId;
    private final int fragmentIdx;
    private final int colorId;
    private final int parentLength;

    /* ---------- constructor ---------- */
    public BitPacket(BigPacket2 parent, int index) {
        this.parentId     = parent.getId();
        this.fragmentIdx  = index;
        this.colorId      = parent.getColorId();
        this.parentLength = parent.getOriginalSize();

        this.size         = 1;
        this.type         = BIT;

        this.speed        = 3f;      // same defaults as Infinity
        this.acceleration = 0.3f;
    }
    public BitPacket(BigPacket1 parent, int index) {
        this.parentId     = parent.getId();
        this.fragmentIdx  = index;
        this.colorId      = parent.getColorId();
        this.parentLength = parent.getOriginalSize();

        this.size         = 1;
        this.type         = BIT;

        this.speed        = 3f;      // same defaults as Infinity
        this.acceleration = 0.3f;
    }

    /* ---------- accessors ---------- */
    public int getParentId()      { return parentId; }
    public int getFragmentIdx()   { return fragmentIdx; }
    public int getColorId()       { return colorId; }
    public int getParentLength()  { return parentLength; }

    /* ---------- wrong-port reaction ---------- */
    @Override
    public void wrongPort(Port p) {
        if (p.getType() != BIT)           // slow, stop, reverse
            acceleration = -acceleration;
    }

    /* =========================================================================
       Movement bookkeeping (exactly the same pattern used in InfinityPacket)
       ====================================================================== */
    private List<Point> path;         // poly-line from the Line
    private List<Float> segLen;       // segment lengths
    private int   segIdx  = 0;        // current segment
    private float sInSeg  = 0f;       // distance along segIdx
    private final float maxSpeed = 5f;

    @Override public void advance(float dt) {
        if (line == null) { isMoving = false; return; }
        if (path == null) initPathTables();

        /* 1 ▸ physics */
        speed += acceleration * dt;
        speed = Math.max(-maxSpeed, Math.min(maxSpeed, speed));

        float remaining = speed * dt;          // may be negative

        /* 2 ▸ march along poly-line (forward or backward) */
        while (remaining != 0f &&
                segIdx >= 0 &&
                segIdx < segLen.size()) {

            if (remaining > 0f) {                          // → forward
                float segRemain = segLen.get(segIdx) - sInSeg;
                if (remaining < segRemain) {
                    sInSeg += remaining;
                    remaining = 0f;
                } else {
                    remaining -= segRemain;
                    segIdx++; sInSeg = 0f;
                }
            } else {                                       // ← backward
                float segPassed = sInSeg;
                if (-remaining < segPassed) {
                    sInSeg += remaining;                   // remaining < 0
                    remaining = 0f;
                } else {
                    remaining += segPassed;
                    segIdx--;
                    if (segIdx >= 0)
                        sInSeg = segLen.get(segIdx);
                }
            }
            updateVisiblePoint();
        }

        /* 3 ▸ arrival at either port */
        if (segIdx >= segLen.size()) {
            line.getEnd().getParentSystem().receivePacket(this);
        } else if (segIdx < 0) {
            line.getStart().getParentSystem().receivePacket(this);
        }
    }

    /* ---------- helpers ---------- */
    private void initPathTables() {
        path   = line.getPath(6);
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0;  sInSeg = 0f;
        point  = path.get(0);
        isMoving = true;
    }
    private void updateVisiblePoint() {
        if (segIdx >= 0 && segIdx < segLen.size())
            point = lerp(path.get(segIdx), path.get(segIdx + 1),
                    sInSeg / segLen.get(segIdx));
    }
    @Override
    protected void resetPath() {
        path = null; segLen = null; segIdx = 0; sInSeg = 0f;
    }
    public int parentLength() { return parentLength; }
}
