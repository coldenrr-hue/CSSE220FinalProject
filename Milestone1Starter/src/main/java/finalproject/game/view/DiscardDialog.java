package finalproject.game.view;

import finalproject.game.ResourceType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a player choose exactly how many cards of each resource to return after a seven.
 * The dialog uses the player's current hand to limit quantities and returns the chosen cards.
 */
final class DiscardDialog {
    private DiscardDialog() { }

    static List<ResourceType> show(Window owner, List<ResourceType> hand, int required) {
        Dialog<List<ResourceType>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Discard resource cards");
        dialog.setHeaderText("Choose " + required + " resource cards to return to the bank.");

        GridPane rows = new GridPane();
        rows.setHgap(12);
        rows.setVgap(8);
        Map<ResourceType, ComboBox<Integer>> quantities = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            int held = Collections.frequency(hand, resource);
            if (held == 0) continue;

            ComboBox<Integer> quantity = new ComboBox<>();
            for (int count = 0; count <= Math.min(held, required); count++) {
                quantity.getItems().add(count);
            }
            quantity.setValue(0);
            quantities.put(resource, quantity);
            Label resourceLabel = new Label(resource.cardName() + " (" + held + " held)");
            resourceLabel.setLabelFor(quantity);
            rows.addRow(quantities.size() - 1, resourceLabel, quantity);
        }

        Label progress = new Label();
        rows.add(progress, 0, quantities.size(), 2, 1);
        ButtonType confirm = new ButtonType("Discard", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(rows);
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        Runnable update = () -> {
            int selected = quantities.values().stream().mapToInt(ComboBox::getValue).sum();
            progress.setText(selected + " of " + required + " selected");
            dialog.getDialogPane().lookupButton(confirm).setDisable(selected != required);
        };
        quantities.values().forEach(quantity -> quantity.valueProperty().addListener(observable -> update.run()));
        update.run();

        dialog.setResultConverter(button -> {
            if (button != confirm) return null;
            List<ResourceType> chosen = new ArrayList<>();
            quantities.forEach((resource, quantity) -> chosen.addAll(Collections.nCopies(quantity.getValue(), resource)));
            return chosen;
        });
        return dialog.showAndWait().orElse(null);
    }
}
