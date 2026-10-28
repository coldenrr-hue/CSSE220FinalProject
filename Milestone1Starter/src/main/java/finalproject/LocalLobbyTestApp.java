package finalproject;

import finalproject.lobby.LobbyController;
import finalproject.lobby.LobbyView;
import finalproject.network.LobbyServer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;

/** Test to make sure all 4 work together */
public final class LocalLobbyTestApp extends Application {
    private final List<LobbyController> controllers = new ArrayList<>();

    @Override public void start(Stage primaryStage) {
        for (int i = 0; i < 4; i++) {
            String name = i == 0 ? "Host laptop" : "Guest laptop " + i;
            LobbyView view = new LobbyView();
            view.prefill(name, "127.0.0.1", LobbyServer.DEFAULT_PORT);
            LobbyController controller = new LobbyController(view, () -> view.showStatus(
                    "Everyone is ready."));
            controllers.add(controller);
            Stage stage = i == 0 ? primaryStage : new Stage();
            stage.setTitle("Local test / " + name);
            stage.setMinWidth(1050);
            stage.setMinHeight(700);
            stage.setScene(new Scene(view, 1050, 700));
            stage.setX(30 + i * 35);
            stage.setY(30 + i * 35);
            stage.setOnHidden(e -> controller.close());
            stage.show();
        }
    }

    @Override public void stop() { controllers.forEach(LobbyController::close); }

    public static void main(String[] args) { launch(args); }
}
