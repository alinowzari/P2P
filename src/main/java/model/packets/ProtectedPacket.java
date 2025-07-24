package model.packets;

import model.Packet;
import model.Port;
import java.util.concurrent.ThreadLocalRandom;

import static model.Type.OTHERS;

public class ProtectedPacket<P extends Packet & MessengerTag> extends Packet {
    private final P innerPacket;
    private int systemId;

    public ProtectedPacket(P innerPacket) {
        super();
        this.type = OTHERS;
        this.innerPacket = innerPacket;
        this.size = innerPacket.getSize() * 2;

        switch (ThreadLocalRandom.current().nextInt(1, 4)) {
            case 1 -> { speed = 4f;  acceleration = 0.0f; }
            case 2 -> { speed = 2.5f; acceleration = 0.0f; }
            case 3 -> { speed = 1f;  acceleration = 0.3f; }
        }
    }

    /* ---------------- API ---------------- */
    public SecretPacket2<P> changePacket() { return new SecretPacket2<>(this); }
    public P unwrap()               { return innerPacket; }
    public void wrongPort(Port p)   { /* no‑op */ }
    public void setSystemId(int id) { this.systemId = id; }
    public int getSystemId() { return systemId; }
}
