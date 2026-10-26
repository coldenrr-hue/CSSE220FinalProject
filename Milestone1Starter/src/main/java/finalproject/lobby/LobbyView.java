package finalproject.lobby;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import java.util.function.Consumer;
import static finalproject.lobby.LobbyStyles.*;

/** Composes panels and exposes UI actions to the controller. No socket code. */
public final class LobbyView extends BorderPane {
    private final ConnectionPanel connection = new ConnectionPanel();
    private final PlayerPanel players = new PlayerPanel();
    private final ChatPanel chat = new ChatPanel();
    private final Label phase = text("LOCAL MULTIPLAYER / LOBBY");
    private final Label status = text("Create a table or join a friend on the same network.");

    public LobbyView() {
        setPadding(new Insets(22));
        setStyle("-fx-background-color: #12202c; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
        Label title = text("CATAN / LOCAL TABLE");
        title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: #f3cc80;");
        VBox header = new VBox(7, title, phase);
        header.setPadding(new Insets(0, 0, 20, 0));
        setTop(header);
        setLeft(connection);
        BoardPreview board = new BoardPreview();
        VBox center = new VBox(12, board, text("BOARD PREVIEW • Game rules will connect here"));
        VBox.setVgrow(board, Priority.ALWAYS);
        center.setPadding(new Insets(0, 18, 0, 18));
        setCenter(center);
        VBox sidebar = new VBox(12, players, new Separator(), chat);
        panel(sidebar, 260);
        VBox.setVgrow(chat, Priority.ALWAYS);
        setRight(sidebar);
        BorderPane.setMargin(status, new Insets(18, 0, 0, 0));
        setBottom(status);
        reset();
    }

    public String playerName() { return connection.nameField.getText().strip(); }
    public String hostAddress() { return connection.hostField.getText().strip(); }
    public String portText() { return connection.portField.getText().strip(); }
    /** Prefills the form for the optional four-window local test launcher. */
    public void prefill(String name, String host, int port) {
        connection.nameField.setText(name);
        connection.hostField.setText(host);
        connection.portField.setText(Integer.toString(port));
    }
    public void onHost(Runnable action) { connection.hostButton.setOnAction(e -> action.run()); }
    public void onJoin(Runnable action) { connection.joinButton.setOnAction(e -> action.run()); }
    public void onLeave(Runnable action) { connection.leaveButton.setOnAction(e -> action.run()); }
    public void onReady(Runnable action) { players.readyButton.setOnAction(e -> action.run()); }
    public void onStart(Runnable action) { players.startButton.setOnAction(e -> action.run()); }
    public void onChat(Consumer<String> action) { chat.onSend(action); }
    public void showStatus(String value) { status.setText(value); }
    public void showAddress(String value) { connection.showAddress(value); }
    public void appendChat(String name, String message) { chat.append(name, message); }
    public void clearChat() { chat.clear(); }

    public void connecting() { connection.setConnected(true); }

    public void render(LobbyState state, int playerId) {
        players.render(state, playerId);
        chat.setConnected(true);
        phase.setText(state.started() ? "LOCAL MULTIPLAYER / GAME HANDOFF" : "LOCAL MULTIPLAYER / LOBBY");
        if (!state.started()) showStatus("Lobby: " + state.players().size() + "/4 connected. All four players must mark ready.");
    }

    public void reset() {
        connection.setConnected(false);
        connection.showAddress("");
        players.reset();
        chat.setConnected(false);
        phase.setText("LOCAL MULTIPLAYER / LOBBY");
    }
}
