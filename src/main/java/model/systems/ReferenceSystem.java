package model.systems;
import model.Line;
import model.Packet;
import model.SystemManager;
import model.packets.ProtectedPacket;
import model.packets.SecretPacket1;
import model.packets.SecretPacket2;
import model.ports.*;
import model.System;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ReferenceSystem extends System {
    int receivedCount = 0;
    public ReferenceSystem(Point location, List<InputPort> inputPorts, List<OutputPort> outputPorts, SystemManager systemManager, int id) {
        super(location, inputPorts, outputPorts, systemManager, id);
    }
    public void receivePacket(Packet packet) {
        receivedCount++;
        systemManager.removePacket(packet);
        packet.getLine().removeMovingPacket();
        packet.setLine(null);
        packet.setSystem(this);
        packets.remove(packet);
        packet.isNotMoving();
        packet.doneMovement();
        addingCoin(packet);
        java.lang.System.out.println("Received packet " + packet.getId() + " status " + packet.getDoneMovement());

    }

    /**
     * Exactly the same send logic as in NormalSystem
     */
    @Override
    public void sendPacket() {
        // nothing to do if we have no packets
        if (packets.isEmpty()) return;

        Packet packet = packets.get(0);  // FIFO
        if(packet.getDoneMovement())return;
        ArrayList<OutputPort> compatible   = new ArrayList<>();
        ArrayList<OutputPort> incompatible = new ArrayList<>();
        for (OutputPort op : outputPorts) {
            if (isCompatible(packet, op)) compatible.add(op);
            else                   incompatible.add(op);
        }

        // look for a free line, first among compatible ports
        OutputPort chosen = firstFreePort(compatible);
        if (chosen == null)
            chosen = firstFreePort(incompatible);

        // if we found one, inject the packet onto the line
        if (chosen != null) {
            chosen.movePacketThrow(packet);
            packets.remove(packet);
        }
    }
    public int getReceivedCount() {
        return receivedCount;
    }
    public void addToReceivedCount() {receivedCount++;}
}
