package finalproject.game;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Full public game snapshot. Revision prevents stale or duplicate turn commands. */
public record GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                        GamePhase phase, int turnNumber, long revision) {
    public GameState {
        Objects.requireNonNull(board);
        Objects.requireNonNull(phase);
        turnOrder = List.copyOf(turnOrder);
        if (turnOrder.size() != 4 || !new HashSet<>(turnOrder).equals(Set.of(0, 1, 2, 3))
                || !turnOrder.contains(currentPlayerId) || turnNumber < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid turn state");
        }
    }
}
