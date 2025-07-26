package model.systems;

import model.*;
import model.System;
import model.packets.ProtectedPacket;
import model.packets.SecretPacket1;
import model.packets.SecretPacket2;
import model.ports.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;

public class SpySystem extends System {
    private final Random rng = new Random();
    public SpySystem(Point location, List<InputPort> inputPorts, List<OutputPort> outputPorts, SystemManager systemManager, int id) {
        super(location, inputPorts, outputPorts, systemManager, id);
    }
    public void receivePacket(Packet packet) {
        if(!(packet instanceof SecretPacket2 || packet instanceof SecretPacket1)) {
            packets.add(packet);
            packet.setSystem(this);
        }
        else{
            systemManager.removePacket(packet);
        }
        addingCoin(packet);
    }
    public void changeSystem() {
        if (packets.isEmpty()) return;

        // pop the head packet
        Packet head = packets.remove(0);

        // 1) Protected/secret → just send it out normally
        if (head instanceof ProtectedPacket) {
            // re-queue at front so sendPacket picks it up
            packets.add(0, head);
            sendPacket();
            return;
        }

        // 2) ∞, ■ or ▲ → forward to another spy then dispatch there
        if (isSpecialShape(head)) {
            // find all other spies
            ArrayList<SpySystem> all = systemManager.getAllSpySystems();
            ArrayList<SpySystem> others = new ArrayList<>(all);
            others.removeIf(s -> s.id == this.id);

            if (!others.isEmpty()) {
                SpySystem peer = others.get(rng.nextInt(others.size()));
                // directly hand off
                peer.receivePacket(head);
                peer.sendPacket();    // immediately send it from their queue
                return;
            }
            // else no other spy → fall through to normal send
        }

        // 3) Anything else: re-queue and send normally
        packets.add(0, head);
        sendPacket();
    }
    public void sendPacket() {
        // identical routing logic to NormalSystem
//        if (packets.isEmpty()) {
//            return;
//        }

        Packet packet = packets.get(0);

        ArrayList<OutputPort> compatible   = new ArrayList<>();
        ArrayList<OutputPort> incompatible = new ArrayList<>();

        for (OutputPort op : outputPorts) {
            if (isCompatible(packet, op)) compatible.add(op);
            else                         incompatible.add(op);
        }

        OutputPort chosen = firstFreePort(compatible);
        if (chosen == null) chosen = firstFreePort(incompatible);

        if (chosen != null) {
            chosen.movePacketThrow(packet);
            packets.remove(packet);
        }
    }

    private boolean isSpecialShape(Packet packet) {
        Type t = packet.getType();
        if(t.equals(Type.SQUARE) || t.equals(Type.TRIANGLE) || t.equals(Type.INFINITY)) {
            return true;
        }
        return false;
    }
}
