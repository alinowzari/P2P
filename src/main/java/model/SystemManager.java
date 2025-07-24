package model;

import model.packets.BigPacket;
import model.packets.BitPacket;
import model.packets.ProtectedPacket;
import model.packets.SecretPacket2;
import model.systems.SpySystem;
import model.systems.VpnSystem;

import java.util.*;

public class SystemManager {
    ArrayList<System> systems;
    ArrayList<SpySystem> spySystems;
    ArrayList<VpnSystem> vpnSystems;
    private final Random rng = new Random();
    public ArrayList<Packet> allPackets;
    public ArrayList<Line> allLines;
    private HashMap<Integer, ArrayList<BitPacket>> bigPackets;
    public SystemManager() {
        systems = new ArrayList<>();
        spySystems = new ArrayList<>();
        vpnSystems = new ArrayList<>();
        allPackets = new ArrayList<>();
    }

    public void addSystem(System system) {
        systems.add(system);
        if (system instanceof SpySystem spy) {
            spySystems.add(spy);
        }
        if (system instanceof VpnSystem vpn) {
            vpnSystems.add(vpn);
        }
    }

    public void removeSystem(System system) {
        systems.remove(system);

        if (system instanceof SpySystem spy) {
            spySystems.remove(spy);
        }
        if (system instanceof VpnSystem vpn) {
            vpnSystems.remove(vpn);
            handleVpnDestruction(vpn.getId());
        }
    }
    public void addPacket(Packet packet) {
        allPackets.add(packet);
        if(packet instanceof BigPacket) {
            bigPackets.put(packet.getId(), ((BigPacket) packet).split() );
        }
    }
    public void removePacket(Packet packet) {allPackets.remove(packet);}


    public void handleVpnDestruction(int vpnId) {
        // 1) First unwrap in every system’s queue
        for (System sys : systems) {
            ListIterator<Packet> it = sys.getPackets().listIterator();
            while (it.hasNext()) {
                Packet p = it.next();
                Packet inner = null;

                if (p instanceof ProtectedPacket<?> prot && prot.getSystemId() == vpnId) {
                    inner = prot.unwrap();
                }
                else if (p instanceof SecretPacket2<?> s2 && s2.getSystemId() == vpnId) {
                    inner = s2.unwrap();
                }

                if (inner != null) {
                    it.set(inner);
                }
            }
        }

        // 2) Then unwrap in the global allPackets list
        ListIterator<Packet> pit = allPackets.listIterator();
        while (pit.hasNext()) {
            Packet p = pit.next();
            Packet inner = null;

            if (p instanceof ProtectedPacket<?> prot && prot.getSystemId() == vpnId) {
                inner = prot.unwrap();
            }
            else if (p instanceof SecretPacket2<?> s2 && s2.getSystemId() == vpnId) {
                inner = s2.unwrap();
            }

            if (inner != null) {
                pit.set(inner);
            }
        }
    }
    public SpySystem getRandomSpySystem() {
        if (spySystems.isEmpty()) {
            return null;
        }
        return spySystems.get(rng.nextInt(spySystems.size()));
    }
    public ArrayList<System> getAllSystems() {
        return systems;
    }
    public ArrayList<SpySystem> getAllSpySystems() {
        return spySystems;
    }
    public ArrayList<VpnSystem> getAllVpnSystems() {
        return vpnSystems;
    }
    public HashMap<Integer, ArrayList<BitPacket>> getBigPackets() {
        return bigPackets;
    }
    public void addLine(Line line) {allLines.add(line);}
    public void removeLine(Line line) {allLines.remove(line);}
}
//    public void updateSpySystems() {
//        spySystems = systems.stream()                 // walk the master list
//                .filter(SpySystem.class::isInstance) // keep only spies
//                .map(SpySystem.class::cast)          // cast to SpySystem
//                .collect(Collectors.toCollection(ArrayList::new));
//    }
