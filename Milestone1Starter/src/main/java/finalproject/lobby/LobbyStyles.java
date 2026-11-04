package finalproject.lobby;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;

/** Shared appearance helpers for the lobby panels. */
final class LobbyStyles {
    private LobbyStyles() { }

    static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #d5e6ef;");
        return label;
    }

    static void panel(VBox box, double width) {
        box.setPrefWidth(width);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #1d303e; -fx-background-radius: 12;");
    }

    static void limit(TextField field, int length) {
        field.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= length ? c : null));
    }
}
