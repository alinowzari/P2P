package model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
public abstract class Packet {
    public static float SPEED_SCALE = 12f;   ;
    public static float dt=1f / 60f;
    private static int NEXT_ID = 0;
    private int id;
    private System system;
    protected  Line line;
    protected Type type;
    protected int size;
    //impact related
    protected int framesOnWire = 0;
    protected int noise;// screen-space approx (match drawing)
    protected float impactVX = 0f, impactVY = 0f;   // px/s lateral kick
    protected float impactDX = 0f, impactDY = 0f;
    protected static final float IMPACT_DRAG = 4.0f;// accumulated lateral offset;
    //
    protected float progress=0.1f;
    protected float speed;
    protected float acceleration;
    private float accelResume;              // where to restore to
    private long  accelSuppressedUntil = 0;
    protected Point point;
    protected boolean isMoving;
    protected boolean trojan;
    private boolean doneMovement;
    public Packet() {
        system=null;
        line=null;
        id=NEXT_ID++;
    }

    public void setSystem(System system) {
        this.system = system;
    }

    public void setLine(Line line) {
        this.line = line;
    }
    public Line getLine() {return line;}
    public void isTrojan(){trojan=true;}
    public void isNotTrojan(){trojan=false;}
    public boolean hasTrojan() {return trojan;}
    public abstract void wrongPort(Port port);

//    public void advance(float dt) {
//        speed += acceleration * dt;
//    }

    public void setPoint(Point point) {this.point=point;}
    public Point getPoint() {return point;}


    public int getId(){return id;}

    public int getSize(){return size;}
    public Type getType(){return type;}
    protected static Point lerp(Point a, Point b, float t) {
        return new Point(
                Math.round(a.x + (b.x - a.x) * t),
                Math.round(a.y + (b.y - a.y) * t)
        );
    }
    public float getProgress()            { return progress; }
    public void  setProgress(float value) { progress = value; }

    public float getSpeed()               { return speed; }
    public void  setSpeed(float s)        { speed = s; }

    public float getAcceleration()        { return acceleration; }
//    public void  setAcceleration(float a) { acceleration = a; }


    public void advance(float dt) {
        if (line == null) return;                 // not travelling
        float v = speed + acceleration * dt;
        float t = progress + v * dt;

        List<Point> path = line.getPath(0);
        if (t >= 1f) {
            t = 1f;
            line.removeMovingPacket();
            line.getEnd().getParentSystem().receivePacket(this);
            setSystem(line.getEnd().getParentSystem());
        }
        setSpeed(v);
        setProgress(t);
        this.setPoint(along(path, t));
    }
    // in Packet.java
    protected Point along(List<Point> pts, float t) {
        if (pts.size() < 2) return pts.get(0);

        double total = 0;
        double[] segLen = new double[pts.size() - 1];
        for (int i = 0; i < segLen.length; i++) {
            total += segLen[i] = pts.get(i).distance(pts.get(i + 1));
        }
        double goal = t * total, run = 0;
        for (int i = 0; i < segLen.length; i++) {
            if (run + segLen[i] >= goal) {
                double localT = (goal - run) / segLen[i];
                Point a = pts.get(i), b = pts.get(i + 1);
                int x = (int) Math.round(a.x + localT * (b.x - a.x));
                int y = (int) Math.round(a.y + localT * (b.y - a.y));
                return new Point(x, y);
            }
            run += segLen[i];
        }
        return pts.get(pts.size() - 1);
    }



    public void beginTraversal(Line l, Point startPos) {
        line = l;
        isMoving = true;
        system   = null;
        progress = 0f;
        // subclasses will override and clear their own cached paths
        resetPath();

        point = startPos;          // exact port centre
    }
    protected void resetPath() {}

    public void doneMovement() {doneMovement = true;}
    public boolean getDoneMovement(){return doneMovement;}
    public void isNotMoving(){isMoving = false;}

    public Point getScreenPosition() { return point; }

    /** Called when a Reset-Center effect is hit. Default: no-op. */
    public void resetCenterDrift() { /* default no drift */ }


    /** If currently suppressed, we update the resume target instead. */
    public void setAcceleration(float a) {
        if (accelSuppressedUntil != 0) {
            accelResume = a;                // keep intent for when suppression ends
        } else {
            this.acceleration = a;
        }
    }

