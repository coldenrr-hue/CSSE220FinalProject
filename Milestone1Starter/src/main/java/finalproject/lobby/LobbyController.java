package finalproject.lobby;

import finalproject.game.GameState;
import finalproject.network.GameStateCodec;
import finalproject.network.LobbyClient;
import finalproject.network.LobbyServer;
import finalproject.network.NetworkAddresses;
import javafx.application.Platform;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Connects UI actions to the LAN transport and applies incoming lobby events. */
public final class LobbyController implements AutoCloseable {
    private final LobbyView view;
    private final Runnable gameStarted;
    private LobbyServer server;
    private LobbyClient client;
    private int generation;
    private int playerId = -1;
    private boolean ready;
    private String serverError;
    private GameState latestGame;

    public LobbyController(LobbyView view, Runnable gameStarted) {
        this.view = view;
        this.gameStarted = gameStarted;
        view.onHost(() -> connect(true));
        view.onJoin(() -> connect(false));
        view.onLeave(() -> { close(); view.showStatus("You left the table."); });
        view.onReady(() -> send("READY", Boolean.toString(!ready)));
        view.onStart(() -> send("START"));
        view.onChat(message -> send("CHAT", message));
        view.onEndTurn(() -> {
            if (latestGame != null) send("END_TURN", Long.toString(latestGame.revision()));
        });
    }

    private void send(String... fields) {
        if (client != null) client.send(fields);
    }

    private void connect(boolean hosting) {
        String name = view.playerName();
        String host = hosting ? "127.0.0.1" : view.hostAddress();
        int port;
        try {
            port = Integer.parseInt(view.portText());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) { view.showStatus("Enter a port from 1 to 65535."); return; }
        if (name.isEmpty() || host.isEmpty()) {
            view.showStatus("Enter your name and the host IP when joining.");
            return;
        }
        close();
        serverError = null;
        int attempt = generation;
        view.connecting();
        view.showStatus(hosting ? "Opening your table…" : "Connecting to " + host + "…");
        CompletableFuture.runAsync(() -> openConnection(hosting, host, port, name, attempt));
    }

    private void openConnection(boolean hosting, String host, int port, String name, int attempt) {
        LobbyServer newServer = null;
        LobbyClient newClient = null;
        try {
            if (hosting) newServer = new LobbyServer(port);
            newClient = new LobbyClient(host, port, name, hosting ? newServer.hostToken() : "",
                    fields -> Platform.runLater(() -> { if (attempt == generation) receive(fields); }),
                    error -> Platform.runLater(() -> {
                        if (attempt == generation) {
                            String reason = serverError == null ? error : serverError;
                            close();
                            view.showStatus(reason);
                        }
                    }));
            LobbyServer connectedServer = newServer;
            LobbyClient connectedClient = newClient;
            String addresses = hosting ? NetworkAddresses.localAddresses() : host;
            Platform.runLater(() -> {
                if (attempt != generation) {
                    closeResources(connectedClient, connectedServer);
                    return;
                }
                server = connectedServer;
                client = connectedClient;
                view.showAddress((hosting ? "Share a LAN address:\n" : "Host: ") + addresses + "\nPort: " + port);
            });
        } catch (Exception e) {
            closeResources(newClient, newServer);
            Platform.runLater(() -> {
                if (attempt == generation) {
                    close();
                    view.showStatus("Could not connect: " + e.getMessage() + ". Check the address, port, and firewall.");
                }
            });
        }
    }

    private void receive(List<String> fields) {
        switch (fields.get(0)) {
            case "WELCOME" -> { playerId = Integer.parseInt(fields.get(1)); view.clearChat(); }
            case "ERROR" -> {
                serverError = "Server: " + fields.get(1);
                view.showStatus(serverError);
            }
            case "CHAT" -> view.appendChat(fields.get(1), fields.get(2));
            case "STATE" -> {
                LobbyState state = LobbyState.fromMessage(fields);
                if (!state.started()) latestGame = null;
                ready = state.isReady(playerId);
                view.render(state, playerId);
            }
            case "STARTED" -> gameStarted.run();
            case "GAME_STATE" -> {
                try {
                    GameState state = GameStateCodec.decode(fields);
                    if (latestGame == null || state.revision() > latestGame.revision()) {
                        latestGame = state;
                        view.renderGame(state, playerId);
                    }
                } catch (IllegalArgumentException e) {
                    view.showStatus("The host sent an invalid game snapshot: " + e.getMessage());
                }
            }
            default -> { }
        }
    }

    private static void closeResources(LobbyClient client, LobbyServer server) {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    @Override public void close() {
        generation++;
        closeResources(client, server);
        client = null;
        server = null;
        playerId = -1;
        ready = false;
        latestGame = null;
        view.reset();
    }
}
