// model/physics/ImpactEvent.java
package model.physics;

import java.awt.*;

public class ImpactEvent {
    public final int id1;
    public final int id2;
    public final Point impact;
    public ImpactEvent(int id1, int id2, Point impact) {
        this.id1 = id1; this.id2 = id2; this.impact = impact;
    }
}
