//// model/physics/PhysicsEngine.java
//package model.physics;
//
//import model.Packet;
//import java.awt.*;
//import java.util.*;
//import java.util.List;
//import java.util.concurrent.*;
//import java.util.stream.IntStream;
//
//public class PhysicsEngine {
//
//    public static final class Snapshot {
//        public final int id;
//        public final float x, y;
//        public final int r;
//        public Snapshot(int id, float x, float y, int r) {
//            this.id = id; this.x = x; this.y = y; this.r = r;
//        }
//    }
//
//    // Build a uniform grid for broad-phase, then parallel narrow-phase
//    public List<ImpactEvent> detectImpacts(List<Snapshot> snaps) {
//        if (snaps.size() < 2) return Collections.emptyList();
//
//        // grid params
//        int cell = Packet.COLLISION_RADIUS * 2;
//        Map<Long, List<Integer>> buckets = new HashMap<>(snaps.size() * 2);
//
//        // hash function
//        java.util.function.BiFunction<Integer,Integer,Long> key = (ix,iy) ->
//                (((long)ix) << 32) ^ (iy & 0xffffffffL);
//
//        // fill grid
//        for (int i = 0; i < snaps.size(); i++) {
//            Snapshot s = snaps.get(i);
//            int ix = (int)Math.floor(s.x / cell);
//            int iy = (int)Math.floor(s.y / cell);
//            long k = key.apply(ix, iy);
//            buckets.computeIfAbsent(k, _ -> new ArrayList<>()).add(i);
//        }
//
//        // candidate pairs (with de-dupe)
//        ConcurrentHashMap<Long, Boolean> seen = new ConcurrentHashMap<>();
//        List<int[]> pairs = Collections.synchronizedList(new ArrayList<>());
//
//        int[] neigh = {-1,0,1};
//        for (var e : buckets.entrySet()) {
//            long k = e.getKey();
//            int ix = (int)(k >> 32);
//            int iy = (int)k;
//            List<Integer> base = e.getValue();
//
//            for (int dx : neigh) for (int dy : neigh) {
//                long nk = key.apply(ix+dx, iy+dy);
//                List<Integer> other = buckets.get(nk);
//                if (other == null) continue;
//
//                for (int i : base) for (int j : other) {
//                    if (i >= j) continue;
//                    Snapshot a = snaps.get(i), b = snaps.get(j);
//                    float dx12 = a.x - b.x, dy12 = a.y - b.y;
//                    float rr = (a.r + b.r);
//                    if (dx12*dx12 + dy12*dy12 <= rr*rr) {
//                        long pairKey = (((long)Math.min(a.id, b.id)) << 32) | (Math.max(a.id, b.id) & 0xffffffffL);
//                        if (seen.putIfAbsent(pairKey, Boolean.TRUE) == null) {
//                            pairs.add(new int[]{i, j});
//                        }
//                    }
//                }
//            }
//        }
//
//        // parallel narrow-phase & impact point
//        ImpactEvent[] events = new ImpactEvent[pairs.size()];
//        IntStream.range(0, pairs.size()).parallel().forEach(pi -> {
//            int[] ij = pairs.get(pi);
//            Snapshot a = snaps.get(ij[0]);
//            Snapshot b = snaps.get(ij[1]);
//            float dx = b.x - a.x, dy = b.y - a.y;
//            float dist = (float)Math.hypot(dx, dy);
//            if (dist < 1e-3f) return; // degenerate, skip
//            float t = (float)a.r / (a.r + b.r);
//            int ix = Math.round(a.x + t * dx);
//            int iy = Math.round(a.y + t * dy);
//            events[pi] = new ImpactEvent(a.id, b.id, new Point(ix, iy));
//        });
//
//        ArrayList<ImpactEvent> out = new ArrayList<>();
//        for (ImpactEvent ev : events) if (ev != null) out.add(ev);
//        // sort for determinism
//        out.sort(Comparator.comparingInt((ImpactEvent e) -> e.id1).thenComparingInt(e -> e.id2));
//        return out;
//    }
//}
