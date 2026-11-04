package finalproject.game;

import java.util.Objects;

/** Axial coordinates identify a hex independently of its screen position. */
public record Tile(int id, int q, int r, ResourceType resource, int number) {
    public Tile {
        Objects.requireNonNull(resource);
        if (id < 0 || id >= 19 || Math.max(Math.max(Math.abs(q), Math.abs(r)), Math.abs(q + r)) > 2) {
            throw new IllegalArgumentException("Invalid tile position");
        }
        if (resource == ResourceType.DESERT ? number != 0 : number < 2 || number > 12 || number == 7) {
            throw new IllegalArgumentException("Invalid number token");
        }
    }

    public boolean isAdjacentTo(Tile other) {
        int dq = q - other.q, dr = r - other.r;
        return Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(dq + dr)) == 1;
    }

    public boolean hasRedNumber() { return number == 6 || number == 8; }
}
