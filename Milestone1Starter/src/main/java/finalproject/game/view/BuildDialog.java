package finalproject.game.view;

import finalproject.game.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

/** Illustrated building menu. Selecting a piece starts placement, without spending cards. */
public final class BuildDialog {
    private static final String[] COLORS = {"red", "blue", "yellow", "green"};
    private BuildDialog() { }

    public static Dialog<Void> show(GameState state, int player, Consumer<GameRules.Build> select) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Build & upgrade");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(BuildDialog.class.getResource("/styles/trading.css")).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("trade-dialog");
        HBox choices = new HBox(12);
        for (GameRules.Build kind : GameRules.Build.values()) {
            choices.getChildren().add(buildOption(state, player, kind, dialog, select));
        }
        VBox body = new VBox(14, text("Build your island", "trade-title"),
                text("Choose a piece, then select a highlighted location on the board.", "trade-subtitle"), choices,
                text("City upgrades cost 3 ore + 2 grain. Your settlement returns to your supply.\nResources are spent only when you place or upgrade a piece.", "trade-summary"));
        body.getStyleClass().add("trade-body");
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(748); scroll.setPrefViewportHeight(690);
        dialog.getDialogPane().setContent(scroll); dialog.setResizable(true);
        dialog.show(); return dialog;
    }

    private static VBox buildOption(GameState state, int player, GameRules.Build kind,
                                    Dialog<Void> dialog, Consumer<GameRules.Build> select) {
        boolean city = kind == GameRules.Build.CITY;
        String title = switch (kind) { case ROAD -> "Road"; case SETTLEMENT -> "Settlement"; case CITY -> "City upgrade"; };
        Node picture = piece(kind.name().toLowerCase(Locale.ROOT), player, 88);
        if (city) {
            HBox upgrade = new HBox(5, piece("settlement", player, 48), text("\u2192", "trade-count"), picture);
            upgrade.setAlignment(Pos.CENTER); picture = upgrade;
        }
        StackPane art = new StackPane(picture); art.setPrefHeight(104);
        art.getStyleClass().add("build-art");
        Label benefit = text(switch (kind) {
            case ROAD -> "Extend your route to new locations.";
            case SETTLEMENT -> "1 victory point. Collect 1 resource from each matching tile.";
            case CITY -> "Replace your settlement. 2 victory points and double resource production.";
        }, "trade-subtitle");
        benefit.setMinHeight(65);
        FlowPane costs = new FlowPane(7, 7); costs.setPrefWrapLength(185); costs.setMinHeight(88);
        List<ResourceType> cost = kind.cost();
        for (ResourceType resource : ResourceType.values()) {
            int needed = Collections.frequency(cost, resource);
            if (needed == 0) continue;
            int held = Collections.frequency(state.assets().hand(player), resource);
            Node icon = image(resource.iconPath(), 25);
            Label amount = text(needed + " " + resource.cardName(), "trade-resource-name");
            HBox chip = new HBox(5, icon, amount); chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add(held >= needed ? "build-cost" : "build-cost-missing");
            Tooltip.install(chip, new Tooltip("Need " + needed + " " + resource.cardName() + "; you have " + held + "."));
            costs.getChildren().add(chip);
        }
        int remaining = kind.remainingPieces(state.assets(), player);
        var spots = GameRules.legalBuilds(state, player, kind);
        String reason;
        if (state.currentPlayerId() != player || GameRules.isSetup(state) || !state.dice().rolled()) reason = "Roll on your turn to build.";
        else if (remaining <= 0) reason = "All of these pieces are on the board.";
        else if (city && state.assets().settlementCount(player) == 0) reason = "Build a settlement first to upgrade it.";
        else if (!GameRules.hasResources(state.assets(), player, cost)) reason = "You need more of the highlighted resources.";
        else if (spots.isEmpty()) reason = "No connected legal locations available.";
        else reason = city ? spots.size() + " settlement(s) ready to upgrade." : spots.size() + " legal location(s) available.";
        Label availability = text(reason, spots.isEmpty() ? "trade-hint" : "build-ready");
        availability.setMinHeight(48);
        Button action = new Button(city ? "Upgrade settlement" : "Place " + title.toLowerCase(Locale.ROOT));
        action.setId("build-" + kind.name()); action.getStyleClass().add("trade-primary");
        action.setMaxWidth(Double.MAX_VALUE); action.setDisable(spots.isEmpty());
        action.setOnAction(e -> { dialog.close(); select.accept(kind); });
        VBox card = new VBox(10, art, text(title, "build-title"), benefit,
                text("RESOURCE COST", "trade-section-title"), costs,
                text(remaining + " pieces remaining", "trade-hint"), availability, action);
        card.getStyleClass().add("build-card"); card.setPrefWidth(228); card.setMinWidth(228);
        return card;
    }

    private static Node piece(String kind, int player, int size) {
        return image("/pieces/" + kind + "_" + COLORS[player] + ".png", size);
    }
    private static Node image(String path, int size) {
        ImageView view = new ImageView(new Image(Objects.requireNonNull(BuildDialog.class.getResource(path)).toExternalForm()));
        view.setFitWidth(size); view.setFitHeight(size); view.setPreserveRatio(true); return view;
    }
    private static Label text(String value, String style) {
        Label label = new Label(value); label.getStyleClass().add(style); label.setWrapText(true); return label;
    }
}
