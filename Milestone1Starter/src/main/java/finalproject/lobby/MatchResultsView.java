package finalproject.lobby;

import finalproject.game.GameState;
import finalproject.game.MatchResult;
import finalproject.game.view.AwardCards;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.Map;
import java.util.Objects;

/** Full-screen standings shown to every player when the host declares a winner. */
final class MatchResultsView extends ScrollPane {
    private static final String[] SEAT_COLORS = {"#e37663", "#72b4e2", "#e6bb59", "#a4c395"};
    private final Label status = label("", "results-note");

    MatchResultsView(MatchResult result, GameState game, int localPlayer, Map<Integer, String> names,
                     Runnable newGame, Runnable leave) {
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/results.css")).toExternalForm());
        getStyleClass().add("match-results");
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        String winnerName = names.getOrDefault(result.winnerId(), "Player " + (result.winnerId() + 1));
        Label winner = label(winnerName + " wins!", "results-winner");
        winner.setWrapText(true);
        winner.setAlignment(Pos.CENTER);
        SVGPath trophy = new SVGPath();
        trophy.setContent("M11 4 H37 V9 H46 V15 Q46 25 35 26 Q32 32 27 33 V39 H36 V44 H12 V39 H21 V33 Q16 32 13 26 Q2 25 2 15 V9 H11 Z "
                + "M11 14 H7 V15 Q7 20 11 21 Z M37 14 V21 Q41 20 41 15 V14 Z");
        trophy.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        trophy.getStyleClass().add("results-trophy");
        VBox hero = new VBox(5, trophy, label("MATCH COMPLETE", "results-kicker"), winner,
                label(result.winner().points() + " victory points", "results-score"));
        hero.setAlignment(Pos.CENTER);
        hero.getStyleClass().add("results-hero");

        int roads = result.players().stream().mapToInt(MatchResult.PlayerStats::roads).sum();
        int cards = result.players().stream().mapToInt(MatchResult.PlayerStats::cardsPlayed).sum();
        HBox highlights = new HBox(12, highlight(result.turns(), "TURNS"), highlight(roads, "ROADS BUILT"),
                highlight(cards, "DEVELOPMENT CARDS PLAYED"));

        Label hint = label(localPlayer == 0
                ? "Start a new game to return the group to the ready lobby."
                : "Waiting for the host to start a new game.", "results-note");
        hint.setWrapText(true);
        Button leaveButton = new Button("Leave table");
        leaveButton.getStyleClass().add("results-secondary");
        leaveButton.setOnAction(e -> leave.run());
        HBox buttons = new HBox(10, leaveButton);
        buttons.setAlignment(Pos.CENTER);
        if (localPlayer == 0) {
            Button next = new Button("Start a new game");
            next.getStyleClass().add("results-primary");
            next.setOnAction(e -> newGame.run());
            buttons.getChildren().addFirst(next);
        }
        status.setWrapText(true);
        status.visibleProperty().bind(status.textProperty().isNotEmpty());
        status.managedProperty().bind(status.visibleProperty());

        VBox panel = new VBox(12, hero, highlights, standings(result, game, localPlayer, names), hint, buttons, status);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(880);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane frame = new StackPane(panel);
        frame.setPadding(new Insets(20, 24, 20, 24));
        frame.getStyleClass().add("results-frame");
        setContent(frame);
    }

    void showStatus(String value) { status.setText(value); }

    private static VBox highlight(int value, String caption) {
        VBox tile = new VBox(2, label(Integer.toString(value), "results-metric"), label(caption, "results-caption"));
        tile.setAlignment(Pos.CENTER);
        tile.setPrefWidth(0);
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.getStyleClass().add("results-highlight");
        return tile;
    }

    private static GridPane standings(MatchResult result, GameState game, int localPlayer, Map<Integer, String> names) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("results-table");
        String[] headings = {"PLAYER", "POINTS", "DEV PLAYED", "KNIGHTS", "SETTLEMENTS", "CITIES", "ROADS"};
        for (int col = 0; col < headings.length; col++) {
            ColumnConstraints width = new ColumnConstraints();
            width.setPercentWidth(col == 0 ? 28 : 12);
            grid.getColumnConstraints().add(width);
            addCell(grid, headings[col], "results-column", col, 0);
        }
        int row = 1;
        for (MatchResult.PlayerStats p : result.players()) {
            if (p.id() == result.winnerId()) {
                Region band = new Region();
                band.getStyleClass().add("results-winning-row");
                grid.add(band, 0, row, headings.length, 1);
            }
            String name = names.getOrDefault(p.id(), "Player " + (p.id() + 1));
            Label player = label(name + (p.id() == localPlayer ? " · You" : ""), "results-player");
            VBox playerCell = new VBox(4, player);
            playerCell.setAlignment(Pos.CENTER_LEFT);
            HBox awards = AwardCards.forPlayer(game, p.id());
            if (!awards.getChildren().isEmpty()) playerCell.getChildren().add(awards);
            grid.add(playerCell, 0, row);
            player.setStyle("-fx-text-fill: " + SEAT_COLORS[p.id()] + ";");
            int[] values = {p.points(), p.cardsPlayed(), p.knights(), p.settlements(), p.cities(), p.roads()};
            for (int col = 0; col < values.length; col++) {
                addCell(grid, Integer.toString(values[col]), col == 0 ? "results-points" : "results-cell", col + 1, row);
            }
            row++;
        }
        return grid;
    }

    private static Label addCell(GridPane grid, String text, String style, int column, int row) {
        Label cell = label(text, style);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setAlignment(column == 0 ? Pos.CENTER_LEFT : Pos.CENTER);
        grid.add(cell, column, row);
        return cell;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }
}
