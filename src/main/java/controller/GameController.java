package controller;

import model.Packet;
import model.SystemManager;
import view.GamePanel;

import javax.swing.*;
import java.awt.CardLayout;

/**
 * Sets up the in-level screen:
 *   • builds a GamePanel from the SystemManager
 *   • installs ConnectionController for wiring
 *   • runs a 60 FPS timer that updates the model AND repaints the panel
 */
public class GameController {

    private final GamePanel     panel;
    private final Timer         timer;
    private final JButton launchBtn = new JButton("Launch packets");


    public GameController(SystemManager sm, JPanel cards) {
        /* 1 ▸ view */
        panel = new GamePanel(sm);
        cards.add(panel, "GAME");
        ((CardLayout) cards.getLayout()).show(cards, "GAME");

        /* 2 ▸ input */
        new ConnectionController(sm, panel);


        launchBtn.setBounds(150, 10, 140, 25);
        launchBtn.addActionListener(e -> {
            sm.launchPackets();
            launchBtn.setEnabled(false);        // one-shot
        });
        launchBtn.setEnabled(false);
        panel.add(launchBtn);


        final float FIXED_DT = 1f / 60f;                 // 16.666… ms
        timer = new Timer(16, e -> {                     // 60 FPS
            sm.update(FIXED_DT * Packet.SPEED_SCALE);    // ← SCALE knob
            panel.repaint();

            if (sm.isReady() && !sm.isLaunched())
                launchBtn.setEnabled(true);
        });
        timer.start();
    }

    /** call when leaving the level */
    public void stop() { timer.stop(); }
}
