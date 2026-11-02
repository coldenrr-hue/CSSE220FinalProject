package finalproject.lobby;

import finalproject.game.GameState;
import finalproject.game.view.PlayingScreen;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.HashMap;
import java.util.Map;
import static finalproject.lobby.LobbyStyles.*;

/** Screen coordinator. The same connection and chat survive the lobby/game transition. */
public final class LobbyView extends BorderPane {
    private final ConnectionPanel connection = new ConnectionPanel();
    private final PlayerPanel players = new PlayerPanel();
    private final ChatPanel chat = new ChatPanel();
    private final Label status = text("Create a table or join a friend on the same network.");
    private final BorderPane lobbyScreen = new BorderPane();
    private final VBox lobbySidebar = new VBox(12);
    private final PlayingScreen playingScreen = new PlayingScreen();
    private final Map<Integer, String> playerNames = new HashMap<>();
    private Consumer<Boolean> screenChanged = playing -> { };
    private boolean playing;

    public LobbyView() {
        setStyle("-fx-background-color: #12202c; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
        lobbyScreen.setPadding(new Insets(22));
        Label title = text("CATAN / LOCAL TABLE");
        title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: #f3cc80;");
        VBox header = new VBox(7, title, text("LOCAL MULTIPLAYER / LOBBY"));
        header.setPadding(new Insets(0, 0, 20, 0));
        lobbyScreen.setTop(header);
        lobbyScreen.setLeft(connection);
        LobbyArtwork artwork = new LobbyArtwork();
        BorderPane.setMargin(artwork, new Insets(0,18,0,18));
        lobbyScreen.setCenter(artwork);
        lobbySidebar.getChildren().addAll(players, new Separator(), chat);
        panel(lobbySidebar, 260);
        VBox.setVgrow(chat, Priority.ALWAYS);
        lobbyScreen.setRight(lobbySidebar);
        BorderPane.setMargin(status, new Insets(18, 0, 0, 0));
        lobbyScreen.setBottom(status);
        playingScreen.onFullscreen(() -> {
            if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
                stage.setFullScreen(!stage.isFullScreen());
            }
        });
        setCenter(lobbyScreen);
        reset();
    }

    public String playerName() { return connection.nameField.getText().strip(); }
    public String hostAddress() { return connection.hostField.getText().strip(); }
    public String portText() { return connection.portField.getText().strip(); }
    public boolean isPlaying() { return playing; }
    public GameState gameState() { return playingScreen.board().state(); }
    public void onScreenChanged(Consumer<Boolean> action) { screenChanged = action; }

    public void prefill(String name, String host, int port) {
        connection.nameField.setText(name);
        connection.hostField.setText(host);
        connection.portField.setText(Integer.toString(port));
    }
    public void onHost(Runnable action) { connection.hostButton.setOnAction(e -> action.run()); }
    public void onJoin(Runnable action) { connection.joinButton.setOnAction(e -> action.run()); }
    public void onLeave(Runnable action) {
        connection.leaveButton.setOnAction(e -> action.run());
        playingScreen.onLeave(action);
    }
    public void onReady(Runnable action) { players.readyButton.setOnAction(e -> action.run()); }
    public void onStart(Runnable action) { players.startButton.setOnAction(e -> action.run()); }
    public void onChat(Consumer<String> action) { chat.onSend(action); }
    public void onEndTurn(Runnable action) { playingScreen.onEndTurn(action); }
    public void onPlaceSettlement(IntConsumer action) { playingScreen.onPlaceSettlement(action); }
    public void onPlaceRoad(IntConsumer action) { playingScreen.onPlaceRoad(action); }
    public void showStatus(String value) { status.setText(value); playingScreen.showStatus(value); }
    public void showAddress(String value) { connection.showAddress(value); }
    public void appendChat(String name, String message) { chat.append(name, message); }
    public void clearChat() { chat.clear(); }
    public void connecting() { connection.setConnected(true); }

    public void render(LobbyState state, int playerId) {
        playerNames.clear();
        state.players().forEach(player -> playerNames.put(player.id(), player.name()));
        players.render(state, playerId);
        chat.setConnected(true);
        if (!state.started()) {
            showLobby();
            playingScreen.reset();
            showStatus("Lobby: " + state.players().size() + "/4 connected. All four players must mark ready.");
        }
    }

    public void renderGame(GameState state, int playerId) {
        playingScreen.render(state, playerId, playerNames);
        if (!playing) {
            lobbySidebar.getChildren().remove(chat);
            playingScreen.attachChat(chat);
            setCenter(playingScreen);
            playing = true;
            screenChanged.accept(true);
        }
        showStatus(state.currentPlayerId() == playerId ? "Your turn." : "Waiting for " + playerNames.get(state.currentPlayerId()) + ".");
    }

    private void showLobby() {
        if (!playing) return;
        playingScreen.detachChat();
        lobbySidebar.getChildren().add(chat);
        setCenter(lobbyScreen);
        playing = false;
        screenChanged.accept(false);
    }

    public void reset() {
        showLobby();
        connection.setConnected(false);
        connection.showAddress("");
        players.reset();
        chat.setConnected(false);
        playerNames.clear();
        playingScreen.reset();
    }
}