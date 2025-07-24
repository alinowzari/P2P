package model.packets;

import model.Packet;
import model.Port;

import static model.Type.OTHERS;

public class SecretPacket1 extends Packet {
    private float firstSpeed=2f;
    private float secondarySpeed=0.01f;
    public SecretPacket1() {
        super();
        type=OTHERS;
        size=4;
        speed=2f;
    }
    public void wrongPort(Port port) {}
}
