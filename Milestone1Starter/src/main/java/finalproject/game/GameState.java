package finalproject.game;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Full public game snapshot. Revision prevents stale or duplicate turn commands. */
public record GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                        GamePhase phase, int turnNumber, long revision, Pieces pieces) {
    public GameState {
        Objects.requireNonNull(board);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(pieces);
        turnOrder = List.copyOf(turnOrder);
        if (turnOrder.size() != 4 || !new HashSet<>(turnOrder).equals(Set.of(0, 1, 2, 3))
                || !turnOrder.contains(currentPlayerId) || turnNumber < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid turn state");
        }
    }

    /** The original constructor, kept so existing code still compiles: starts with no pieces. */
    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, Pieces.EMPTY);
    }

    /** Same turn, new pieces, next revision. */
    public GameState withPieces(Pieces newPieces) {
        return new GameState(board, turnOrder, currentPlayerId, phase, turnNumber, revision + 1, newPieces);
    }

    /** Same pieces, new turn info (use after TurnManager.advance()), next revision. */
    public GameState withTurn(TurnManager turns) {
        return new GameState(board, turnOrder, turns.currentPlayerId(), turns.phase(),
                turns.turnNumber(), revision + 1, pieces);
    }
}