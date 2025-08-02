package model;

import model.packets.BigPacket;
import model.packets.BitPacket;
import model.packets.ProtectedPacket;
import model.packets.SecretPacket2;
import model.ports.InputPort;
import model.ports.OutputPort;
import model.systems.SpySystem;
import model.systems.VpnSystem;

import java.awt.*;
import java.util.*;
import java.util.List;

import static model.Packet.dt;

public class SystemManager {
    ArrayList<System> systems;
    ArrayList<SpySystem> spySystems;
    ArrayList<VpnSystem> vpnSystems;
    private final Random rng = new Random();
    private final Set<Integer> packetIds = new HashSet<>();
    public ArrayList<Packet> allPackets;
    public ArrayList<Line> allLines;
    private HashMap<Integer, ArrayList<BitPacket>> bigPackets;
    private boolean isReady;
    private boolean launched;
    public boolean test;
    private static final float DT = dt;
    public int coinCount = 0;
    public SystemManager() {
        systems = new ArrayList<>();
        spySystems = new ArrayList<>();
        vpnSystems = new ArrayList<>();
        allPackets = new ArrayList<>();
        allLines = new ArrayList<>();
        bigPackets = new HashMap<>();
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
    public void addPacket(Packet p) {
        if (packetIds.add(p.getId())) {        // returns false if ID already present
            allPackets.add(p);
            if (p instanceof BigPacket big)
                bigPackets.put(big.getId(), big.split());
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

    public boolean isReady() {return isReady;}
    public boolean isLaunched() {return launched;}
    public void launchPackets() { launched = true; }
    public void addCoin(int plus){coinCount+=plus;}
    public void update(float dt) {
        /* -------- 1: move packets along their lines -------- */
//        for (Line l : allLines) {
//            Packet pkt = l.getMovingPacket();
//            if (pkt == null) continue;
//
//            /* integrate velocity */
//            float v  = pkt.getSpeed() + pkt.getAcceleration() * DT;
//            float t  = pkt.getProgress() + v * DT;
//            pkt.setSpeed(v);
//
//            java.lang.System.out.printf("this is packet count "+ allPackets.size());
//            List<Point> path = l.getPath(6);     // path already includes bends
//            if (t >= 1f) {
//                /* ── ARRIVAL ───────────────────────────────────────────── */
//                pkt.setProgress(1f);
//                pkt.setPoint(path.get(path.size() - 1));
//
//                l.removeMovingPacket();      // frees the line
//                l.getEnd().getParentSystem().receivePacket(pkt);
//                pkt.setSystem(l.getEnd().getParentSystem());
//
//            } else {
//                /* ── STILL TRAVELLING ──────────────────────────────────── */
//                pkt.setProgress(t);
//                pkt.setPoint(along(path, t));     // interpolate along bends
//            }
//        }
        for (Line l : allLines) {
            Packet pkt = l.getMovingPacket();
            if (pkt != null) {
                pkt.advance(dt);              // polymorphic – each subclass decides
            }
        }

        /* -------- 2: try to send from every fully-wired system -------- */
        isReady = systems.stream().allMatch(this::allOutputsConnected)
                && systems.stream().allMatch(this::allInputsConnected);

        if (launched && isReady) {
            for (System sys : systems) {
                if (!sys.getPackets().isEmpty()) {
                    sys.sendPacket();
                }
            }
        }
    }
    private boolean allOutputsConnected(System s) {
        for (OutputPort op : s.getOutputPorts())
            if (op.getLine() == null)
                return false;
        return true;
    }
    private boolean allInputsConnected(System s) {
        for (InputPort ip : s.getInputPorts()){
            if(ip.getLine() == null){
                return false;
            }
        }
        return true;
    }
    private Point along(List<Point> pts, float t) {
        if (pts.size() < 2) return pts.get(0);

        /* 1. total length & per-segment lengths */
        double total = 0;
        double[] segLen = new double[pts.size()-1];
        for (int i=0;i<segLen.length;i++){
            total += segLen[i] = pts.get(i).distance(pts.get(i+1));
        }
        double goal = t * total;

        /* 2. walk segments until we reach ‘goal’ */
        double run = 0;
        for (int i=0;i<segLen.length;i++){
            if (run + segLen[i] >= goal) {
                double localT = (goal - run) / segLen[i];
                Point a = pts.get(i), b = pts.get(i+1);
                int x = (int)Math.round(a.x + localT * (b.x - a.x));
                int y = (int)Math.round(a.y + localT * (b.y - a.y));
                return new Point(x, y);
            }
            run += segLen[i];
        }
        return pts.get(pts.size()-1);             // fallback
    }
}