package finalproject.game.view;

import finalproject.game.GamePhase;
import finalproject.game.GameState;
import finalproject.game.PlayerAssets;
import finalproject.game.ResourceType;
import finalproject.game.GameRules;
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
import javafx.scene.layout.FlowPane;
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
public final class GameScreen extends BorderPane {

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
    private final DiceRollAnimation diceAnimation = new DiceRollAnimation();
    private final Label diceSummary = label("Roll to begin your turn.", 14, GOLD, true);
    private boolean rolled;
    private boolean diceRevealed;
    private int animatedTurn = -1;

    private final VBox playerRows = new VBox(8);
    private final Label phaseBadge = label("", 13, BG, true);
    private final Label turnBanner = label("Waiting for the host…", 26, GOLD, true);
    private final Label instruction = label("", 15, TEXT, false);
    private final Label statusLabel = label("", 13, TEXT, false);
    private final FlowPane setupTracker = new FlowPane(4, 4);
    private final VBox chatHolder = new VBox();
    private final Label[] handCounts = new Label[CARD_NAMES.length];
    private VBox actionsPanel;

    private final Button fullscreenButton = button("Full screen", DARK_BUTTON);
    private final Button leaveButton = button("Leave table", DARK_BUTTON);
    private final Label actionsNote = label("", 12, TEXT, false);
    private final Button rollButton = button("Roll dice", GOLD_BUTTON);
    private final Button cancelBuild = button("Cancel placement", DARK_BUTTON);
    private final Button buildButton = button("Build", GOLD_BUTTON);
    private final Button tradeButton = button("Trade", GOLD_BUTTON);
    private final Button devCardButton = button("Development card", GOLD_BUTTON);
    private final Button endTurnButton = button("End turn", GOLD_BUTTON);

    private Runnable onRoll, onBuild, onTrade, onDevelopmentCard, onEndTurn;
    private IntConsumer onPlaceSettlement, onPlaceRoad;
    private boolean myTurn;
    private boolean setup;
    private GameState latestState;
    private int localPlayer;
    private Map<Integer, String> playerNames = Map.of();
    private java.util.function.Consumer<String[]> command;
    private final VBox tradeOfferPanel = new VBox(6);

    public void onCommand(java.util.function.Consumer<String[]> action) {
        command = action;
        onBuild(this::chooseBuild);
        onTrade(() -> TradeDialog.show(latestState, localPlayer, playerNames, command));
    }

    private void chooseBuild() {
        if (latestState == null) return;
        GameState snapshot = latestState;
        BuildDialog.show(snapshot, localPlayer, kind -> {
            if (latestState == null || latestState.revision() != snapshot.revision()) {
                showStatus("The board changed. Open Build again to choose a piece.");
                return;
            }
            var spots = GameRules.legalBuilds(snapshot, localPlayer, kind);
            boolean city = kind == GameRules.Build.CITY;
            instruction.setText(city ? "Click one of your ringed settlements to upgrade it for 3 ore + 2 grain."
                    : "Click a glowing location to build your " + kind.name().toLowerCase(java.util.Locale.ROOT) + ".");
            cancelBuild.setVisible(true); cancelBuild.setManaged(true);
            java.util.function.IntConsumer place = spot -> {
                board.clearSpots();
                cancelBuild.setVisible(false); cancelBuild.setManaged(false);
                command.accept(new String[]{"BUILD", kind.name(), "" + spot, "" + snapshot.revision()});
            };
            if (kind == GameRules.Build.ROAD) board.showRoadSpots(spots, place);
            else if (city) board.showCitySpots(spots, place);
            else board.showSettlementSpots(spots, place);
        });
    }

    private void renderOffer(GameState state) {
        tradeOfferPanel.getChildren().clear();
        var offer = state.offer();
        if (offer == null) return;
        Label text = label(nameOf(offer.from(), playerNames) + " offers " + ResourceType.describeCards(offer.give())
                + " for " + ResourceType.describeCards(offer.receive()) + " from " + nameOf(offer.to(), playerNames), 13, TEXT, false);
        tradeOfferPanel.getChildren().add(text);
        if (command == null) return;
        if (localPlayer == offer.to()) {
            Button accept = button("Accept", GOLD_BUTTON), decline = button("Decline", DARK_BUTTON);
            accept.setDisable(!GameRules.hasResources(state.assets(), localPlayer, offer.receive()));
            if (accept.isDisabled()) tradeOfferPanel.getChildren().add(label("You do not have the requested cards.", 12, GOLD, false));
            accept.setOnAction(e -> command.accept(new String[]{"TRADE_ACCEPT", "" + state.revision()}));
            decline.setOnAction(e -> command.accept(new String[]{"TRADE_DECLINE", "" + state.revision()}));
            tradeOfferPanel.getChildren().add(new HBox(6, accept, decline));
        } else if (localPlayer == offer.from()) {
            Button cancel = button("Cancel offer", DARK_BUTTON);
            cancel.setOnAction(e -> command.accept(new String[]{"TRADE_CANCEL", "" + state.revision()}));
            tradeOfferPanel.getChildren().add(cancel);
        }
    }

