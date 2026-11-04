package finalproject.game.view;

import finalproject.game.*;
import finalproject.network.GameStateCodec;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Line;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.function.Consumer;

/** Builds a proposal locally; only the host can exchange the selected cards. */
public final class TradeDialog {
    private TradeDialog() { }
    public static Dialog<Void> show(GameState state, int player, Map<Integer, String> names, Consumer<String[]> send) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Trade resources");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(TradeDialog.class.getResource("/styles/trading.css")).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("trade-dialog");
        dialog.setResizable(true);
        List<Integer> seats = state.turnOrder().stream().filter(id -> id != player).toList();
        ToggleGroup partners = new ToggleGroup();
        HBox partnerBar = new HBox(8);
        ToggleButton bank = partnerButton("Bank & docks", -1, partners);
        bank.setGraphic(ship());
        partnerBar.getChildren().add(bank);
        for (int seat : seats) {
            ToggleButton button = partnerButton(names.getOrDefault(seat, "Player " + (seat + 1)), seat, partners);
            javafx.scene.shape.Circle avatar = new javafx.scene.shape.Circle(7, Color.web(HexBoardView.SEAT_COLORS[seat]));
            button.setGraphic(avatar);
            partnerBar.getChildren().add(button);
        }
        bank.setSelected(true);
        partners.selectedToggleProperty().addListener((o, previous, selected) -> {
            if (selected == null && previous != null) previous.setSelected(true);
        });
        Map<ResourceType, Spinner<Integer>> give = new EnumMap<>(ResourceType.class), receive = new EnumMap<>(ResourceType.class);
        HBox giveCards = new HBox(10), receiveCards = new HBox(10);
        giveCards.setAlignment(Pos.CENTER); receiveCards.setAlignment(Pos.CENTER);
        for (ResourceType r : ResourceType.values()) {
            if (r == ResourceType.DESERT) continue;
            int held = (int) state.assets().hand(player).stream().filter(c -> c == r).count();
            Spinner<Integer> a = new Spinner<>(0, held, 0), b = new Spinner<>(0, 19, 0);
            give.put(r, a); receive.put(r, b);
            giveCards.getChildren().add(resourceCard(r, a, held + " held / " + HarborRules.tradeRate(state, player, r) + ":1 bank", "give-"));
            receiveCards.getChildren().add(resourceCard(r, b, "Choose quantity", "receive-"));
        }
        Label hint = text("", "trade-hint");
        Label summary = text("Choose resources below to prepare your trade.", "trade-summary");
        Label error = text("", "trade-error");
        error.setVisible(false); error.setManaged(false);
        Button submit = new Button("Trade with bank"); submit.setId("trade-submit");
        submit.getStyleClass().add("trade-primary"); submit.setMaxWidth(Double.MAX_VALUE);
        Label receiveTitle = text("YOU RECEIVE", "trade-section-title");
        Runnable refresh = () -> {
            boolean isBank = (int) partners.getSelectedToggle().getUserData() == -1;
            submit.setText(isBank ? "Complete bank trade" : "Send trade offer");
            receiveTitle.setText(isBank ? "YOU RECEIVE  /  FROM THE BANK" : "YOU RECEIVE  /  REQUEST FROM PLAYER");
            hint.setText(isBank ? "Use one resource on each side. Your docks automatically give you the best rate."
                    : "Mix resources to make a deal. The other player must accept before cards move.");
            List<ResourceType> offered = selected(give), requested = selected(receive);
            summary.setText((offered.isEmpty() ? "Choose what to give" : ResourceType.describeCards(offered))
                    + "  \u2192  " + (requested.isEmpty() ? "Choose what to receive" : ResourceType.describeCards(requested)));
            submit.setDisable(offered.isEmpty() || requested.isEmpty());
            error.setVisible(false); error.setManaged(false);
        };
        give.values().forEach(spinner -> spinner.valueProperty().addListener((o, a, b) -> refresh.run()));
        receive.values().forEach(spinner -> spinner.valueProperty().addListener((o, a, b) -> refresh.run()));
        partners.selectedToggleProperty().addListener((o, a, b) -> { if (b != null) refresh.run(); });
        refresh.run();
        submit.setOnAction(event -> {
            try {
                List<ResourceType> offered = selected(give);
                List<ResourceType> requested = selected(receive);
                String revision = Long.toString(state.revision());
                if ((int) partners.getSelectedToggle().getUserData() == -1) {
                    if (new HashSet<>(offered).size() != 1 || new HashSet<>(requested).size() != 1)
                        throw new IllegalArgumentException("Select one resource to give and one to receive.");
                    ResourceType a = offered.getFirst(), b = requested.getFirst();
                    if (offered.size() != HarborRules.tradeRate(state, player, a) * requested.size())
                        throw new IllegalArgumentException("Give " + HarborRules.tradeRate(state, player, a) + " cards for each card you receive.");
                    GameRules.bankTrade(state, player, a, b, requested.size());
                    send.accept(new String[]{"BANK_TRADE", a.name(), b.name(), "" + requested.size(), revision});
                } else {
                    TradeOffer offer = new TradeOffer(player, (int) partners.getSelectedToggle().getUserData(), offered, requested);
                    GameRules.validateOffer(state, offer);
                    send.accept(new String[]{"TRADE_OFFER", "" + offer.to(), GameStateCodec.encodeCards(offered), GameStateCodec.encodeCards(requested), revision});
                }
                dialog.close();
            } catch (IllegalArgumentException ex) { error.setText(ex.getMessage()); error.setVisible(true); error.setManaged(true); }
        });
        Label title = text("The trading post", "trade-title");
        Label subtitle = text("Turn your resources into your next move.", "trade-subtitle");
        VBox header = new VBox(3, title, subtitle);
        VBox giving = new VBox(8, text("YOU GIVE  /  FROM YOUR HAND", "trade-section-title"), giveCards);
        giving.getStyleClass().add("trade-section");
        VBox receiving = new VBox(8, receiveTitle, receiveCards);
        receiving.getStyleClass().add("trade-section");
        VBox body = new VBox(12, header, partnerBar, hint, giving, receiving, summary, error, submit);
        body.getStyleClass().add("trade-body");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(620); scroll.setPrefViewportHeight(650);
        dialog.getDialogPane().setContent(scroll);
        dialog.show();
        return dialog;
    }
    private static List<ResourceType> selected(Map<ResourceType, Spinner<Integer>> values) {
        List<ResourceType> cards = new ArrayList<>();
        values.forEach((resource, spinner) -> cards.addAll(Collections.nCopies(spinner.getValue(), resource)));
        return cards;
    }

    private static ToggleButton partnerButton(String name, int seat, ToggleGroup group) {
        ToggleButton button = new ToggleButton(name);
        button.setToggleGroup(group); button.setUserData(seat);
        button.setId("trade-partner-" + seat);
        button.getStyleClass().add("trade-partner");
        button.setMaxWidth(Double.MAX_VALUE); button.setMinWidth(0);
        button.setTooltip(new Tooltip(name)); HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }

    private static Node resourceCard(ResourceType resource, Spinner<Integer> value, String caption, String prefix) {
        var url = TradeDialog.class.getResource(resource.iconPath());
        Node artwork = text(resource.cardName(), "trade-resource-name");
        if (url != null) {
            ImageView image = new ImageView(new Image(url.toExternalForm()));
            image.setFitWidth(48); image.setFitHeight(44); image.setPreserveRatio(true);
            artwork = image;
        }
        StackPane picture = new StackPane(artwork); picture.setPrefHeight(48);
        picture.getStyleClass().add("trade-picture");
        Label count = text("0", "trade-count"); count.setMinWidth(25); count.setAlignment(Pos.CENTER);
        count.textProperty().bind(value.valueProperty().asString());
        Button minus = new Button("\u2212"), plus = new Button("+");
        minus.setId(prefix + resource.name() + "-minus"); plus.setId(prefix + resource.name() + "-plus");
        minus.getStyleClass().add("trade-step"); plus.getStyleClass().add("trade-step");
        minus.setAccessibleText("Remove " + resource.cardName() + (prefix.equals("give-") ? " from offer" : " from request"));
        plus.setAccessibleText("Add " + resource.cardName() + (prefix.equals("give-") ? " to offer" : " to request"));
        minus.setOnAction(e -> value.decrement()); plus.setOnAction(e -> value.increment());
        SpinnerValueFactory.IntegerSpinnerValueFactory range = (SpinnerValueFactory.IntegerSpinnerValueFactory) value.getValueFactory();
        minus.disableProperty().bind(value.valueProperty().isEqualTo(0));
        plus.disableProperty().bind(value.valueProperty().isEqualTo(range.getMax()));
        HBox controls = new HBox(3, minus, count, plus); controls.setAlignment(Pos.CENTER);
        VBox card = new VBox(4, picture, text(resource.cardName(), "trade-resource-name"), text(caption, "trade-caption"), controls);
        card.setAlignment(Pos.CENTER); card.setPrefWidth(100); card.setMinWidth(98);
        card.getStyleClass().add("trade-resource");
        value.valueProperty().addListener((o, a, b) -> card.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("chosen"), b > 0));
        return card;
    }

    private static Label text(String content, String style) {
        Label label = new Label(content); label.getStyleClass().add(style); label.setWrapText(true);
        return label;
    }

    private static Node ship() {
        Polygon hull = new Polygon(0, 14, 25, 14, 20, 21, 5, 21);
        hull.setFill(Color.web("#d6aa6b"));
        Polygon sail = new Polygon(11, 0, 11, 12, 24, 12);
        sail.setFill(Color.web("#f3cc80"));
        Line mast = new Line(9, 1, 9, 15); mast.setStroke(Color.web("#d6aa6b")); mast.setStrokeWidth(2);
        return new Group(hull, sail, mast);
    }

}
