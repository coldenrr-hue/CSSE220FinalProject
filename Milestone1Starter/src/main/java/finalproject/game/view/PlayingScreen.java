package finalproject.game.view;

import finalproject.game.GamePhase;
import finalproject.game.GameState;
import finalproject.game.Pieces;
import finalproject.game.ResourceType;
import finalproject.game.SetupRules;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * In-game screen, following the team sketch:
 *
 *   PLAYERS (left)   |  phase banner + whose turn  |  Full screen, Leave
 *                    |  setup order tracker         |  Table chat
 *                    |           BOARD              |  ACTIONS
 *                    |      YOUR CARDS (bottom)     |
 *
 * During setup the banner says SETUP PHASE, the tracker shows the snake order
 * (A B C D D C B A), and on your turn the legal corners / edges glow on the board.
 */
public final class PlayingScreen extends BorderPane {

    private static final String[] SEAT_COLORS = HexBoardView.SEAT_COLORS;

    // Hand order and the tile type that produces each card.
    private static final String[] CARD_NAMES = {"lumber", "wool", "grain", "brick", "ore"};
    private static final ResourceType[] CARD_SOURCES = {ResourceType.FOREST, ResourceType.PASTURE,
            ResourceType.FIELDS, ResourceType.HILLS, ResourceType.MOUNTAINS};

    // Lobby palette
    private static final String BG = "#12202c", PANEL = "#1d303e", ROW = "#283e4d",
            HIGHLIGHT = "#35546a", GOLD = "#f3cc80", TEXT = "#d5e6ef", SETUP = "#3d8b6e";
    private static final String PANEL_STYLE =
            "-fx-background-color: " + PANEL + "; -fx-background-radius: 12; -fx-padding: 14;";
    private static final String GOLD_BUTTON =
            "-fx-background-color: " + GOLD + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;"
                    + "-fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;";
    private static final String DARK_BUTTON =
            "-fx-background-color: " + ROW + "; -fx-text-fill: " + TEXT + ";"
                    + "-fx-background-radius: 8; -fx-cursor: hand;";

    private final HexBoardView board = new HexBoardView();

    private final VBox playerRows = new VBox(8);
    private final Label phaseBadge = label("", 13, BG, true);
    private final Label turnBanner = label("Waiting for the host…", 26, GOLD, true);
    private final Label instruction = label("", 15, TEXT, false);
    private final Label statusLabel = label("", 13, TEXT, false);
    private final HBox setupTracker = new HBox(4);
    private final VBox chatHolder = new VBox();
    private final Label[] handCounts = new Label[CARD_NAMES.length];
    private VBox actionsPanel;

    private final Button fullscreenButton = button("Full screen", DARK_BUTTON);
    private final Button leaveButton = button("Leave table", DARK_BUTTON);
    private final Label actionsNote = label("", 12, TEXT, false);
    private final Button rollButton = button("Roll dice", GOLD_BUTTON);
    private final Button buyButton = button("Buy", GOLD_BUTTON);
    private final Button tradeButton = button("Trade", GOLD_BUTTON);
    private final Button devCardButton = button("Development card", GOLD_BUTTON);
    private final Button endTurnButton = button("End turn", GOLD_BUTTON);

    private Runnable onRoll, onBuy, onTrade, onDevelopmentCard, onEndTurn;
    private IntConsumer onPlaceSettlement, onPlaceRoad;
    private boolean myTurn;
    private boolean setup;

    public PlayingScreen() {
        setStyle("-fx-background-color: " + BG + ";");
        setPadding(new Insets(18));

        setLeft(buildPlayers());
        setCenter(buildCenter());
        setRight(buildRight());
        setBottom(buildHand());

        rollButton.setOnAction(e -> run(onRoll));
        buyButton.setOnAction(e -> run(onBuy));
        tradeButton.setOnAction(e -> run(onTrade));
        devCardButton.setOnAction(e -> run(onDevelopmentCard));
        endTurnButton.setOnAction(e -> run(onEndTurn));

        reset();
    }

    // =====================================================================
    // API used by LobbyView
    // =====================================================================

    public HexBoardView board() { return board; }

