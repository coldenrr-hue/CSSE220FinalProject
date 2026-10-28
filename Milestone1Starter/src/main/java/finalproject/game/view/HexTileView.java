package finalproject.game.view;

import finalproject.game.Tile;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** Tile artwork with a number token; terrain names are available on hover. */
public final class HexTileView extends Pane {
    public static final double RADIUS = 51;
    public static final double WIDTH = Math.sqrt(3) * RADIUS;
    public static final double HEIGHT = RADIUS * 2;
    private static final Map<String, Image> IMAGES = new HashMap<>();

    public HexTileView(Tile tile) {
        setPrefSize(WIDTH, HEIGHT);
        Polygon background = hex();
        background.setFill(Color.web("#e7e3da"));
        getChildren().add(background);
        if (tile != null) {
            Image image = artwork(tile.resource().imagePath());
            if (image != null) {
                ImageView art = new ImageView(image);
                art.setFitWidth(WIDTH);
                art.setFitHeight(HEIGHT);
                art.setClip(hex());
                getChildren().add(art);
            }
            if (tile.number() != 0) {
                Circle token = new Circle(WIDTH / 2, 64, 17, Color.web("#fbf5e6"));
                token.setStroke(Color.web("#b8afa0"));
                Label number = label(Integer.toString(tile.number()), 20, tile.hasRedNumber() ? "#b33f38" : "#263b47");
                number.setLayoutY(49);
                getChildren().addAll(token, number);
            }
            Tooltip.install(this, new Tooltip("Tile " + tile.id() + " • " + tile.resource().label()
                    + " • axial (" + tile.q() + ", " + tile.r() + ")"));
            setUserData(tile);
        }
        Polygon border = hex();
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.web("#97a9b1"));
        border.setStrokeWidth(2);
        border.setMouseTransparent(true);
        getChildren().add(border);
    }

    private static Label label(String text, int size, String color) {
        Label label = new Label(text);
        label.setPrefWidth(WIDTH);
        label.setAlignment(javafx.geometry.Pos.CENTER);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: " + size + "px;");
        label.setMouseTransparent(true);
        return label;
    }

    private static Image artwork(String path) {
        URL url = HexTileView.class.getResource(path);
        if (url == null) return null;
        return IMAGES.computeIfAbsent(path, key -> {
            Image image = new Image(url.toExternalForm());
            return image.isError() ? null : image;
        });
    }

    private static Polygon hex() {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            polygon.getPoints().addAll(WIDTH / 2 + RADIUS * Math.cos(angle), HEIGHT / 2 + RADIUS * Math.sin(angle));
        }
        return polygon;
    }
}
