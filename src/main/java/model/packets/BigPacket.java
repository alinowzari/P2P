package model.packets;

import model.Packet;
import model.Port;
import model.Type;

import java.awt.*;
import java.util.ArrayList;

public class BigPacket extends Packet {
    private final int originalSize;          // immutable “N”
    private final int colorId;               // so bits share a colour

    public BigPacket(int size, int colorId) {
        super();
        this.size        = size;             // live size (may drop if split)
        this.originalSize = size;
        this.colorId     = colorId;
        this.type        = Type.BIG;         // invent if absent
    }

    public int getOriginalSize() { return originalSize; }
    public int getColorId()      { return colorId; }
    @Override
    public void wrongPort(Port p) { /* no-op – compatibility meaningless */ }

    /** split into N BitPackets */
    public ArrayList<BitPacket> split() {
        ArrayList<BitPacket> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(new BitPacket(this, i));
        }
        return list;
    }
}

