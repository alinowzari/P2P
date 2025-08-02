package model.ports;

import model.Packet;
import model.Port;
import model.Type;
import model.System;
import model.systems.ReferenceSystem;

import java.awt.*;

public class InputPort extends Port {
    public InputPort(System system, Point location) {
        super(system, location);
    }
    public void transferPacket(Packet packet) {
        parentSystem.addPacket(packet);
        if(parentSystem instanceof ReferenceSystem){
            ((ReferenceSystem) parentSystem).addToReceivedCount();
            packet.doneMovement();
        }
    }
}
