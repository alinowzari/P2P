package model;

import java.awt.*;
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
    protected float progress=0.1f;
    protected float speed;
    protected float acceleration;
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
//        isMoving=false;
        line=null;
    }

    public void setLine(Line line) {
        this.line = line;
        isMoving=true;
        system=null;
    }

    public void isTrojan(){trojan=true;}
    public void isNotTrojan(){trojan=false;}
    public boolean hasTrojan() {return trojan;}


    public void shrink() {
        if (size > 0) {
            size--;
        }
    }
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
    public void  setAcceleration(float a) { acceleration = a; }


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
}
