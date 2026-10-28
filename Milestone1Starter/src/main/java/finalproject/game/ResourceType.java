package finalproject.game;

/** Artwork is optional. Add PNG files under src/main/resources/tiles/. */
public enum ResourceType {
    FOREST("Forest"), PASTURE("Pasture"), FIELDS("Fields"),
    HILLS("Hills"), MOUNTAINS("Mountains"), DESERT("Desert");

    private final String label;
    ResourceType(String label) { this.label = label; }
    public String label() { return label; }
    public String imagePath() { return "/tiles/" + name().toLowerCase(java.util.Locale.ROOT) + ".png"; }
}
