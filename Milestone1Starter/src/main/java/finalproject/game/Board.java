package finalproject.game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores the island's immutable terrain and number tokens so every client renders the same
 * layout.
 */
public record Board(List<Tile> tiles) {
    public Board {
        tiles = List.copyOf(tiles);
        if (tiles.size() != 19) throw new IllegalArgumentException("The board needs 19 tiles");
        Set<Integer> ids = new HashSet<>();
        Set<String> positions = new HashSet<>();
        for (Tile tile : tiles) {
            if (!ids.add(tile.id()) || !positions.add(tile.q() + "," + tile.r())) {
                throw new IllegalArgumentException("Duplicate tile ID or position");
            }
        }
    }
}
