package finalproject.game.view;

import finalproject.game.GameState;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.util.Objects;

/** Displays the illustrated two-point awards held by a player in the game or final standings. */
public final class AwardCards {
    private static final Image LONGEST_ROAD = load("longest-road");
    private static final Image LARGEST_ARMY = load("largest-army");

    private AwardCards() { }

    public static HBox forPlayer(GameState game, int playerId) {
        HBox cards = new HBox(6);
        if (game.longestRoadHolder() == playerId) {
            cards.getChildren().add(card(LONGEST_ROAD, "Longest Road", "longest-road-award"));
        }
        if (game.development().largestArmy() == playerId) {
            cards.getChildren().add(card(LARGEST_ARMY, "Largest Army", "largest-army-award"));
        }
        return cards;
    }

    private static ImageView card(Image image, String title, String id) {
        ImageView view = new ImageView(image);
        view.setId(id);
        view.setFitWidth(72);
        view.setPreserveRatio(true);
        String description = title + " (+2 victory points)";
        view.setAccessibleText(description);
        Tooltip.install(view, new Tooltip(description));
        return view;
    }

    private static Image load(String name) {
        return new Image(Objects.requireNonNull(AwardCards.class.getResource("/awards/" + name + ".png"))
                .toExternalForm());
    }
}
