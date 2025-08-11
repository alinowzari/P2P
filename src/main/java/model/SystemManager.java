package model;

import model.packets.BigPacket;
import model.packets.BitPacket;
import model.packets.ProtectedPacket;
import model.packets.SecretPacket2;
import model.ports.InputPort;
import model.ports.OutputPort;
import model.systems.AntiTrojanSystem;
import model.systems.SpySystem;
import model.systems.VpnSystem;

import java.awt.*;
import java.util.*;
import java.util.List;

import static model.Packet.dt;

public class SystemManager {
    private static final int EFFECT_RADIUS_PX = 10;
    private static final long NANO_20S = 20_000_000_000L;
    ArrayList<System> systems;
    ArrayList<SpySystem> spySystems;
    ArrayList<VpnSystem> vpnSystems;
    private final Random rng = new Random();
    private final Set<Integer> packetIds = new HashSet<>();
    public ArrayList<Packet> allPackets;
    private static final int SAFE_RADIUS = 35;
    public ArrayList<Line> allLines;
    private HashMap<Integer, ArrayList<BitPacket>> bigPackets;
    private boolean isReady;
    private boolean launched;
    private boolean isLevelPassed;
    private int firstCountPacket;
    private int receivedPacket;
    private static final float DT = dt;
    public int coinCount = 0;
    public static GameStatus gameStatus;
    private String levelName;          // NEW
    private boolean winCommitted = false;
    public SystemManager(GameStatus gameStatus) {
        systems = new ArrayList<>();
        spySystems = new ArrayList<>();
        vpnSystems = new ArrayList<>();
        allPackets = new ArrayList<>();
        allLines = new ArrayList<>();
        bigPackets = new HashMap<>();
        this.gameStatus = gameStatus;
        isLevelPassed = false;
        winCommitted = gameStatus.isLevelPassed(levelName);
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
        // safely remove all incident lines
        Iterator<Line> it = allLines.iterator();
        while (it.hasNext()) {
            Line line = it.next();
            boolean incident = system.getInputPorts().contains(line.getEnd())
                    || system.getOutputPorts().contains(line.getStart());
            if (incident) {
                Packet mp = line.getMovingPacket();
                if (mp != null) {
                    removePacket(mp);      // will also pull it out of any system queue
//                    line.setMovingPacket(null);
                }
                it.remove();               // <- safe removal during iteration
            }
        }

        systems.remove(system);
        if (system instanceof SpySystem spy) spySystems.remove(spy);
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
    public void removePacket(Packet packet) {
        allPackets.remove(packet);
        for(System system : systems) {
            if(system.getPackets().contains(packet)) {
                system.removePacket(packet);
            }
        }
    }
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
        //new line
        if(receivedPacket>=(firstCountPacket/2)){
            isLevelPassed = true;
        }
//        for (Line l : allLines) {
//            Packet pkt = l.getMovingPacket();
//            if (pkt != null) {
//                pkt.advance(dt);              // polymorphic – each subclass decides
//            }
//        }
        long now = java.lang.System.nanoTime();
        List<Line> lines = new ArrayList<>(allLines);
        for (Line l : lines) {
            l.cullExpiredChangeCenters(now);
            l.cullExpiredZeroAccelPoints(now);
            Packet pkt = l.getMovingPacket();

            if (pkt != null) {
                Point pos = pkt.getScreenPosition();
                if (pos != null && !l.accelerationZero.isEmpty()) {
                    for (Point z : new ArrayList<>(l.accelerationZero)) {
                        if (pos.distance(z) <= EFFECT_RADIUS_PX) {
                            pkt.suppressAccelerationForNanos(NANO_20S);
                            break;
                        }
                    }
                }

                if (pos != null && !l.getBackToCenter.isEmpty()) {
                    // iterate over a snapshot of points as well (paranoid-safe)
                    for (Point trigger : new ArrayList<>(l.getBackToCenter)) {
                        if (pos.distance(trigger) <= EFFECT_RADIUS_PX) {
                            pkt.resetCenterDrift();
                            break;
                        }
                    }
                }
                pkt.advance(dt);
            }
        }
        for (Packet p : new ArrayList<>(allPackets)) {
            p.updateTimedEffects(now);
        }

        /* -------- 2: try to send from every fully-wired system -------- */
        boolean everyPortWired =
                systems.stream().allMatch(this::allOutputsConnected)
                        && systems.stream().allMatch(this::allInputsConnected);

        boolean noWireCutsThroughSystem = wiringClearsSystemCentres();
        isReady=noWireCutsThroughSystem && everyPortWired;

        if (launched && isReady) {
            for (System sys : systems) {
                if (!sys.getPackets().isEmpty()) {
                    sys.sendPacket();
                }
                //remove this if problem
                if(sys instanceof AntiTrojanSystem) {
                    ((AntiTrojanSystem) sys).cleanTrojan();
                }
            }
        }
        //new lines
        if(isLevelPassed && allPackets.isEmpty()){
            java.lang.System.out.println("you win");
            winCommitted=true;
            gameStatus.commitWin(levelName, coinCount);
        }
        else if(!isLevelPassed && allPackets.isEmpty()){
            java.lang.System.out.println("you lose");
        }
        //
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
    private boolean wiringClearsSystemCentres() {

        for (System sys : systems) {

            /* centre of the rounded rectangle */
            Point c = new Point(
                    sys.getLocation().x + 90/2,   // SYS_W from GamePanel
                    sys.getLocation().y + 70/2);  // SYS_H from GamePanel

            for (Line l : allLines) {
                List<Point> pts = l.getPath(6);           // smoothed poly-line
                for (int i = 0; i < pts.size()-1; i++) {
                    if (segmentDistance(c, pts.get(i), pts.get(i+1)) < SAFE_RADIUS)
                        return false;                     // wire too close → not ready
                }
            }
        }
        return true;                                      // every centre is clear
    }

    /* minimal distance from point p to segment ab (helper) */
    private static double segmentDistance(Point p, Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (dx == 0 && dy == 0) return p.distance(a);     // a==b
        double t = ((p.x - a.x)*dx + (p.y - a.y)*dy) / (dx*dx + dy*dy);
        t = Math.max(0, Math.min(1, t));                  // clamp to [0,1]
        double projX = a.x + t*dx, projY = a.y + t*dy;
        return p.distance(projX, projY);
    }
    public void addToFirstCountPacket(){firstCountPacket++;}
    public void addToReceivedPacket(){receivedPacket++;}
    public void setLevelName(String levelName) {   // NEW
        this.levelName = levelName;
    }
    public int getTotalCoins() {
        return (gameStatus != null) ? gameStatus.getTotalCoin() : 0; // adjust getter name if different
    }
    public boolean spendTotalCoins(int amount) {
        if (gameStatus == null) return false;
        int cur = gameStatus.getTotalCoin();                // <-- use your real API
        if (cur < amount) return false;
        gameStatus.setTotalCoin(cur - amount);              // <-- use your real API
        return true;
    }
}