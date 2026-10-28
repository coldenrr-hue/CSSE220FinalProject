package finalproject.game.view;

import finalproject.game.Board;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/** Coordinates and scaling only. Each individual tile owns its own rendering. */
public class HexBoardView extends StackPane {
    private final Pane surface = new Pane();

    public HexBoardView() {
        surface.setPrefSize(640, 500);
        surface.setMinSize(640, 500);
        surface.setMaxSize(640, 500);
        Group scaled = new Group(surface);
        getChildren().add(scaled);
        setMinSize(200, 180);
        setStyle("-fx-background-color: #214e63; -fx-background-radius: 18;");
        Runnable resize = () -> {
            double scale = Math.min(getWidth() / 640, getHeight() / 500);
            scaled.setScaleX(scale);
            scaled.setScaleY(scale);
        };
        widthProperty().addListener((o, a, b) -> resize.run());
        heightProperty().addListener((o, a, b) -> resize.run());
        showWaiting();
    }

    public void render(Board board) {
        surface.getChildren().clear();
        board.tiles().forEach(tile -> add(new HexTileView(tile), tile.q(), tile.r()));
    }

    public void showWaiting() {
        surface.getChildren().clear();
        for (int r = -2; r <= 2; r++) {
            for (int q = Math.max(-2, -r - 2); q <= Math.min(2, -r + 2); q++) add(new HexTileView(null), q, r);
        }
    }

    private void add(HexTileView tile, int q, int r) {
        double radius = HexTileView.RADIUS + 2;
        tile.setLayoutX(320 + Math.sqrt(3) * radius * (q + r / 2.0) - HexTileView.WIDTH / 2);
        tile.setLayoutY(250 + radius * 1.5 * r - HexTileView.HEIGHT / 2);
        surface.getChildren().add(tile);
    }
}
