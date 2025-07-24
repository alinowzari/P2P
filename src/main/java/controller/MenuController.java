package controller;

import view.MenuView;

import javax.swing.*;
import java.awt.*;

/**
 * Controller for the main menu: switches views on button clicks.
 */
public class MenuController {
    private final MenuView view;
    private final CardLayout cardLayout;
    private final JPanel cards;

    public MenuController(MenuView view, JPanel cards) {
        this.view = view;
        this.cards = cards;
        LayoutManager layout = cards.getLayout();
        if (!(layout instanceof CardLayout)) {
            throw new IllegalArgumentException("cards must use CardLayout");
        }
        this.cardLayout = (CardLayout) layout;
        initListeners();
    }

    private void initListeners() {
        view.getLevelsButton().addActionListener(e ->
                cardLayout.show(cards, "LEVELS"));
        view.getSettingsButton().addActionListener(e ->
                cardLayout.show(cards, "SETTINGS"));
        view.getShopButton().addActionListener(e ->
                cardLayout.show(cards, "SHOP"));
    }
}