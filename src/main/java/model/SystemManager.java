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
    //new fields
    private static final int EFFECT_RADIUS_PX = 10;
    private final Map<Integer, Integer> offwireFrames = new HashMap<>();
    private static final int   OFFWIRE_GRACE_FRAMES = 4;   // require N consecutive frames off-wire
    private static final float OFFWIRE_FACTOR       = 1.6f; // soften threshold: > 1.6*radius to count as “off”
    private static final int   PORT_SAFE_PX         = 18;
    private Set<Long> activeContacts = new HashSet<>();
    private static final int CELL = 32;

    // cell hash map: key packs (cx, cy) into a long
    private final Map<Long, ArrayList<Packet>> grid = new HashMap<>();
    //
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
        if(receivedPacket>=(firstCountPacket/2)){
            isLevelPassed = true;
        }
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
        checkCollisions();
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
//    public void checkCollisions() {
//        // 0) snapshot + filter to travelling packets that have a screen position
//        final ArrayList<Packet> list = new ArrayList<>(allPackets);
//        final ArrayList<Packet> moving = new ArrayList<>(list.size());
//        for (Packet p : list) {
//            if (p != null && p.isMoving && p.getLine() != null && p.getScreenPosition() != null) {
//                moving.add(p);
//            }
//        }
//        if (moving.size() < 2) return;
//
//        // 1) pairwise polygon-vs-polygon intersection
//        for (int i = 0; i < moving.size(); i++) {
//            Packet a = moving.get(i);
//            List<Point> polyA = worldHitMap(a);
//            if (polyA.size() < 2) continue;
//
//            for (int j = i + 1; j < moving.size(); j++) {
//                Packet b = moving.get(j);
//                List<Point> polyB = worldHitMap(b);
//                if (polyB.size() < 2) continue;
//
//                // 1a) edge vs edge test
//                boolean hit = polygonsIntersect(polyA, polyB);
//
//                // 1b) (fallback) containment test: a vertex inside the other polygon
//                if (!hit) {
//                    Point a0 = polyA.get(0);
//                    Point b0 = polyB.get(0);
//                    hit = pointInPolygon(a0, polyB) || pointInPolygon(b0, polyA);
//                }
//
//                if (hit) {
//                    // --- pick an impact point (prefer the first real segment intersection) ---
//                    Point impact = firstIntersectionPoint(polyA, polyB);
//                    if (impact == null) {
//                        // fallback = midpoint between centers
//                        Point ca = a.getScreenPosition();
//                        Point cb = b.getScreenPosition();
//                        impact = new Point((ca.x + cb.x) / 2, (ca.y + cb.y) / 2);
//                    }
//
//                    // --- add noise (your Packet.incNoise() already self-destroys if >= size) ---
//                    a.incNoise();
//                    b.incNoise();
//
//                    // --- shove both away from the impact (adds lateral velocity) ---
//                    a.applyImpactImpulse(impact, 1f);
//                    b.applyImpactImpulse(impact, 1f);
//
//                    // --- make the shove visible immediately this frame too ---
//                    a.immediateImpactStep(DT);
//                    b.immediateImpactStep(DT);
//                }
//            }
//        }
//    }
public void checkCollisions() {
    final ArrayList<Packet> list = new ArrayList<>(allPackets);
    final ArrayList<Packet> moving = new ArrayList<>(list.size());
    for (Packet p : list) {
        if (p != null && p.isMoving && p.getLine() != null && p.getScreenPosition() != null) moving.add(p);
    }
    if (moving.size() < 2) return;

    rebuildGrid(moving);

    // neighbor cell offsets
    int[] off = {-1,0,1};

    for (var e : grid.entrySet()) {
        long k = e.getKey();
        int cx = (int)(k >> 32);
        int cy = (int)(k & 0xffffffffL);

        for (int dx : off) for (int dy : off) {
            ArrayList<Packet> bucket = grid.get(key(cx+dx, cy+dy));
            if (bucket == null) continue;

            // cross-check packets in 'e.getValue()' with 'bucket' (avoid dupes via id ordering)
            for (Packet a : e.getValue()) {
                Point ca = a.getScreenPosition(); if (ca == null) continue;
                int ra = a.collisionRadius();

                for (Packet b : bucket) {
                    if (a.getId() >= b.getId()) continue;         // ensure each pair once
                    Point cb = b.getScreenPosition(); if (cb == null) continue;
                    int rb = b.collisionRadius();

                    // circle–circle broad-phase
                    int dxp = ca.x - cb.x, dyp = ca.y - cb.y;
                    int sum = ra + rb;
                    if (dxp*dxp + dyp*dyp > sum*sum) continue;

                    // narrow-phase: polygon vs polygon
                    List<Point> A = worldHitMap(a);
                    List<Point> B = worldHitMap(b);
                    boolean hit = polygonsIntersect(A,B) || pointInPolygon(A.get(0),B) || pointInPolygon(B.get(0),A);
                    if (!hit) continue;

                    // react
                    // 1) noise
                    a.incNoise();
                    b.incNoise();

                    // 2) impulse away from midpoint of closest centers (cheap & stable)
                    Point impact = new Point((ca.x + cb.x)/2, (ca.y + cb.y)/2);
                    a.applyImpactImpulse(impact, 1f);
                    b.applyImpactImpulse(impact, 1f);

                    // 3) tiny immediate step so they visibly separate this frame
                    a.immediateImpactStep(1f/60f);
                    b.immediateImpactStep(1f/60f);
                }
            }
        }
    }
}


    // Convert a packet’s local hit-map points to world space (center + offset).
    private static List<Point> worldHitMap(Packet p) {
        java.util.List<Point> local = p.hitMapLocal();   // you added these per-packet
        Point c = p.getScreenPosition();
        java.util.ArrayList<Point> world = new java.util.ArrayList<>(local.size());
        for (Point q : local) world.add(new Point(c.x + q.x, c.y + q.y));
        return world;
    }

    // Polygon (closed) intersection via edge–edge checks.
    private static boolean polygonsIntersect(java.util.List<Point> A, java.util.List<Point> B) {
        int na = A.size(), nb = B.size();
        for (int ia = 0; ia < na; ia++) {
            Point a0 = A.get(ia);
            Point a1 = A.get((ia + 1) % na);
            for (int ib = 0; ib < nb; ib++) {
                Point b0 = B.get(ib);
                Point b1 = B.get((ib + 1) % nb);
                if (segmentsIntersect(a0, a1, b0, b1)) return true;
            }
        }
        return false;
    }

    // Robust segment intersection (including collinear overlap).
    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        int o1 = orient(a, b, c);
        int o2 = orient(a, b, d);
        int o3 = orient(c, d, a);
        int o4 = orient(c, d, b);

        if (o1 != o2 && o3 != o4) return true; // general case

        // Collinear cases
        if (o1 == 0 && onSegment(a, b, c)) return true;
        if (o2 == 0 && onSegment(a, b, d)) return true;
        if (o3 == 0 && onSegment(c, d, a)) return true;
        if (o4 == 0 && onSegment(c, d, b)) return true;

        return false;
    }

    private static int orient(Point a, Point b, Point c) {
        long v = (long)(b.x - a.x) * (c.y - a.y) - (long)(b.y - a.y) * (c.x - a.x);
        return (v > 0) ? 1 : (v < 0 ? -1 : 0);
    }

    private static boolean onSegment(Point a, Point b, Point p) {
        return Math.min(a.x, b.x) <= p.x && p.x <= Math.max(a.x, b.x) &&
                Math.min(a.y, b.y) <= p.y && p.y <= Math.max(a.y, b.y) &&
                orient(a, b, p) == 0;
    }

    // Standard ray-casting point-in-polygon.
    private static boolean pointInPolygon(Point p, java.util.List<Point> poly) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point pi = poly.get(i), pj = poly.get(j);
            boolean intersect = ((pi.y > p.y) != (pj.y > p.y)) &&
                    (p.x < (long)(pj.x - pi.x) * (p.y - pi.y) / (double)(pj.y - pi.y) + pi.x);
            if (intersect) inside = !inside;
        }
        return inside;
    }
    private static Point firstIntersectionPoint(List<Point> A, List<Point> B) {
        int na = A.size(), nb = B.size();
        for (int ia = 0; ia < na; ia++) {
            Point a0 = A.get(ia);
            Point a1 = A.get((ia + 1) % na);
            for (int ib = 0; ib < nb; ib++) {
                Point b0 = B.get(ib);
                Point b1 = B.get((ib + 1) % nb);

                Point p = segmentIntersectionPoint(a0, a1, b0, b1);
                if (p != null) return p;
            }
        }
        return null;
    }

    // Returns intersection point for proper intersection; for collinear overlap returns midpoint of the overlap; else null.
    private static Point segmentIntersectionPoint(Point a, Point b, Point c, Point d) {
        long x1=a.x, y1=a.y, x2=b.x, y2=b.y, x3=c.x, y3=c.y, x4=d.x, y4=d.y;

        long den = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4);
        if (den != 0) {
            double t = ((x1-x3)*(y3-y4) - (y1-y3)*(x3-x4)) / (double) den;
            double u = ((x1-x3)*(y1-y2) - (y1-y3)*(x1-x2)) / (double) den;
            if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
                int px = (int)Math.round(x1 + t*(x2 - x1));
                int py = (int)Math.round(y1 + t*(y2 - y1));
                return new Point(px, py);
            }
            return null;
        }

        // Collinear? check overlap and return midpoint of the overlapped segment if any
        if (orient(a,b,c) == 0 && orient(a,b,d) == 0) {
            // project onto X or Y (whichever has larger span) and compute overlap
            if (Math.abs(x1 - x2) >= Math.abs(y1 - y2)) {
                long minAB = Math.min(x1, x2), maxAB = Math.max(x1, x2);
                long minCD = Math.min(x3, x4), maxCD = Math.max(x3, x4);
                long lo = Math.max(minAB, minCD);
                long hi = Math.min(maxAB, maxCD);
                if (lo <= hi) {
                    long mid = (lo + hi) / 2;
                    // find corresponding y via linear interpolation on segment AB
                    double t = (x2 == x1) ? 0.0 : (mid - x1) / (double)(x2 - x1);
                    int py = (int)Math.round(y1 + t*(y2 - y1));
                    return new Point((int)mid, py);
                }
            } else {
                long minAB = Math.min(y1, y2), maxAB = Math.max(y1, y2);
                long minCD = Math.min(y3, y4), maxCD = Math.max(y3, y4);
                long lo = Math.max(minAB, minCD);
                long hi = Math.min(maxAB, maxCD);
                if (lo <= hi) {
                    long mid = (lo + hi) / 2;
                    double t = (y2 == y1) ? 0.0 : (mid - y1) / (double)(y2 - y1);
                    int px = (int)Math.round(x1 + t*(x2 - x1));
                    return new Point(px, (int)mid);
                }
            }
        }
        return null;
    }
    private void rebuildGrid(List<Packet> moving) {
        grid.clear();

        for (Packet p : moving) {
            Point c = p.getScreenPosition();
            if (c == null) continue;

            int r = p.collisionRadius();

            // Which cells does this (center±radius) span?
            int minCx = Math.floorDiv(c.x - r, CELL);
            int maxCx = Math.floorDiv(c.x + r, CELL);
            int minCy = Math.floorDiv(c.y - r, CELL);
            int maxCy = Math.floorDiv(c.y + r, CELL);

            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cy = minCy; cy <= maxCy; cy++) {
                    grid.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(p);
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
    // SystemManager.java
    public void packetDestroyed(Packet p) {
        // 1) Detach from its current line, if any
        Line l = p.getLine();
        if (l != null) {
            Packet mp = l.getMovingPacket();
            if (mp == p) {                 // identity compare, null-safe
                l.removeMovingPacket();    // clears the line’s moving packet
            }
            p.setLine(null);               // clear packet's line pointer
        }

        // 2) Remove from any system queues
        for (System sys : systems) {
            sys.removePacket(p);
        }

        // 3) Remove from the global render list
        allPackets.remove(p);
    }// pairs overlapping in the previous frame
    private static long pairKey(int id1, int id2) {
        if (id1 > id2) { int t = id1; id1 = id2; id2 = t; }
        return (((long) id1) << 32) ^ (id2 & 0xffffffffL);
    }
    private static long key(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xffffffffL);
    }
}