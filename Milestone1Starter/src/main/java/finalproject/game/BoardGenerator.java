package finalproject.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Run once on the host. Clients receive the resulting tiles, never reroll them. */
public final class BoardGenerator {
    private BoardGenerator() { }

    public static Board generate(Random random) {
        List<ResourceType> resources = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            int count = switch (type) {
                case FOREST, PASTURE, FIELDS -> 4;
                case HILLS, MOUNTAINS -> 3;
                case DESERT -> 1;
            };
            resources.addAll(Collections.nCopies(count, type));
        }
        Collections.shuffle(resources, random);
        List<Integer> numbers = new ArrayList<>(List.of(2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12));
        // Rejection sampling preserves the token counts while separating 6/8 tokens.
        for (int attempt = 0; attempt < 10000; attempt++) {
            Collections.shuffle(numbers, random);
            List<Tile> tiles = new ArrayList<>();
            int id = 0, token = 0;
            for (int r = -2; r <= 2; r++) {
                for (int q = Math.max(-2, -r - 2); q <= Math.min(2, -r + 2); q++) {
                    ResourceType type = resources.get(id);
                    tiles.add(new Tile(id++, q, r, type, type == ResourceType.DESERT ? 0 : numbers.get(token++)));
                }
            }
            boolean adjacentRed = tiles.stream().anyMatch(a -> a.hasRedNumber()
                    && tiles.stream().anyMatch(b -> b.hasRedNumber() && a.isAdjacentTo(b)));
            if (!adjacentRed) return new Board(tiles);
        }
        throw new IllegalStateException("Could not generate separated number tokens");
    }
}
