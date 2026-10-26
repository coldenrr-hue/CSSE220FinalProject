package finalproject.lobby;

import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import java.util.List;
import static finalproject.lobby.LobbyStyles.*;

/** Renders the four seats and ready/start controls. */
final class PlayerPanel extends VBox {
    private final VBox seats = new VBox(10);
    final Button readyButton = new Button("Mark ready");
    final Button startButton = new Button("Start game");

    PlayerPanel() {
        super(12);
        getChildren().addAll(text("PLAYERS / 4 SEATS"), seats, readyButton, startButton);
        readyButton.setMaxWidth(Double.MAX_VALUE);
        startButton.setMaxWidth(Double.MAX_VALUE);
        reset();
    }

    void reset() {
        render(new LobbyState(false, List.of()), -1);
        readyButton.setDisable(true);
    }

    void render(LobbyState state, int playerId) {
        seats.getChildren().clear();
        for (int i = 0; i < 4; i++) seats.getChildren().add(seat(i, "Open seat", "Waiting for player"));
        for (LobbyState.Player player : state.players()) {
            String name = player.name() + (player.id() == 0 ? " · Host" : "")
                    + (player.id() == playerId ? " · You" : "");
            seats.getChildren().set(player.id(), seat(player.id(), name, player.ready() ? "Ready" : "Getting ready"));
        }
        readyButton.setText(state.isReady(playerId) ? "Cancel ready" : "Mark ready");
        readyButton.setDisable(state.started() || playerId < 0);
        startButton.setDisable(!state.canStart(playerId));
    }

    private VBox seat(int id, String name, String state) {
        VBox box = new VBox(4, text((id + 1) + "  " + name), text(state));
        String[] colors = {"#e37663", "#72b4e2", "#e6bb59", "#a4c395"};
        box.setStyle("-fx-background-color: #283e4d; -fx-padding: 10; -fx-background-radius: 7;"
                + "-fx-border-color: " + colors[id] + "; -fx-border-width: 0 0 0 4;");
        return box;
    }
}
