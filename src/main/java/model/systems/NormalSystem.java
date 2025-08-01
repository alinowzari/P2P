package model.systems;

import model.Line;
import model.Packet;
import model.System;
import model.SystemManager;
import model.packets.ProtectedPacket;
import model.packets.*;
import model.ports.InputPort;
import model.ports.OutputPort;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NormalSystem extends System {
    public NormalSystem(Point location, List<InputPort> inputPorts, List<OutputPort> outputPorts, SystemManager systemManager, int id) {
        super(location, inputPorts, outputPorts, systemManager, id);
    }
    public void receivePacket(Packet packet){
        packets.add(packet);
        packet.setSystem(this);
    }
    public void sendPacket() {

        /* 1 ── nothing to do if we have no packets */
        if (packets.isEmpty()) return;

        Packet packet = packets.get(0);                 // FIFO policy
        if(packet.getDoneMovement())return;
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
