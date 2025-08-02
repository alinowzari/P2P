
package model.packets;

import model.Packet;
import model.Port;

import static model.Type.OTHERS;

public final class SecretPacket2<P extends Packet & MessengerTag> extends Packet {
    private static final float BASE_SPEED = 0.2f;

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