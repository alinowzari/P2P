package model.packets;

import model.Packet;
import model.Port;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static model.Type.OTHERS;

public class SecretPacket1 extends Packet {

    /* design constants ------------------------------------------------ */
    private static final float FAST  = 2f;      // px / s
    private static final float CRAWL = 1f;

    /* per-wire state --------------------------------------------------- */
    private List<Point> path;
    private List<Float> segLen;
    private int   segIdx = 0;
    private float sInSeg = 0f;

    public SecretPacket1() {
        size  = 4;
        type  = OTHERS;
        speed = FAST;            // will be overwritten on first hop
        acceleration = 0f;
    }

    @Override public void wrongPort(Port p) {/* inert – always compatible */}

    /* =================== movement =================== */
    @Override
    public void advance(float dt) {
        if (line == null) return;              // not travelling

        /* 0 ▸ first frame on this wire → build geometry + pick speed */
        if (path == null) {
            initPathTables();

            boolean targetBusy =
                    !line.getEnd().getParentSystem().getPackets().isEmpty();
            speed = targetBusy ? CRAWL : FAST;
        }

        /* 1 ▸ march along poly-line (straight walk, no accel) */
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
            updateVisiblePoint();
        }

        /* 2 ▸ reached destination ? */
        if (segIdx >= segLen.size()) {
            line.getEnd().getParentSystem().receivePacket(this);
        }
    }

    /* helpers ---------------------------------------------------------- */
    private void initPathTables() {
        path   = line.getPath(6);
        segLen = new ArrayList<>(path.size() - 1);
        for (int i = 0; i < path.size() - 1; i++)
            segLen.add((float) path.get(i).distance(path.get(i + 1)));

        segIdx = 0;  sInSeg = 0f;
        point  = path.get(0);
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
        path = null; segLen = null;
        segIdx = 0; sInSeg = 0f;
    }
}
