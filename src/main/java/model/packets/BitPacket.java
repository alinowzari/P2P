package model.packets;

import model.Packet;
import model.Port;
import model.Type;

public class BitPacket extends Packet implements MessengerTag {
    private final int parentId;
    private final int fragmentIndex;   // 0 … N-1
    private final int colorId;
    private final int parentLength;
    public BitPacket(BigPacket parent, int index) {
        super();
        this.parentId      = parent.getId();
        this.fragmentIndex = index;
        this.size          = 1;
        this.type          = Type.BIT;
        this.colorId       = parent.getColorId();
        this.parentLength  = parent.getOriginalSize();
    }

    public int getParentId()   { return parentId; }
    public int getFragmentIdx(){ return fragmentIndex; }
    public int getColorId()   { return colorId; }
    public int getParentLength() { return parentLength; }
    @Override
    public void wrongPort(Port p) { /* messenger logic */ }
}