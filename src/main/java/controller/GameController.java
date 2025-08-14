//package controller;
//
//import model.Packet;
//import model.SystemManager;
//import view.GamePanel;
//
//import javax.swing.*;
//import java.awt.CardLayout;
//
///**
// * Sets up the in-level screen:
// *   • builds a GamePanel from the SystemManager
// *   • installs ConnectionController for wiring
// *   • runs a 60 FPS timer that updates the model AND repaints the panel
// */
//public class GameController {
//
//    private final GamePanel     panel;
//    private final Timer         timer;
//    private final JButton launchBtn = new JButton("Launch packets");
//
//
//    public GameController(SystemManager sm, JPanel cards) {
//        /* 1 ▸ view */
//        panel = new GamePanel(sm);
//        cards.add(panel, "GAME");
//        ((CardLayout) cards.getLayout()).show(cards, "GAME");
//
//        /* 2 ▸ input */
//        new ConnectionController(sm, panel);
//
//
//        launchBtn.setBounds(150, 10, 140, 25);
//        launchBtn.addActionListener(e -> {
//            sm.launchPackets();
//            launchBtn.setEnabled(false);        // one-shot
//        });
//        launchBtn.setEnabled(false);
//        panel.add(launchBtn);
//
//
//        final float FIXED_DT = 1f / 60f;                 // 16.666… ms
//        timer = new Timer(16, e -> {                     // 60 FPS
//            sm.update(FIXED_DT * Packet.SPEED_SCALE);    // ← SCALE knob
//            panel.repaint();
//
//            if (sm.isReady() && !sm.isLaunched())
//                launchBtn.setEnabled(true);
//        });
//        timer.start();
//    }
//
//    /** call when leaving the level */
//    public void stop() { timer.stop(); }
//}
package controller;

import model.Packet;
import model.SystemManager;
import view.GamePanel;

import javax.swing.*;
import java.awt.CardLayout;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sets up the in-level screen:
 *   • builds a GamePanel from the SystemManager
 *   • installs ConnectionController for wiring
 *   • runs a 60 Hz simulation on a background thread
 *   • repaints on the EDT
 */
public class GameController {

    private static final float FIXED_DT = 1f / 60f;   // 60 Hz sim tick

    private final GamePanel panel;
    private final JButton   launchBtn = new JButton("Launch packets");
    private final SystemManager sm;

    private ScheduledExecutorService simExec;

    public GameController(SystemManager sm, JPanel cards) {
        this.sm = sm;

        /* 1 ▸ view */
        panel = new GamePanel(sm);
        cards.add(panel, "GAME");
        ((CardLayout) cards.getLayout()).show(cards, "GAME");

        /* 2 ▸ input */
        new ConnectionController(sm, panel);

        /* 3 ▸ launch button */
        launchBtn.setBounds(150, 10, 140, 25);
        launchBtn.addActionListener(e -> {
            sm.launchPackets();
            launchBtn.setEnabled(false); // one-shot
        });
        launchBtn.setEnabled(false);
        panel.add(launchBtn);

        /* 4 ▸ start simulation (off the EDT) */
        startSimulation();
    }

    private void startSimulation() {
        // single worker thread for deterministic updates
        simExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SimThread");
            t.setDaemon(true);
            return t;
        });

        simExec.scheduleAtFixedRate(() -> {
            try {
                // physics step (off the EDT)
                sm.update(FIXED_DT * Packet.SPEED_SCALE);

                // UI work must happen on the EDT
                SwingUtilities.invokeLater(() -> {
                    panel.repaint();
                    launchBtn.setEnabled(sm.isReady() && !sm.isLaunched());
                });
            } catch (Throwable t) {
                t.printStackTrace(); // don't let the scheduler die silently
            }
        }, 0, 16, TimeUnit.MILLISECONDS); // ~60 FPS cadence
    }

    /** Call when leaving the level */
    public void stop() {
        if (simExec != null) {
            simExec.shutdownNow();
            simExec = null;
        }
    }
}