    public void onFullscreen(Runnable action) { fullscreenButton.setOnAction(e -> action.run()); }
    public void onLeave(Runnable action)      { leaveButton.setOnAction(e -> action.run()); }
    public void onEndTurn(Runnable action)    { onEndTurn = action; updateButtons(); }

    /** Called with the vertex ID when this player clicks a glowing corner during setup. */
    public void onPlaceSettlement(IntConsumer action) { onPlaceSettlement = action; }
    /** Called with the edge ID when this player clicks a glowing edge during setup. */
    public void onPlaceRoad(IntConsumer action)       { onPlaceRoad = action; }

    public void showStatus(String value) { statusLabel.setText(value); }

    public void attachChat(Node chat) {
        chatHolder.getChildren().setAll(chat);
        VBox.setVgrow(chat, Priority.ALWAYS);
    }

    public void detachChat() { chatHolder.getChildren().clear(); }

    public void render(GameState state, int playerId, Map<Integer, String> names) {
        board.render(state);
        myTurn = state.currentPlayerId() == playerId;
        setup = SetupRules.isSetup(state);
        String current = nameOf(state.currentPlayerId(), names);

        if (setup) {
            int round = state.phase() == GamePhase.SETUP_FORWARD ? 1 : 2;
            phaseBadge.setText("SETUP PHASE  ·  ROUND " + round + " OF 2");
            phaseBadge.setStyle(badgeStyle(SETUP, "white"));
            turnBanner.setText(myTurn ? "Your Setup Turn" : current + " is placing");
            instruction.setText(setupInstruction(state, current, round));
            renderTracker(state, names);
            showPlacementSpots(state, playerId);
        } else {
            phaseBadge.setText("TURN " + state.turnNumber());
            phaseBadge.setStyle(badgeStyle(GOLD, BG));
            turnBanner.setText(myTurn ? "Your Turn" : current + "'s Turn");
            instruction.setText("");
            setupTracker.getChildren().clear();
            board.clearSpots();
            actionsNote.setText("");
        }
        // The Actions panel only exists during normal play; during setup the chat gets the space.
        actionsPanel.setVisible(!setup);
        actionsPanel.setManaged(!setup);


        renderPlayers(state, playerId, names);
        setHand(state.pieces(), playerId);
        updateButtons();
    }

    public void reset() {
        board.clear();
        myTurn = false;
        setup = false;
        playerRows.getChildren().clear();
        setupTracker.getChildren().clear();
        phaseBadge.setText("WAITING");
        phaseBadge.setStyle(badgeStyle(ROW, TEXT));
        turnBanner.setText("Waiting for the host…");
        instruction.setText("");
        statusLabel.setText("");
        actionsNote.setText("");
        actionsPanel.setVisible(false);
        actionsPanel.setManaged(false);
        setHand(Pieces.EMPTY, -1);
        updateButtons();
    }

    // Hooks for later features
    public void onRollDice(Runnable action)   { onRoll = action; updateButtons(); }
    public void onBuy(Runnable action)        { onBuy = action; updateButtons(); }
    public void onTrade(Runnable action)      { onTrade = action; updateButtons(); }
    public void onDevelopmentCard(Runnable action) { onDevelopmentCard = action; updateButtons(); }

    // =====================================================================
    // Setup phase
    // =====================================================================

    private String setupInstruction(GameState state, String current, int round) {
        boolean road = SetupRules.needsRoad(state);
        if (!myTurn) {
            return "Waiting for " + current + " to place a " + (road ? "road." : "settlement.");
        }
        if (road) return "Now click a glowing edge next to your new settlement to place a road. That ends your setup turn.";
        return "Click a glowing corner to place your settlement."
                + (round == 2 ? "  You'll collect one card from each tile around it." : "");
    }

    /** On your turn, make the legal corners or edges clickable. */
    private void showPlacementSpots(GameState state, int playerId) {
        if (!myTurn) {
            board.clearSpots();
        } else if (SetupRules.needsSettlement(state)) {
            // The host rejects repeated clicks because they carry an old revision number.
            board.showSettlementSpots(SetupRules.legalSettlements(state, playerId), vertexId -> {
                if (onPlaceSettlement != null) onPlaceSettlement.accept(vertexId);
            });
        } else {
            board.showRoadSpots(SetupRules.legalRoads(state, playerId), edgeId -> {
                if (onPlaceRoad != null) onPlaceRoad.accept(edgeId);
            });
        }
    }