    public GameScreen() {
        diceSummary.setId("dice-summary");
        setStyle("-fx-background-color: " + BG + ";");
        setPadding(new Insets(18));

        setLeft(buildPlayers());
        setCenter(new StackPane(buildCenter(), diceAnimation));
        setRight(buildRight());
        setBottom(buildHand());

        cancelBuild.setVisible(false); cancelBuild.setManaged(false);
        cancelBuild.setOnAction(e -> {
            board.clearSpots(); instruction.setText("");
            cancelBuild.setVisible(false); cancelBuild.setManaged(false);
        });
        rollButton.setOnAction(e -> run(onRoll));
        buildButton.setOnAction(e -> run(onBuild));
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
        cancelBuild.setVisible(false); cancelBuild.setManaged(false);
        latestState = state; localPlayer = playerId; playerNames = Map.copyOf(names);
        renderOffer(state);
        board.render(state);
        myTurn = state.currentPlayerId() == playerId;
        setup = GameRules.isSetup(state);
        rolled = state.dice().rolled();
        if (rolled && animatedTurn != state.turnNumber()) {
            animatedTurn = state.turnNumber();
            diceRevealed = false;
            diceSummary.setText("Rolling…");
            int revealingTurn = state.turnNumber();
            diceAnimation.play(state.dice(), () -> {
                if (rolled && animatedTurn == revealingTurn) {
                    diceRevealed = true;
                    diceSummary.setText("Dice: " + state.dice().first() + " + " + state.dice().second()
                            + " = " + state.dice().total());
                    updateButtons();
                }
            });
        } else if (!rolled) {
            diceRevealed = false;
            diceSummary.setText("Roll to begin your turn.");
            diceAnimation.clear();
        }
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
        setHand(state.assets(), playerId);
        updateButtons();
    }

    public void reset() {
        cancelBuild.setVisible(false); cancelBuild.setManaged(false);
        latestState = null;
        tradeOfferPanel.getChildren().clear();
        board.clear();
        myTurn = false;
        setup = false;
        rolled = false;
        diceRevealed = false;
        animatedTurn = -1;
        diceAnimation.clear();
        diceSummary.setText("Roll to begin your turn.");
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
        setHand(PlayerAssets.EMPTY, -1);
        updateButtons();
    }

    // Hooks for later features
    public void onRollDice(Runnable action)   { onRoll = action; updateButtons(); }
    public void onBuild(Runnable action)        { onBuild = action; updateButtons(); }
    public void onTrade(Runnable action)      { onTrade = action; updateButtons(); }
    public void onDevelopmentCard(Runnable action) { onDevelopmentCard = action; updateButtons(); }

    // =====================================================================
    // Setup phase
    // =====================================================================

    private String setupInstruction(GameState state, String current, int round) {
        boolean road = GameRules.needsRoad(state);
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
        } else if (GameRules.needsSettlement(state)) {
            // The host rejects repeated clicks because they carry an old revision number.
            board.showSettlementSpots(GameRules.legalSetupSettlements(state, playerId), vertexId -> {
                if (onPlaceSettlement != null) onPlaceSettlement.accept(vertexId);
            });
        } else {
            board.showRoadSpots(GameRules.legalSetupRoads(state, playerId), edgeId -> {
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
        VBox center = new VBox(6, phaseBadge, turnBanner, instruction, cancelBuild, setupTracker, statusLabel, board);
        center.setMinWidth(0);
        setupTracker.setMinWidth(0);
        center.setAlignment(Pos.TOP_CENTER);
        return center;
    }

    private VBox buildRight() {
        HBox top = new HBox(8, fullscreenButton, leaveButton);
        top.setAlignment(Pos.TOP_RIGHT);

        chatHolder.setStyle(PANEL_STYLE);
        VBox.setVgrow(chatHolder, Priority.ALWAYS);

        VBox actions = new VBox(8, label("ACTIONS", 13, TEXT, true), actionsNote, diceSummary,
                rollButton, buildButton, tradeButton, tradeOfferPanel, devCardButton, endTurnButton);
        actions.setStyle(PANEL_STYLE);
        actionsPanel = actions;
        for (Button b : List.of(rollButton, buildButton, tradeButton, devCardButton, endTurnButton)) {
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
            name.setMinWidth(0);
            HBox.setHgrow(name, Priority.ALWAYS);
            HBox top = new HBox(8, chip, name);
            top.setAlignment(Pos.CENTER_LEFT);

            PlayerAssets pieces = state.assets();
            int vp = pieces.victoryPoints(id);
            Label detail = label(vp + " VP   ·   " + pieces.settlementCount(id) + " settlements   ·   "
                    + pieces.roadCount(id) + " roads   /   " + pieces.cityCount(id) + " cities", 12, TEXT, false);

            VBox row = new VBox(3, top, detail);
            row.setStyle("-fx-padding: 9; -fx-background-radius: 8; -fx-background-color: "
                    + (current ? HIGHLIGHT : ROW) + "; -fx-border-color: " + SEAT_COLORS[id]
                    + "; -fx-border-width: 0 0 0 4;");
            playerRows.getChildren().add(row);
        }
    }

    private void setHand(PlayerAssets pieces, int playerId) {
        List<ResourceType> hand = pieces.hand(playerId);
        for (int i = 0; i < CARD_NAMES.length; i++) {
            ResourceType source = CARD_SOURCES[i];
            long count = hand.stream().filter(r -> r == source).count();
            handCounts[i].setText(Long.toString(count));
        }
    }

    private void updateButtons() {
        boolean playing = myTurn && !setup;
        rollButton.setDisable(!playing || rolled || onRoll == null);
        buildButton.setDisable(!playing || !rolled || !diceRevealed || onBuild == null);
        tradeButton.setDisable(!playing || !rolled || !diceRevealed || onTrade == null);
        devCardButton.setDisable(!playing || onDevelopmentCard == null);
        endTurnButton.setDisable(!playing || !rolled || !diceRevealed || onEndTurn == null);
        for (Button b : List.of(rollButton, buildButton, tradeButton, devCardButton)) {
            b.setTooltip(b.isDisabled() && playing ? new Tooltip(b == devCardButton ? "Coming soon" : "Roll the dice first") : null);
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
