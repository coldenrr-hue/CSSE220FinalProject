package finalproject.game;

/**
 * The five kinds of Catan development card, with how many of each are in the
 * 25-card deck (14 knights, 5 victory points, 2 of each progress card).
 */
public enum DevelopmentCard {
    KNIGHT("Knight", 14, "Move the robber to any other tile, then steal 1 random resource card from a "
            + "player with a settlement on that tile. The first player to play 3 knights gets Largest Army "
            + "(+2 victory points)."),
    VICTORY_POINT("Victory Point", 5, "Worth 1 victory point. It stays secret: only you can see it. "
            + "You never play this card; it counts toward your score automatically."),
    ROAD_BUILDING("Road Building", 2, "Build 2 roads for free. Each one has to connect to one of your "
            + "roads or settlements."),
    YEAR_OF_PLENTY("Year of Plenty", 2, "Take any 2 resource cards from the bank. They can be the same "
            + "resource or two different ones."),
    MONOPOLY("Monopoly", 2, "Choose a resource. Every other player has to give you all of their cards "
            + "of that resource.");

    public static final int DECK_SIZE = 25;

    private final String label;
    private final int copies;
    private final String description;

    DevelopmentCard(String label, int copies, String description) {
        this.label = label;
        this.copies = copies;
        this.description = description;
    }

    public String label() { return label; }
    public int copies() { return copies; }
    public String description() { return description; }
}
