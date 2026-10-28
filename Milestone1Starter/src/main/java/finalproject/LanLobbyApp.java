package finalproject;

import finalproject.lobby.LobbyController;
import finalproject.lobby.LobbyView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Creates the window and connects the lobby view to its controller. */
public class LanLobbyApp extends Application {
    private LobbyView view;
    private LobbyController controller;

    @Override public void start(Stage stage) {
        view = new LobbyView();
        stage.setFullScreenExitHint("Press Esc to leave full screen. The game will keep running.");
        view.onScreenChanged(stage::setFullScreen);
        controller = new LobbyController(view, this::onGameStarted);
        stage.setTitle("Catan • Local Table");
        stage.setMinWidth(1050);
        stage.setMinHeight(700);
        stage.setScene(new Scene(view, 1280, 790));
        stage.setOnHidden(event -> stop());
        stage.show();
    }

    /** Teammates can open their game controller here on every laptop. */
    protected void onGameStarted() {
        view.showStatus("Starting game. Waiting for the host's board and turn order…");
    }

    @Override public void stop() {
        if (controller != null) controller.close();
    }
}