    /** Apply "acceleration = 0" for `durationNanos`. Extends if already active. */
    public void suppressAccelerationForNanos(long durationNanos) {
        long now = java.lang.System.nanoTime();
        if (accelSuppressedUntil == 0) {
            accelResume = acceleration;     // first time: remember current accel
        }
        long until = now + durationNanos;
        if (until > accelSuppressedUntil) { // refresh/extend window
            accelSuppressedUntil = until;
        }
        this.acceleration = 0f;
    }

    /** Call each frame to auto-restore when time is up. */
    public void updateTimedEffects(long now) {
        if (accelSuppressedUntil != 0 && now >= accelSuppressedUntil) {
            this.acceleration = accelResume;
            accelSuppressedUntil = 0;
        }
    }

    public boolean isAccelerationSuppressed() { return accelSuppressedUntil != 0; }
    public int     getNoise() { return noise; }
    public void incNoise() {
        if (noise < size) {
            noise++;
            return;
        }
        // noise reached size → destroy this packet
        SystemManager mgr = resolveManager();
        if (mgr != null) {
            mgr.packetDestroyed(this);
        } else {
            // last-resort fallback: detach safely to avoid leaked state
            if (line != null) {
                line.removeMovingPacket();
                setLine(null);
            }
            isMoving = false;
            // (Optionally log) System.err.println("Packet destroyed but manager was null (id=" + id + ")");
        }
    }

    /** Find a SystemManager even when `system == null` while travelling on a line. */
    private SystemManager resolveManager() {
        if (system != null) {
            return system.getSystemManager();  // if your System exposes this
        }
        if (line != null) {
            // prefer start; if missing, try end
            model.System startSys = (line.getStart() != null) ? line.getStart().getParentSystem() : null;
            if (startSys != null && startSys.getSystemManager() != null) {
                return startSys.getSystemManager();
            }
            model.System endSys = (line.getEnd() != null) ? line.getEnd().getParentSystem() : null;
            if (endSys != null) {
                return endSys.getSystemManager();
            }
        }
        return null;
    }
    public List<Point> hitMapLocal() {
        int r = collisionRadius();
        ArrayList<Point> pts = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0; // 0,45,90,...
            pts.add(new Point((int)Math.round(r * Math.cos(a)),
                    (int)Math.round(r * Math.sin(a))));
        }
        return pts;
    }

    /** Translate local hit points by current screen center. */
    public List<Point> hitMapWorld() {
        Point c = getScreenPosition();
        ArrayList<Point> out = new ArrayList<>();
        for (Point p : hitMapLocal()) out.add(new Point(c.x + p.x, c.y + p.y));
        return out;
    }
    public int collisionRadius() {
        // default matches your on-screen packet radius (PACKET_R = 8)
        return 8;
    }
    public void applyImpactImpulse(Point impact, float strength) {
        if (impact == null || this.point == null) return;

        float dx = this.point.x - impact.x;
        float dy = this.point.y - impact.y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) { dx = 1f; dy = 0f; len = 1f; }

        dx /= len; dy /= len;                // unit vector away from impact

        final float KICK = 60f;              // px/s; tune to taste
        impactVX += dx * KICK * strength;
        impactVY += dy * KICK * strength;
    }

    /**
     * Combine the on-wire geometric center (base) with the transient impact
     * offset/velocity, with exponential damping. Returns the visible point.
     */
    protected Point composeImpact(Point base, float dt) {
        if (base == null) return this.point;

        // integrate lateral velocity → offset
        impactDX += impactVX * dt;
        impactDY += impactVY * dt;

        // clamp offset so packets can’t fly off too far
        float maxOffset = Math.max(24f, 2f * collisionRadius());
        float offLen = (float) Math.hypot(impactDX, impactDY);
        if (offLen > maxOffset) {
            float k = maxOffset / offLen;
            impactDX *= k; impactDY *= k;
        }

        // exponential damping of lateral velocity
        float decay = (float) Math.exp(-IMPACT_DRAG * dt);
        impactVX *= decay;
        impactVY *= decay;

        return new Point(
                Math.round(base.x + impactDX),
                Math.round(base.y + impactDY)
        );
    }
    public void immediateImpactStep(float dt) {
        if (point != null) {
            // use current visible center as base for one tiny integration step
            this.point = composeImpact(this.point, dt);
        }
    }

}
