//package model.packets;
//
//import model.Packet;
//import model.Port;
//import model.SystemManager;
//
//import java.util.Optional;
//
//import static javax.swing.text.html.HTML.Tag.DT;
//import static model.Type.OTHERS;
//
//public final class SecretPacket2<P extends Packet & ProtectedTag> extends Packet {
//
//    /* ---------- immutable configuration ---------- */
//    private static final float BASE_SPEED        = 2.0f;   // m/s – tweak to taste
//    private static final float COMFORT_DISTANCE  = 70f;    // pixels or metres
//    private static final float MAX_ADJUST        = 0.8f;   // ± m/s cap
//    private static final int   COINS_PER_HOP     = 4;
//
//    /* ---------- wrapped payload ---------- */
//    private final Packet inner;
//    private int systemId;
//    /* ---------- ctor ---------- */
//    public SecretPacket2(Packet inner) {
//        super();
//        type=OTHERS;
//        this.inner = inner;
//
//        this.size  = 6;                        // as per table
//        this.speed = BASE_SPEED;
//        this.acceleration = 0;                 // fixed-speed (spec’s “سرعت ثابت”)
//    }
//    public void setSystemId(int systemId) {this.systemId = systemId;}
////    /* ---------- behaviour hooks ---------- */
////
////    /** spacing-aware advance; call exactly once per simulation frame */
////    public void advance(SystemManager mgr) {
////        // 1) compute speed tweak to approach COMFORT_DISTANCE on *this* line
////        float tweak = spacingAdjustment(mgr);
////        speed = BASE_SPEED + tweak;          // clamp done inside spacingAdjustment
////
////        // 2) apply the usual Euler integration
////        super.advance(DT);
////    }
////
////    /** returns a small Δv ∈ [-MAX_ADJUST … +MAX_ADJUST] */
////    private float spacingAdjustment(SystemManager mgr) {
////        Optional<Float> maybeDist = mgr.distanceToNearestPacketOnLine(this, line);
////        if (maybeDist.isEmpty()) return 0f;          // alone on the wire
////
////        float delta = maybeDist.get() - COMFORT_DISTANCE;  // +ve = too far
////        float adj   = delta * 0.01f;                       // scale factor → tweak
////        return Math.max(-MAX_ADJUST, Math.min(MAX_ADJUST, adj));
////    }
//
//    /** Secret packets ignore wrong-port semantics. */
//     public void wrongPort(Port port) { /* no-op */ }
//
//    /* ---------- unwrap helper ---------- */
//    public Packet unwrap() { return inner; }
//}
package model.packets;

import model.Packet;
import model.Port;

import static model.Type.OTHERS;

public final class SecretPacket2<P extends Packet & MessengerTag> extends Packet {
    private static final float BASE_SPEED = 2.0f;

    private final ProtectedPacket<P> inner;
    private int systemId;

    public SecretPacket2(ProtectedPacket<P> inner) {
        super();
        this.type = OTHERS;
        this.inner = inner;

        this.size  = 6;
        this.speed = BASE_SPEED;
        this.acceleration = 0;
    }

    /* ---------------- API ---------------- */
    public ProtectedPacket<P> unwrap()       { return inner; }
    public void wrongPort(Port p)            { /* no‑op */ }
    public void setSystemId(int id)          { this.systemId = id; }
    public int getSystemId() { return systemId; }
}