    /** The 8 setup turns in snake order; finished ones are dimmed, the current one is outlined. */
    private void renderTracker(GameState state, Map<Integer, String> names) {
        setupTracker.getChildren().clear();
        List<Integer> order = state.turnOrder();
        int position = order.indexOf(state.currentPlayerId());
        int currentStep = state.phase() == GamePhase.SETUP_FORWARD ? position : 4 + (3 - position);

        for (int step = 0; step < 8; step++) {
            int id = step < 4 ? order.get(step) : order.get(7 - step);
            Label chip = label(shortName(nameOf(id, names)), 12, BG, true);
            chip.setMinWidth(Region.USE_PREF_SIZE);
            String border = step == currentStep ? "-fx-border-color: white; -fx-border-width: 2; -fx-border-radius: 6;" : "";
            chip.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + BG + ";"
                    + "-fx-background-color: " + SEAT_COLORS[id] + "; -fx-background-radius: 6;"
                    + "-fx-padding: 3 8 3 8;" + border
                    + "-fx-opacity: " + (step < currentStep ? "0.35;" : "1;"));
            setupTracker.getChildren().add(chip);
            if (step < 7) {
                Label arrow = label(step == 3 ? "↩" : "→", 13, TEXT, true);
                setupTracker.getChildren().add(arrow);
            }
        }
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private VBox buildPlayers() {
        VBox panel = new VBox(12, label("PLAYERS", 13, TEXT, true), playerRows);
        panel.setStyle(PANEL_STYLE);
        panel.setPrefWidth(230);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        BorderPane.setMargin(panel, new Insets(0, 18, 0, 0));
        return panel;
    }

    private VBox buildCenter() {
        setupTracker.setAlignment(Pos.CENTER);
        instruction.setAlignment(Pos.CENTER);
        VBox.setVgrow(board, Priority.ALWAYS);
        VBox center = new VBox(6, phaseBadge, turnBanner, instruction, setupTracker, statusLabel, board);
        center.setAlignment(Pos.TOP_CENTER);
        return center;
    }

    private VBox buildRight() {
        HBox top = new HBox(8, fullscreenButton, leaveButton);
        top.setAlignment(Pos.TOP_RIGHT);

        chatHolder.setStyle(PANEL_STYLE);
        VBox.setVgrow(chatHolder, Priority.ALWAYS);

        VBox actions = new VBox(8, label("ACTIONS", 13, TEXT, true), actionsNote,
                rollButton, buyButton, tradeButton, devCardButton, endTurnButton);
        actions.setStyle(PANEL_STYLE);
        actionsPanel = actions;
        for (Button b : List.of(rollButton, buyButton, tradeButton, devCardButton, endTurnButton)) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefHeight(36);
        }

