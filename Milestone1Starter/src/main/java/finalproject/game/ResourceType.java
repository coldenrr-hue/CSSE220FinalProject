package finalproject.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Terrain identifiers stay stable on the network; each terrain names its resource card. */
public enum ResourceType {
    FOREST("Forest", "Lumber"), PASTURE("Pasture", "Wool"), FIELDS("Fields", "Grain"),
    HILLS("Hills", "Brick"), MOUNTAINS("Mountains", "Ore"), DESERT("Desert", "Any");

    private final String label;
    private final String cardName;

    ResourceType(String label, String cardName) {
        this.label = label;
        this.cardName = cardName;
    }

    public String label() { return label; }
    public String cardName() { return cardName; }
    public String imagePath() { return "/tiles/" + name().toLowerCase(Locale.ROOT) + ".png"; }
    public String iconPath() { return "/icons/" + cardName.toLowerCase(Locale.ROOT) + ".png"; }

    /** Human-readable trade summary, in the same resource order everywhere. */
    public static String describeCards(List<ResourceType> cards) {
        List<String> parts = new ArrayList<>();
        for (ResourceType resource : values()) {
            long count = cards.stream().filter(card -> card == resource).count();
            if (count > 0) parts.add(count + " " + resource.cardName());
        }
        return String.join(", ", parts);
    }
}
