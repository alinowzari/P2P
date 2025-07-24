package controller;

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

    private final SystemManager model;
    private final GamePanel     panel;
    private final Timer         timer;

    public GameController(SystemManager model, JPanel cards) {
        this.model = model;

        /* 1 ▸ view */
        panel = new GamePanel(model);
        cards.add(panel, "GAME");
        ((CardLayout) cards.getLayout()).show(cards, "GAME");

        /* 2 ▸ input */
        new ConnectionController(model, panel);

        /* 3 ▸ loop: update model, then repaint canvas */
        timer = new Timer(16, e -> {
//            model.update();   // physics, packet motion, etc.
            panel.repaint();  // paint new model state
        });
        timer.start();
    }

    /** call when leaving the level */
    public void stop() { timer.stop(); }
}
