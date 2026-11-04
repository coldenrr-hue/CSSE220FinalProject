package finalproject.game.view;

import finalproject.game.Board;
import finalproject.game.GamePhase;
import finalproject.game.GameState;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared board, ordered player names, active turn, and a prototype advance button. */
public final class GameBoardView extends VBox {
    private final Label currentTurn = text("Waiting for four ready players");
    private final Label turnOrder = text("The host will randomize the board and player order when the game starts.");
    private final HexBoardView boardView = new HexBoardView();
    private final Button endTurn = new Button("End turn");
    private final Label hint = text("Blank tile artwork • Resource labels and number tokens appear when the game starts.");
    private Board renderedBoard;
    private GameState state;

    public GameBoardView() {
        super(10);
        currentTurn.setStyle("-fx-text-fill: #f3cc80; -fx-font-size: 18px; -fx-font-weight: bold;");
        getChildren().addAll(currentTurn, turnOrder, boardView, endTurn, hint);
        VBox.setVgrow(boardView, Priority.ALWAYS);
        endTurn.setDisable(true);
    }

    public void onEndTurn(Runnable action) { endTurn.setOnAction(e -> action.run()); }
    public GameState state() { return state; }

    public void render(GameState state, int playerId, Map<Integer, String> names) {
        this.state = state;
        if (!state.board().equals(renderedBoard)) {
            boardView.render(state.board());
            renderedBoard = state.board();
        }
        String name = names.getOrDefault(state.currentPlayerId(), "Player " + (state.currentPlayerId() + 1));
        boolean mine = state.currentPlayerId() == playerId;
        String phase = switch (state.phase()) {
            case SETUP_FORWARD -> "Setup • first round";
            case SETUP_REVERSE -> "Setup • reverse round";
            case PLAYING -> "Turn " + state.turnNumber();
        };
        currentTurn.setText(phase + "  /  " + name + (mine ? " (your turn)" : ""));
        turnOrder.setText("Player order: " + state.turnOrder().stream()
                .map(id -> (id == state.currentPlayerId() ? "▶ " : "") + names.getOrDefault(id, "Player " + (id + 1)))
                .collect(Collectors.joining(" → ")));
        endTurn.setText(state.phase() == GamePhase.PLAYING ? "End turn" : "Advance setup (test)");
        endTurn.setDisable(!mine);
        hint.setText(state.phase() == GamePhase.PLAYING
                ? "Turn test only • Dice and building rules are not connected yet."
                : "Setup test only • Settlements and roads are not placed yet.");
    }

    public void reset() {
        state = null;
        renderedBoard = null;
        boardView.showWaiting();
        currentTurn.setText("Waiting for four ready players");
        turnOrder.setText("The host will randomize the board and player order when the game starts.");
        endTurn.setText("End turn");
        endTurn.setDisable(true);
        hint.setText("Blank tile artwork • Resource labels and number tokens appear when the game starts.");
    }

    private static Label text(String content) {
        Label label = new Label(content);
        label.setStyle("-fx-text-fill: #d5e6ef;");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }
}