        VBox column = new VBox(12, top, chatHolder, actions);
        column.setPrefWidth(250);
        // Pushed down so the menu starts lower on the screen.
        BorderPane.setMargin(column, new Insets(60, 0, 0, 18));
        return column;
    }

    private VBox buildHand() {
        HBox cards = new HBox(10);
        cards.setAlignment(Pos.CENTER);
        for (int i = 0; i < CARD_NAMES.length; i++) {
            handCounts[i] = label("0", 14, BG, true);
            handCounts[i].setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + BG + ";"
                    + "-fx-background-color: " + GOLD + "; -fx-background-radius: 12; -fx-padding: 1 8 1 8;"
                    + "-fx-border-color: " + BG + "; -fx-border-radius: 12; -fx-border-width: 2;");

            Node face;
            Image art = loadOptional("/cards/" + CARD_NAMES[i] + ".png");
            if (art != null) {
                ImageView view = new ImageView(art);
                view.setFitWidth(64);
                view.setFitHeight(90);
                face = view;
            } else {                              // plain card if the image is missing
                Label name = label(capitalize(CARD_NAMES[i]), 12, "#3b2a1a", true);
                StackPane plain = new StackPane(name);
                plain.setPrefSize(64, 90);
                plain.setStyle("-fx-background-color: #fbf6e9; -fx-background-radius: 8;"
                        + "-fx-border-color: " + GOLD + "; -fx-border-radius: 8; -fx-border-width: 2;");
                face = plain;
            }
            StackPane card = new StackPane(face, handCounts[i]);
            StackPane.setAlignment(handCounts[i], Pos.TOP_RIGHT);   // top corner so the card name stays readable
            cards.getChildren().add(card);
        }
        VBox panel = new VBox(8, label("YOUR CARDS", 13, TEXT, true), cards);
        panel.setAlignment(Pos.CENTER);
        panel.setStyle(PANEL_STYLE);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        BorderPane.setAlignment(panel, Pos.CENTER);
        BorderPane.setMargin(panel, new Insets(14, 0, 0, 0));
        return panel;
    }

    // =====================================================================
    // Rendering helpers
    // =====================================================================

    private void renderPlayers(GameState state, int playerId, Map<Integer, String> names) {
        playerRows.getChildren().clear();
        List<Integer> order = state.turnOrder();
        for (int i = 0; i < order.size(); i++) {
            int id = order.get(i);
            boolean current = id == state.currentPlayerId();

            Rectangle chip = new Rectangle(14, 14, Color.web(SEAT_COLORS[id]));
            chip.setArcWidth(4);
            chip.setArcHeight(4);
            Label name = label(nameOf(id, names) + (id == playerId ? " · You" : ""), 15,
                    current ? GOLD : TEXT, true);
            HBox top = new HBox(8, chip, name);
            top.setAlignment(Pos.CENTER_LEFT);

            Pieces pieces = state.pieces();
            int vp = pieces.victoryPoints(id);
            Label detail = label(vp + " VP   ·   " + pieces.settlementCount(id) + " settlements   ·   "
                    + pieces.roadCount(id) + " roads", 12, TEXT, false);

            VBox row = new VBox(3, top, detail);
            row.setStyle("-fx-padding: 9; -fx-background-radius: 8; -fx-background-color: "
                    + (current ? HIGHLIGHT : ROW) + "; -fx-border-color: " + SEAT_COLORS[id]
                    + "; -fx-border-width: 0 0 0 4;");
            playerRows.getChildren().add(row);
        }
    }

    private void setHand(Pieces pieces, int playerId) {
        List<ResourceType> hand = pieces.hand(playerId);
        for (int i = 0; i < CARD_NAMES.length; i++) {
            ResourceType source = CARD_SOURCES[i];
            long count = hand.stream().filter(r -> r == source).count();
            handCounts[i].setText(Long.toString(count));
        }
    }

    private void updateButtons() {
        boolean playing = myTurn && !setup;
        rollButton.setDisable(!playing || onRoll == null);
        buyButton.setDisable(!playing || onBuy == null);
        tradeButton.setDisable(!playing || onTrade == null);
        devCardButton.setDisable(!playing || onDevelopmentCard == null);
        endTurnButton.setDisable(!playing || onEndTurn == null);
        for (Button b : List.of(rollButton, buyButton, tradeButton, devCardButton)) {
            b.setTooltip(b.isDisabled() && playing ? new Tooltip("Coming soon") : null);
        }
    }

    private static String badgeStyle(String background, String text) {
        return "-fx-background-color: " + background + "; -fx-text-fill: " + text + ";"
                + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 20;";
    }

    private static String nameOf(int id, Map<Integer, String> names) {
        return names.getOrDefault(id, "Player " + (id + 1));
    }

    private static String shortName(String name) {
        return name.length() <= 10 ? name : name.substring(0, 9) + "…";
    }

    private static void run(Runnable action) { if (action != null) action.run(); }

    private static Label label(String text, int size, String color, boolean bold) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: " + size + "px; -fx-text-fill: " + color + ";"
                + (bold ? " -fx-font-weight: bold;" : ""));
        return l;
    }

    private static Button button(String text, String style) {
        Button b = new Button(text);
        b.setStyle(style);
        return b;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Image loadOptional(String path) {
        URL url = getClass().getResource(path);
        return url == null ? null : new Image(url.toExternalForm());
    }
}