package finalproject.game.view;

import finalproject.game.DevelopmentCard;
import finalproject.game.DevelopmentHand;
import finalproject.game.DevelopmentRules;
import finalproject.game.DevelopmentState;
import finalproject.game.GamePhase;
import finalproject.game.GameState;
import finalproject.game.Pieces;
import finalproject.game.ResourceType;
import finalproject.game.SetupRules;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
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
    private final Button buyButton = button("Buy", GOLD_BUTTON);
    private final Button tradeButton = button("Trade", GOLD_BUTTON);
    private final Button devCardButton = button("Buy development card", GOLD_BUTTON);
    private final Button endTurnButton = button("End turn", GOLD_BUTTON);

    // Development cards. devHand is only ever THIS player's cards: the host never sends anyone else's.
    private static final Map<DevelopmentCard, String> DEV_COLORS = Map.of(
            DevelopmentCard.KNIGHT, "#6f5498", DevelopmentCard.VICTORY_POINT, "#b8901c",
            DevelopmentCard.ROAD_BUILDING, "#4f7f40", DevelopmentCard.YEAR_OF_PLENTY, "#33709e",
            DevelopmentCard.MONOPOLY, "#a64c34");
    private final HBox devCardRow = new HBox(10);
    private DevelopmentHand devHand = DevelopmentHand.EMPTY;

    private Runnable onRoll, onBuy, onTrade, onBuyDevelopmentCard, onEndTurn;
    private IntConsumer onPlaceSettlement, onPlaceRoad;
    private BiConsumer<DevelopmentCard, List<ResourceType>> onPlayDevelopmentCard;
    private BiConsumer<Integer, Integer> onMoveRobber;
    private boolean myTurn;
    private boolean setup;
    private GameState state;
    private int playerId = -1;
    private Map<Integer, String> names = Map.of();

    public PlayingScreen() {
        diceSummary.setId("dice-summary");
        setStyle("-fx-background-color: " + BG + ";");
        setPadding(new Insets(18));

        setLeft(buildPlayers());
        setCenter(new StackPane(buildCenter(), diceAnimation));
        setRight(buildRight());
        setBottom(buildHand());

        rollButton.setOnAction(e -> run(onRoll));
        buyButton.setOnAction(e -> run(onBuy));
        tradeButton.setOnAction(e -> run(onTrade));
        devCardButton.setOnAction(e -> run(onBuyDevelopmentCard));
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
        this.state = state;
        this.playerId = playerId;
        this.names = Map.copyOf(names);
        board.render(state);
        myTurn = state.currentPlayerId() == playerId;
        setup = SetupRules.isSetup(state);
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
            setupTracker.getChildren().clear();
            actionsNote.setText("");
            showDevelopmentSpots(state, playerId, current);
        }
        // The Actions panel only exists during normal play; during setup the chat gets the space.
        actionsPanel.setVisible(!setup);
        actionsPanel.setManaged(!setup);


        renderPlayers(state, playerId, names);
        setHand(state.pieces(), playerId);
        renderDevCards();
        updateButtons();
    }

    public void reset() {
        state = null;
        playerId = -1;
        names = Map.of();
        devHand = DevelopmentHand.EMPTY;
        renderDevCards();
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
        setHand(Pieces.EMPTY, -1);
        updateButtons();
    }

    // Hooks for later features
    public void onRollDice(Runnable action)   { onRoll = action; updateButtons(); }
    public void onBuy(Runnable action)        { onBuy = action; updateButtons(); }
    public void onTrade(Runnable action)      { onTrade = action; updateButtons(); }

    /** Buy button. The host draws the card and tells only this player what it was. */
    public void onBuyDevelopmentCard(Runnable action) { onBuyDevelopmentCard = action; updateButtons(); }
    /** Play button on a card: the card, plus resource picks for Year of Plenty (2) or Monopoly (1). */
    public void onPlayDevelopmentCard(BiConsumer<DevelopmentCard, List<ResourceType>> action) {
        onPlayDevelopmentCard = action;
    }
    /** After a Knight: (tile ID, player to steal from or -1). */
    public void onMoveRobber(BiConsumer<Integer, Integer> action) { onMoveRobber = action; }

    /** This player's own development cards, sent privately by the host. */
    public void renderDevelopmentHand(DevelopmentHand hand) {
        devHand = hand;
        renderDevCards();
        if (state != null) renderPlayers(state, playerId, names);
        updateButtons();
    }

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
    // Development cards
    // =====================================================================

    /** After a Knight the robber tiles glow; after Road Building the free road spots glow. */
    private void showDevelopmentSpots(GameState state, int playerId, String current) {
        DevelopmentState dev = state.development();
        if (dev.movingRobber()) {
            instruction.setText(myTurn ? "Knight: click a glowing tile to move the robber." : current + " is moving the robber.");
            if (myTurn) board.showTileSpots(DevelopmentRules.robberTiles(state), this::moveRobberTo);
            else board.clearSpots();
        } else if (dev.freeRoads() > 0) {
            instruction.setText(myTurn
                    ? "Road Building: click a glowing edge to place a free road (" + dev.freeRoads() + " left)."
                    : current + " is placing free roads.");
            if (myTurn) {
                board.showRoadSpots(DevelopmentRules.legalFreeRoads(state, playerId), edgeId -> {
                    if (onPlaceRoad != null) onPlaceRoad.accept(edgeId);
                });
            } else {
                board.clearSpots();
            }
        } else {
            instruction.setText("");
            board.clearSpots();
        }
    }

    /** Robber tile clicked: pick who to steal from (asks only if there's a choice). */
    private void moveRobberTo(int tileId) {
        if (state == null || onMoveRobber == null) return;
        List<Integer> victims = DevelopmentRules.victims(state, playerId, tileId);
        int victim = -1;
        if (victims.size() == 1) {
            victim = victims.get(0);
        } else if (victims.size() > 1) {
            List<String> choices = new ArrayList<>();
            victims.forEach(id -> choices.add(nameOf(id, names)));
            ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
            prepare(dialog, "Knight", "Steal one random card from:");
            Optional<String> picked = dialog.showAndWait();
            if (picked.isEmpty()) return;                 // cancelled: tiles stay clickable
            victim = victims.get(choices.indexOf(picked.get()));
        }
        onMoveRobber.accept(tileId, victim);
    }

    /** Rebuilds this player's development cards along the bottom of the screen. */
    private void renderDevCards() {
        devCardRow.getChildren().clear();
        if (devHand.size() == 0) {
            Label none = label("None yet.\nBuy one for\n1 ore, 1 wool\nand 1 grain.", 12, TEXT, false);
            none.setMinHeight(90);
            devCardRow.getChildren().add(none);
            return;
        }
        for (DevelopmentCard card : DevelopmentCard.values()) {
            if (devHand.count(card) > 0) devCardRow.getChildren().add(devCardNode(card));
        }
    }

    private VBox devCardNode(DevelopmentCard card) {
        Node face;
        Image art = loadOptional("/cards/dev_" + card.name().toLowerCase(java.util.Locale.ROOT) + ".png");
        if (art != null) {                      // same size as the resource cards
            ImageView view = new ImageView(art);
            view.setFitWidth(64);
            view.setFitHeight(90);
            face = view;
        } else {                                // plain colored card if the image is missing
            Label title = label(card.label(), 11, "white", true);
            title.setAlignment(Pos.CENTER);
            title.setStyle(title.getStyle() + "-fx-text-alignment: center;");
            StackPane plain = new StackPane(title);
            plain.setPrefSize(64, 90);
            plain.setPadding(new Insets(4));
            plain.setStyle("-fx-background-color: " + DEV_COLORS.get(card) + "; -fx-background-radius: 8;"
                    + "-fx-border-color: " + GOLD + "; -fx-border-radius: 8; -fx-border-width: 2;");
            face = plain;
        }

        Label count = label(Integer.toString(devHand.count(card)), 14, BG, true);
        count.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + BG + ";"
                + "-fx-background-color: " + GOLD + "; -fx-background-radius: 12; -fx-padding: 1 8 1 8;"
                + "-fx-border-color: " + BG + "; -fx-border-radius: 12; -fx-border-width: 2;");
        StackPane cardPane = new StackPane(face, count);
        StackPane.setAlignment(count, Pos.TOP_RIGHT);

        // Hover explanation. These cards only exist on their owner's screen, so only they can see it.
        int fresh = devHand.freshCount(card);
        String tip = card.label().toUpperCase(java.util.Locale.ROOT) + "\n\n" + card.description();
        if (card != DevelopmentCard.VICTORY_POINT) {
            tip += "\n\nYou can play one development card per turn, even before you roll.";
        }
        if (fresh > 0 && card != DevelopmentCard.VICTORY_POINT) {
            Label isNew = label(fresh + " NEW", 10, BG, true);
            isNew.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + BG + ";"
                    + "-fx-background-color: " + TEXT + "; -fx-background-radius: 6; -fx-padding: 0 4 0 4;");
            cardPane.getChildren().add(isNew);
            StackPane.setAlignment(isNew, Pos.BOTTOM_CENTER);
            tip += "\n\n" + fresh + " bought this turn: you can play " + (fresh == 1 ? "it" : "them") + " starting next turn.";
        }
        Tooltip explain = new Tooltip(tip);
        explain.setWrapText(true);
        explain.setMaxWidth(280);
        explain.setShowDelay(javafx.util.Duration.millis(150));
        explain.setShowDuration(javafx.util.Duration.INDEFINITE);
        explain.setStyle("-fx-font-size: 13px; -fx-background-color: " + PANEL + "; -fx-text-fill: " + TEXT + ";"
                + "-fx-border-color: " + GOLD + "; -fx-border-width: 1.5; -fx-padding: 10;");
        Tooltip.install(cardPane, explain);

        Node below;
        if (card == DevelopmentCard.VICTORY_POINT) {
            below = label("+" + devHand.count(card) + " VP (secret)", 11, GOLD, true);
        } else {
            Button play = button("Play", GOLD_BUTTON);
            play.setStyle(GOLD_BUTTON + "-fx-font-size: 12px; -fx-padding: 2 10 2 10;");
            play.setMaxWidth(Double.MAX_VALUE);
            String problem = state == null ? "Waiting for the game." : DevelopmentRules.playProblem(state, devHand, playerId, card);
            play.setDisable(problem != null || onPlayDevelopmentCard == null);
            if (problem != null && myTurn && !setup) {
                // Disabled buttons don't show tooltips, so put the reason on the wrapper.
                StackPane wrapper = new StackPane(play);
                Tooltip.install(wrapper, new Tooltip(problem));
                below = wrapper;
            } else {
                below = play;
            }
            play.setOnAction(e -> playCard(card));
        }
        VBox box = new VBox(4, cardPane, below);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefWidth(74);
        return box;
    }

    /** Year of Plenty and Monopoly ask which resources first. */
    private void playCard(DevelopmentCard card) {
        if (onPlayDevelopmentCard == null) return;
        List<ResourceType> choices = switch (card) {
            case YEAR_OF_PLENTY -> askTwoResources();
            case MONOPOLY -> askOneResource();
            default -> List.of();
        };
        if (choices != null) onPlayDevelopmentCard.accept(card, choices);   // null = cancelled
    }

    private List<ResourceType> askOneResource() {
        List<String> names = resourceNames();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        prepare(dialog, "Monopoly", "Every other player gives you all of their:");
        return dialog.showAndWait().map(n -> List.of(CARD_SOURCES[names.indexOf(n)])).orElse(null);
    }

    private List<ResourceType> askTwoResources() {
        List<String> names = resourceNames();
        ComboBox<String> first = new ComboBox<>(), second = new ComboBox<>();
        first.getItems().setAll(names);
        second.getItems().setAll(names);
        first.setValue(names.get(0));
        second.setValue(names.get(0));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("First card:"), first);
        grid.addRow(1, new Label("Second card:"), second);

        Dialog<List<ResourceType>> dialog = new Dialog<>();
        prepare(dialog, "Year of Plenty", "Take any 2 resource cards from the bank.");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button != ButtonType.OK ? null : List.of(
                CARD_SOURCES[names.indexOf(first.getValue())], CARD_SOURCES[names.indexOf(second.getValue())]));
        return dialog.showAndWait().orElse(null);
    }

    private void prepare(Dialog<?> dialog, String title, String header) {
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        if (getScene() != null && getScene().getWindow() != null) dialog.initOwner(getScene().getWindow());
    }

    private static List<String> resourceNames() {
        List<String> names = new ArrayList<>();
        for (String n : CARD_NAMES) names.add(capitalize(n));
        return names;
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

    private HBox buildHand() {
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
        VBox resources = new VBox(8, label("YOUR CARDS", 13, TEXT, true), cards);
        resources.setAlignment(Pos.TOP_CENTER);

        // Only this player's development cards ever reach this screen; others just see a count.
        devCardRow.setAlignment(Pos.TOP_CENTER);
        VBox development = new VBox(8, label("DEVELOPMENT CARDS", 13, TEXT, true), devCardRow);
        development.setAlignment(Pos.TOP_CENTER);
        Region divider = new Region();
        divider.setPrefWidth(2);
        divider.setStyle("-fx-background-color: " + ROW + ";");

        HBox panel = new HBox(18, resources, divider, development);
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

            Pieces pieces = state.pieces();
            DevelopmentState dev = state.development();
            // VP cards are secret: you see your own, but other players' rows only show public points.
            int secretVp = id == playerId ? devHand.victoryPoints() : 0;
            String vp = (state.victoryPoints(id) + secretVp) + " VP" + (secretVp > 0 ? " (" + secretVp + " secret)" : "");
            Label detail = label(vp + "   ·   " + pieces.settlementCount(id) + " settlements   ·   "
                    + pieces.roadCount(id) + " roads", 12, TEXT, false);
            int devCount = dev.cardCount(id);
            Label devDetail = label(devCount + " dev card" + (devCount == 1 ? "" : "s") + "   ·   "
                    + dev.knights(id) + " knight" + (dev.knights(id) == 1 ? "" : "s")
                    + (dev.largestArmy() == id ? "   ·   Largest Army" : ""), 12,
                    dev.largestArmy() == id ? GOLD : TEXT, dev.largestArmy() == id);

            VBox row = new VBox(3, top, detail, devDetail);
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
        boolean busy = state != null && state.development().busy();   // robber or free roads still to do
        rollButton.setDisable(!playing || rolled || busy || onRoll == null);
        buyButton.setDisable(!playing || onBuy == null);
        tradeButton.setDisable(!playing || onTrade == null);
        endTurnButton.setDisable(!playing || !rolled || !diceRevealed || busy || onEndTurn == null);
        for (Button b : List.of(rollButton, buyButton, tradeButton)) {
            b.setTooltip(b.isDisabled() && playing ? new Tooltip("Coming soon") : null);
        }
        String buyProblem = state == null ? "Waiting for the game." : DevelopmentRules.buyProblem(state, playerId);
        devCardButton.setDisable(!playing || buyProblem != null || onBuyDevelopmentCard == null);
        devCardButton.setTooltip(new Tooltip(buyProblem != null && playing ? buyProblem
                : "Costs 1 ore, 1 wool and 1 grain. Only you will see which card you get."));
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
