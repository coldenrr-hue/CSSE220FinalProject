package finalproject.lobby;

import finalproject.network.LobbyServer;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.List;
import static finalproject.lobby.LobbyStyles.*;

/** Name, host address, port, and connection buttons. */
final class ConnectionPanel extends VBox {
    final TextField nameField = new TextField();
    final TextField hostField = new TextField();
    final TextField portField = new TextField(Integer.toString(LobbyServer.DEFAULT_PORT));
    final Button hostButton = new Button("Host a table");
    final Button joinButton = new Button("Join table");
    final Button leaveButton = new Button("Leave table");
    private final Label address = text("");

    ConnectionPanel() {
        super(10);
        nameField.setPromptText("Your name (max 20 characters)");
        limit(nameField, 20);
        hostField.setPromptText("Host IP, e.g. 192.168.1.20");
        getChildren().addAll(text("YOUR TABLE"), text("Your name"), nameField, text("Port"), portField,
                hostButton, new Separator(), hostField, joinButton, leaveButton, address,
                text("Same Wi-Fi or Ethernet\nOne host + three guests\nNo online account required"));
        for (Button b : List.of(hostButton, joinButton, leaveButton)) b.setMaxWidth(Double.MAX_VALUE);
        panel(this, 235);
    }

    void setConnected(boolean connected) {
        hostButton.setDisable(connected);
        joinButton.setDisable(connected);
        nameField.setDisable(connected);
        hostField.setDisable(connected);
        portField.setDisable(connected);
        leaveButton.setDisable(!connected);
    }

    void showAddress(String value) { address.setText(value); }
}
