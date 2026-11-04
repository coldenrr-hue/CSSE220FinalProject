package finalproject.lobby;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;
import static finalproject.lobby.LobbyStyles.*;

/** Chat history and message input; sends through a controller callback. */
final class ChatPanel extends VBox {
    private final TextArea history = new TextArea();
    private final TextField message = new TextField();

    ChatPanel() {
        super(12);
        history.setEditable(false);
        history.setWrapText(true);
        history.setPrefRowCount(5);
        message.setPromptText("Message your table… (Enter)");
        limit(message, 300);
        getChildren().addAll(text("TABLE CHAT"), history, message);
        VBox.setVgrow(history, Priority.ALWAYS);
        setConnected(false);
    }

    void onSend(Consumer<String> send) {
        message.setOnAction(e -> {
            if (!message.getText().isBlank()) {
                send.accept(message.getText());
                message.clear();
            }
        });
    }

    void append(String name, String content) {
        history.appendText(name + ": " + content + "\n");
        if (history.getLength() > 20000) history.deleteText(0, history.getLength() - 16000);
    }

    void clear() { history.clear(); message.clear(); }
    void setConnected(boolean connected) { message.setDisable(!connected); }
}
