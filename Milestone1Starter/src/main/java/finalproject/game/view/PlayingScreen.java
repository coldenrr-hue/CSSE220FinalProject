package finalproject.game.view;

import finalproject.game.GameState;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import java.util.Map;

/** Dedicated playing screen: board in the center, player roster and chat at the side. */
public final class PlayingScreen extends BorderPane {
    private final GameBoardView board = new GameBoardView();
    private final VBox playerRows = new VBox(9);
    private final VBox sidebar = new VBox(12);
    private final Button leave = new Button("Leave game");
    private final Button fullscreen = new Button("Toggle full screen");
    private final Label status = text("");
    private Node chat;

    public PlayingScreen() {
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #12202c;");
        Label title = text("CATAN / THE ISLAND");
        title.setStyle("-fx-text-fill: #f3cc80; -fx-font-size: 22px; -fx-font-weight: bold;");
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox header = new HBox(12, title, space, fullscreen, leave);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(0,0,16,0));
        setTop(header);
        board.setPadding(new Insets(0,18,0,0));
        setCenter(board);
        sidebar.setPrefWidth(245);
        sidebar.setPadding(new Insets(16));
        sidebar.setStyle("-fx-background-color: #1d303e; -fx-background-radius: 12;");
        sidebar.getChildren().addAll(text("PLAYERS / TURN ORDER"), playerRows, new Separator());
        setRight(sidebar);
        BorderPane.setMargin(status, new Insets(12,0,0,0));
        setBottom(status);
    }

    public void attachChat(Node chat) {
        this.chat = chat;
        if (!sidebar.getChildren().contains(chat)) sidebar.getChildren().add(chat);
        VBox.setVgrow(chat, Priority.ALWAYS);
    }
    public void detachChat() { sidebar.getChildren().remove(chat); chat = null; }
    public void onLeave(Runnable action) { leave.setOnAction(e -> action.run()); }
    public void onFullscreen(Runnable action) { fullscreen.setOnAction(e -> action.run()); }
    public void onEndTurn(Runnable action) { board.onEndTurn(action); }
    public GameBoardView board() { return board; }
    public void showStatus(String value) { status.setText(value); }

    public void render(GameState state, int playerId, Map<Integer, String> names) {
        board.render(state, playerId, names);
        playerRows.getChildren().clear();
        String[] colors = {"#e37663", "#72b4e2", "#e6bb59", "#a4c395"};
        for (int id : state.turnOrder()) {
            boolean active = id == state.currentPlayerId();
            Label name = text(names.getOrDefault(id, "Player " + (id+1)) + (id == playerId ? " · You" : ""));
            VBox row = new VBox(3, name, text(active ? "▶ Current turn" : "Waiting"));
            row.setStyle("-fx-padding: 10; -fx-background-color: " + (active ? "#365363" : "#283e4d")
                    + "; -fx-background-radius: 7; -fx-border-width: 0 0 0 4; -fx-border-color: " + colors[id] + ";");
            playerRows.getChildren().add(row);
        }
    }

    public void reset() { board.reset(); playerRows.getChildren().clear(); }

    private static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #d5e6ef;");
        return label;
    }
}
