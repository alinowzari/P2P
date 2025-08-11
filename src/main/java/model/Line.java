package model;

import model.ports.InputPort;
import model.ports.OutputPort;

import javax.lang.model.type.ArrayType;
import java.awt.*;
import java.lang.System;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class Line {
    private OutputPort start;
    private InputPort end;
    private boolean isOccupied;
    private Packet movingPacket;
    private final ArrayList<BendPoint> bendPoints;
    public final ArrayList<Point> accelerationZero = new ArrayList<>();
    private final ArrayList<Long> azExpiry = new ArrayList<>();
    public final ArrayList<Point> getBackToCenter = new ArrayList<>();
    private final ArrayList<Long> gbcExpiry = new ArrayList<>();

    private static final long DURATION_NANOS = 20_000_000_000L;
    public Line(OutputPort start, InputPort end) {
        this.start = start;
        this.end = end;
        this.isOccupied = false;
        this.movingPacket = null;
        this.bendPoints = new ArrayList<>(3);
    }
    public OutputPort getStart() {
        return start;
    }
    public InputPort getEnd() {
        return end;
    }
    public boolean isOccupied() {
        return isOccupied;
    }
    public void setMovingPacket(Packet movingPacket) {
        this.movingPacket = movingPacket;
        isOccupied = true;
        movingPacket.isMoving = true;
    }
    public void removeMovingPacket() {
        this.movingPacket = null;
        isOccupied = false;
    }
    public Packet getMovingPacket() {
        return movingPacket;
    }
    public BendPoint addBendPoint(Point footA, Point middle, Point footB) {

        if (bendPoints.size() >= 3)
            throw new IllegalStateException("max 3 bends already present");

        /* ----- keep points ordered along the port-to-port axis ----- */
        if (projectionT(footA) > projectionT(footB)) {
            Point tmp = footA;   footA = footB;   footB = tmp;
        }
        if (projectionT(middle) < projectionT(footA) ||
                projectionT(middle) > projectionT(footB)) {
            throw new IllegalArgumentException(
                    "middle point must lie between footA and footB on the axis");
        }

        /* ----- find allowed insertion spans --------------------------------
         *   span-1 : [ portStart , firstBend.start ]
         *   span-2 : [ lastBend.end , portEnd ]
         *   (If no bends yet, the whole port-to-port axis is legal.)
         * ------------------------------------------------------------------ */
        double span1Lo = projectionT(start.getCenter());
        double span1Hi = bendPoints.isEmpty()
                ? projectionT(end.getCenter())
                : projectionT(bendPoints.get(0).getStart());

        double span2Lo = bendPoints.isEmpty()
                ? span1Lo   /* unused */
                : projectionT(bendPoints.get(bendPoints.size()-1).getEnd());
        double span2Hi = projectionT(end.getCenter());

        double a = projectionT(footA);
        double b = projectionT(footB);

        boolean inSpan1 = (a >= span1Lo) && (b <= span1Hi);
        boolean inSpan2 = (a >= span2Lo) && (b <= span2Hi);

//        if (!inSpan1 && !inSpan2) {
//            throw new IllegalArgumentException(
//                    "New bend must lie entirely before the first bend or after the last bend");
//        }
//        bendPoints.add(new BendPoint(footA, middle, footB));
//        bendPoints.sort(
//                Comparator.comparingDouble(bp -> projectionT(bp.getMiddle())));

        BendPoint bp = new BendPoint(footA, middle, footB);
        bendPoints.add(bp);
        bendPoints.sort(Comparator.comparingDouble(bpp -> projectionT(bpp.getMiddle())));
        return bp;
    }
    public void removeBendPoint(BendPoint bendPoint) {bendPoints.remove(bendPoint);}
    public ArrayList<Point> getPath(int smoothness) {
        ArrayList<Point> path = new ArrayList<>();

        // 0) start at output-port centre
        Point current = start.getCenter();
        path.add(current);

        // 1) PROCESS BENDS IN THE ORDER THEY APPEAR ALONG THE LINE
        ArrayList<BendPoint> ordered = new ArrayList<>(bendPoints);
        ordered.sort((b1, b2) ->
                Double.compare(projectionT(b1.getMiddle()), projectionT(b2.getMiddle())));

        for (BendPoint bp : ordered) {
            // 1a. straight segment up to bend-start foot (if any distance)
            if (!current.equals(bp.getStart())) path.add(bp.getStart());

            // 1b. Bézier samples (smoothness may be 0)
            if (smoothness > 0)
                path.addAll(bp.sampleCurve(smoothness));

            // 1c. bend-end foot
            path.add(bp.getEnd());

            current = bp.getEnd();   // advance
        }

        // 2) final straight to input-port centre
        if (!current.equals(end.getCenter()))
            path.add(end.getCenter());

        return path;
    }
    public BendPoint getLastBend() {
        return bendPoints.isEmpty() ? null
                : bendPoints.get(bendPoints.size() - 1);
    }
    /* --------------------------------------------------------------
     * helper: projection parameter t  (distance from start along OS)
     * -------------------------------------------------------------- */
    private double projectionT(Point p) {
        Point O = start.getCenter();
        Point S = end.getCenter();
        double vx = S.x - O.x, vy = S.y - O.y;
        double wx = p.x - O.x, wy = p.y - O.y;
        double L2 = vx*vx + vy*vy;
        return L2 == 0 ? 0 : (vx*wx + vy*wy) / Math.sqrt(L2); // scalar distance
    }
    public boolean hit(Point p, double tol) {
        List<Point> pts = getPath(0);
        for (int i = 0; i < pts.size()-1; i++) {
            if (ptToSegmentDist(p, pts.get(i), pts.get(i+1)) <= tol)
                return true;
        }
        return false;
    }
    private double ptToSegmentDist(Point p, Point a, Point b) {
        double vx = b.x - a.x, vy = b.y - a.y;
        double wx = p.x - a.x, wy = p.y - a.y;
        double len2 = vx*vx + vy*vy;
        double t = (len2==0) ? 0 : (vx*wx + vy*wy)/len2;
        t = Math.max(0, Math.min(1, t));
        double dx = a.x + t*vx - p.x;
        double dy = a.y + t*vy - p.y;
        return Math.hypot(dx, dy);
    }
    public float distanceAlong(Packet from, Packet to) {
        if (from.getLine() != this || to.getLine() != this) return Float.POSITIVE_INFINITY;

        // fast path – if both packets keep their progress field up-to-date
        float d = (to.getProgress() - from.getProgress()) *
                (float) totalLength();          // may be negative
        return Math.abs(d);
    }

    /** First packet strictly ahead of <code>me</code> (direction→output→input). */
    public Packet closestAhead(Packet me, float sInSeg, int segIdx) {
        return neighbour(me, sInSeg, segIdx, /*forward*/ true);
    }

    /** First packet strictly behind <code>me</code> (direction→input→output). */
    public Packet closestBehind(Packet me, float sInSeg, int segIdx) {
        return neighbour(me, sInSeg, segIdx, /*forward*/ false);
    }

    /* ---------- internal ---------- */
    private Packet neighbour(Packet me, float sInSeg, int segIdx, boolean fwd) {
        Packet best = null;
        float  bestDist = Float.POSITIVE_INFINITY;

        // global list lives on the start-system’s manager
        List<Packet> all = start.getParentSystem()
                .getSystemManager().allPackets;

        for (Packet p : all) {
            if (p == me || p.getLine() != this) continue;

            float dist = fwd
                    ? distanceAlong(me, p)
                    : distanceAlong(p , me);

            if (dist > 0 && dist < bestDist) {
                bestDist = dist;
                best     = p;
            }
        }
        return best;
    }

    /* cache total length once per wire */
    private double totalLenCache = -1;
    private double totalLength() {
        if (totalLenCache < 0) {
            List<Point> pts = getPath(0);
            double sum = 0;
            for (int i = 0; i < pts.size() - 1; i++)
                sum += pts.get(i).distance(pts.get(i + 1));
            totalLenCache = sum;
        }
        return totalLenCache;
    }

    //new code remove if fucked up
    public void addChangeCenter(Point click) {
        Point at = closestPointOnPath(click);              // snap to wire
        getBackToCenter.add(at);
        gbcExpiry.add(System.nanoTime() + DURATION_NANOS);
    }
    /** call once per tick */
    public void cullExpiredChangeCenters(long now) {
        for (int i = getBackToCenter.size() - 1; i >= 0; i--) {
            if (now >= gbcExpiry.get(i)) {
                getBackToCenter.remove(i);
                gbcExpiry.remove(i);
            }
        }
    }
    public void addZeroAccelPoint(Point click) {
        Point at = closestPointOnPath(click); // snap to wire
        accelerationZero.add(at);
        azExpiry.add(System.nanoTime() + DURATION_NANOS);
    }
    public void cullExpiredZeroAccelPoints(long now) {
        for (int i = accelerationZero.size() - 1; i >= 0; i--) {
            if (now >= azExpiry.get(i)) {
                accelerationZero.remove(i);
                azExpiry.remove(i);
            }
        }
    }
    public Point closestPointOnPath(Point click) {
        List<Point> pts = getPath(6);
        double bestD = Double.POSITIVE_INFINITY;
        Point best = pts.get(0);
        for (int i=0; i<pts.size()-1; i++) {
            Point a = pts.get(i), b = pts.get(i+1);
            Point proj = projectPointToSegment(click, a, b);
            double d = click.distance(proj);
            if (d < bestD) { bestD = d; best = proj; }
        }
        return best;
    }
    private static Point projectPointToSegment(Point p, Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (dx == 0 && dy == 0) return new Point(a);
        double t = ((p.x - a.x)*dx + (p.y - a.y)*dy) / (dx*dx + dy*dy);
        t = Math.max(0, Math.min(1, t));
        return new Point((int)Math.round(a.x + t*dx), (int)Math.round(a.y + t*dy));
    }
}
