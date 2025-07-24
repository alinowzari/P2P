package view;

import config.GameConfig;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Displays a button for each level.
 */
public class LevelsView extends JPanel {
    private final JButton[] levelButtons;

    public LevelsView(List<GameConfig> configs) {
        setLayout(new GridLayout(0, 1, 10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        levelButtons = new JButton[configs.size()];
        for (int i = 0; i < configs.size(); i++) {
            String name = configs.get(i).levelName();
            JButton btn = new JButton(name);
            levelButtons[i] = btn;
            add(btn);
        }
    }

    public JButton[] getLevelButtons() {
        return levelButtons;
    }
}
