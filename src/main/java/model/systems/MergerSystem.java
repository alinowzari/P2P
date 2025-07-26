package model.systems;

import model.Line;
import model.Packet;
import model.SystemManager;
import model.packets.*;
import model.ports.*;
import model.System;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

public class MergerSystem extends System{
    private final HashMap<Integer, ArrayList<BitPacket>> bins = new HashMap<>();
    public MergerSystem(Point location, List<InputPort> inputPorts, List<OutputPort> outputPorts, SystemManager systemManager, int id) {
        super(location, inputPorts, outputPorts, systemManager, id);

    }
    public void receivePacket(Packet packet) {
        if (packet instanceof BitPacket bit) {
            bins.computeIfAbsent(bit.getParentId(), k -> new ArrayList<>())
                    .add(bit);
            checkMerge(bit.getParentId());
            return;     // don’t queue – they’re consumed
        }

        packets.add(packet); // ordinary packet
        packet.setSystem(this);
        addingCoin(packet);
    }

    private void checkMerge(int parentId) {
        ArrayList<BitPacket> list = bins.get(parentId);
        if (list == null) return;

        int expected = list.get(0).getParentLength();

        if (list.size() == expected) {
            // remove fragments from global list
            for (BitPacket bp : list) systemManager.removePacket(bp);

            BigPacket big = new BigPacket(expected, list.get(0).getColorId());
            systemManager.addPacket(big);
            packets.add(big);          // now route like normal

            bins.remove(parentId);
        }
    }
    public void sendPacket() {

        /* 1 ── nothing to do if we have no packets */
        if (packets.isEmpty()) return;

        Packet packet = packets.get(0);                 // FIFO policy

        /* 2 ── partition output ports into compatible / incompatible */
        ArrayList<OutputPort> compatible   = new ArrayList<>();
        ArrayList<OutputPort> incompatible = new ArrayList<>();

        for (OutputPort op : outputPorts) {
            if (isCompatible(packet, op))
                compatible.add(op);
            else
                incompatible.add(op);
        }

        /* 3 ── look for a free line, first among compatible ports */
        OutputPort chosen = firstFreePort(compatible);
        if (chosen == null)                      // fall back to non-compatible
            chosen = firstFreePort(incompatible);

        /* 4 ── if we found one, inject the packet onto the line */
        if (chosen != null) {
            chosen.movePacketThrow(packet);
            packets.remove(packet);
        }
        /* else: every line is busy → leave packet queued */
    }
}
