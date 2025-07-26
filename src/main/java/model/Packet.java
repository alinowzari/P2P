package model;

import java.awt.*;
import java.util.List;
public abstract class Packet {
    public static float dt=1f / 60f;
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
    public Packet() {
        system=null;
        line=null;
    }

    public void setSystem(System system) {
        this.system = system;
        isMoving=false;
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

    public void advance(float dt) {
        speed += acceleration * dt;
    }

    public void setPoint(Point point) {this.point=point;}
    public Point getPoint() {return point;}


    public int getId(){return id;}
    public void setId(int id){this.id=id;}

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
//    public void isProtected() {
//        size=5;
//        protection=true;
//    }
//    public void isNotProtected() {
//        protection=false;
//    }

//    public void ifMoving() {
//        if(system==null) {
//            isMoving=true;
//        }
//        else {
//            isMoving=false;
//        }
//    }
}
