package model.packets;

import model.Packet;
import model.Port;
import model.System;                // for finishTraversal
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static model.Type.OTHERS;

/** Wrapper produced by VPN systems. */
public class ProtectedPacket<P extends Packet & MessengerTag> extends Packet {

    private final P inner;
    private int systemId;

    /* ── per-wire caches ─────────────────────────────────────────── */
    private List<Point> path;          // poly-line along the wire
    private List<Float> segLen;        // segment lengths
    private int   segIdx  = 0;
    private float sInSeg  = 0f;

    public ProtectedPacket(P inner) {
        this.inner = inner;
        this.type  = OTHERS;
        this.size  = inner.getSize() * 2;

        switch (ThreadLocalRandom.current().nextInt(1, 4)) {
            case 1 -> { speed = 4f;  acceleration = 0f; }
            case 2 -> { speed = 2.5f; acceleration = 0f; }
            case 3 -> { speed = 1f;  acceleration = 0.3f; }
        }
    }

    /* ------------------------------------------------------------- */
    public SecretPacket2<P> changePacket()    { return new SecretPacket2<>(this); }
    public P               unwrap()          { return inner; }
    @Override public void  wrongPort(Port p)  { /* inert */ }
    public void            setSystemId(int id){ systemId = id; }
    public int             getSystemId()     { return systemId; }

    /* ================= movement ================= */

    @Override
    public void advance(float dt) {
        if (line == null) return;                 // not travelling

        /* 0 ▸ ensure we built path+tables */
        if (path == null) initPathTables();

        /* 1 ▸ physics */
        speed += acceleration * dt;
        float remaining = speed * dt;

        /* 2 ▸ march along poly-line */
        while (remaining > 0f && segIdx < segLen.size()) {
            float segRemain = segLen.get(segIdx) - sInSeg;

            if (remaining < segRemain) {
                sInSeg += remaining;
                remaining = 0f;
            } else {
                remaining -= segRemain;
                segIdx++;  sInSeg = 0f;
            }
            updateVisiblePoint();
        }

        /* 3 ▸ reached destination? */
        if (segIdx >= segLen.size()){
            line.getEnd().getParentSystem().receivePacket(this);
        }
    }

    /* ---------- helpers ---------- */

    private void initPathTables() {
        path   = line.getPath(6);                 // same smoothness everywhere
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
    protected void resetPath() {            // called by beginTraversal(...)
        path = null; segLen = null;
        segIdx = 0; sInSeg = 0f;
    }
}
