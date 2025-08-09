package model;

import model.packets.*;
import model.ports.InputPort;
import model.ports.OutputPort;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class System {
    protected ArrayList<Packet> packets;
    protected List<InputPort> inputPorts;
    protected List<OutputPort> outputPorts;
    protected Point location;
    protected SystemManager systemManager;
    protected int id;
    protected int bigPacketCount;
    public System(Point location, List<InputPort> inputPorts, List<OutputPort> outputPorts, SystemManager systemManager, int id) {
        packets = new ArrayList<>();
        this.location = location;
        this.inputPorts = inputPorts;
        this.outputPorts = outputPorts;
        this.systemManager = systemManager;
        this.id = id;
    }
    public void handleBigPacketArrival(BigPacket bigPacket) {
        // 1) remove all existing packets
        List<Packet> copy = new ArrayList<>(packets);
        for (Packet packet : copy) {
            systemManager.removePacket(packet);
        }

        // 2) now clear and add the big packet
        packets.clear();
//        addPacket(bigPacket);
//        bigPacket.setSystem(this);
//        bigPacket.isNotMoving();
//        addingCoin(bigPacket);
        bigPacketCount++;

        // 3) optional destruction
        if (bigPacketCount == 3) {
            systemManager.removeSystem(this);
        }
    }

    public void addPacket(Packet packet) {
        packets.add(packet);
    }
    public void removePacket(Packet packet) {
        int id=packet.getId();
        for(Packet p : packets) {
            if(p.getId()==id) {
                packets.remove(p);
                break;
            }
        }
    }
    public List<OutputPort> getOutputPorts() {return outputPorts;}
    public List<InputPort> getInputPorts() {return inputPorts;}
    public int countPackets() {
        return packets.size();
    }
    public ArrayList<Packet> getPackets() {return packets;}
    public abstract void sendPacket();
    public abstract void receivePacket(Packet packet);

    public boolean isCompatible(Packet p, OutputPort op) {
        if (p instanceof ProtectedPacket
                || p instanceof SecretPacket1
                || p instanceof SecretPacket2) {
            return true;
        }
        return op.getType() == p.getType();
    }

    public OutputPort firstFreePort(ArrayList<OutputPort> ports) {
        for (OutputPort op : ports) {
            Line l = op.getLine();
            if (l != null && !l.isOccupied()) {
                return op;
            }
        }
        return null;
    }
    public Point getLocation() {return location;}
    public void addingCoin(Packet packet) {
        switch (packet) {
            case SquarePacket sp -> systemManager.addCoin(2);
            case TrianglePacket tp -> systemManager.addCoin(3);
            case InfinityPacket ip -> systemManager.addCoin(1);
            case SecretPacket2<?> secret -> systemManager.addCoin(4);
            case SecretPacket1 secret -> systemManager.addCoin(3);
            case ProtectedPacket<?> prp -> systemManager.addCoin(5);
            case BigPacket bp -> systemManager.addCoin(bp.size);
            default -> systemManager.addCoin(1);
        }
    }
    public SystemManager getSystemManager() {return systemManager;}
}
