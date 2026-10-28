package finalproject.lobby;

import finalproject.game.ResourceType;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import java.net.URL;

/** Decorative lobby illustration using the tile artwork, independent of any match. */
final class LobbyArtwork extends VBox {
    LobbyArtwork() {
        super(16);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: #1b3d4c; -fx-background-radius: 18; -fx-padding: 24;");
        Label title = LobbyStyles.text("Gather around the island.");
        title.setStyle("-fx-text-fill: #f3cc80; -fx-font-size: 26px; -fx-font-weight: bold;");
        title.setAlignment(Pos.CENTER);
        Label subtitle = LobbyStyles.text("Build your table. Bring your friends. Your island awaits.");
        subtitle.setAlignment(Pos.CENTER);
        Group tiles = new Group();
        ResourceType[] resources = {ResourceType.MOUNTAINS, ResourceType.FIELDS, ResourceType.FOREST,
                ResourceType.HILLS, ResourceType.DESERT, ResourceType.PASTURE, ResourceType.FOREST};
        double[][] positions = {{-75,-125}, {75,-125}, {-150,5}, {0,5}, {150,5}, {-75,135}, {75,135}};
        for (int i = 0; i < resources.length; i++) {
            StackPane tile = illustrationTile(resources[i]);
            tile.setLayoutX(positions[i][0]);
            tile.setLayoutY(positions[i][1]);
            tiles.getChildren().add(tile);
        }
        StackPane art = new StackPane(tiles);
        art.setMinSize(0, 0);
        art.setPrefHeight(420);
        VBox.setVgrow(art, javafx.scene.layout.Priority.ALWAYS);
        Runnable scale = () -> {
            double size = Math.max(0.1, Math.min(art.getWidth() / 460, art.getHeight() / 450));
            tiles.setScaleX(size);
            tiles.setScaleY(size);
        };
        art.widthProperty().addListener((o,a,b) -> scale.run());
        art.heightProperty().addListener((o,a,b) -> scale.run());
        Label caption = LobbyStyles.text("HOST A TABLE  •  JOIN YOUR FRIENDS  •  MARK READY");
        caption.setAlignment(Pos.CENTER);
        getChildren().addAll(title, subtitle, art, caption);
    }

    private StackPane illustrationTile(ResourceType type) {
        StackPane tile = new StackPane();
        tile.setPrefSize(147, 170);
        URL url = getClass().getResource(type.imagePath());
        Polygon hex = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            hex.getPoints().addAll(73.5 + 85 * Math.cos(angle), 85 + 85 * Math.sin(angle));
        }
        hex.setFill(Color.web("#e7ddbd"));
        tile.getChildren().add(hex);
        if (url != null) {
            ImageView image = new ImageView(new Image(url.toExternalForm()));
            image.setFitWidth(147);
            image.setFitHeight(170);
            tile.getChildren().add(image);
        }
        tile.setClip(new Polygon(hex.getPoints().stream().mapToDouble(Double::doubleValue).toArray()));
        return tile;
    }
}
