// app/Main.java
package app;

import javax.swing.*;
import java.awt.*;

import controller.LevelsController;
import controller.MenuController;
//import controller.LevelsController;
import model.LevelsManager;
import view.LevelsView;
import view.MenuView;
//import view.LevelsView;
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("My Game");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);

            // ── Create models & views ────────────────────────────
            LevelsManager levelsManager = new LevelsManager();
            MenuView       menuView      = new MenuView();
            LevelsView     levelsView    = new LevelsView(levelsManager.getLevelConfigs());
            JPanel         settingsView  = new JPanel();  // stub
            JPanel         shopView      = new JPanel();  // stub

            // ── CardLayout container ────────────────────────────
            JPanel cards = new JPanel(new CardLayout());
            cards.add(menuView,     "MENU");
            cards.add(levelsView,   "LEVELS");
            cards.add(settingsView, "SETTINGS");
            cards.add(shopView,     "SHOP");
            // Note: the "GAME" card will be added at runtime by GameController

            // ── Hook up controllers ─────────────────────────────
            new MenuController(menuView, cards);
            new LevelsController(levelsView, cards, levelsManager);

            frame.getContentPane().add(cards);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}