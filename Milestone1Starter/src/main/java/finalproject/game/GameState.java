package finalproject.game;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Full public game snapshot. Revision prevents stale or duplicate turn commands. */
public record GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                        GamePhase phase, int turnNumber, long revision, PlayerAssets assets, DiceResult dice, TradeOffer offer) {
    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, PlayerAssets assets, DiceResult dice) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, assets, dice, null);
    }

    public GameState {
        Objects.requireNonNull(board);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(assets);
        Objects.requireNonNull(dice);
        turnOrder = List.copyOf(turnOrder);
        if (turnOrder.size() != 4 || !new HashSet<>(turnOrder).equals(Set.of(0, 1, 2, 3))
                || !turnOrder.contains(currentPlayerId) || turnNumber < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid turn state");
        }
    }

    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, PlayerAssets assets) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, assets, DiceResult.NONE);
    }

    /** Create a new game before any pieces or resource cards have been assigned. */
    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, PlayerAssets.EMPTY);
    }

    /** Same turn, new assets, next revision. */
    public GameState withAssets(PlayerAssets newAssets) {
        return new GameState(board, turnOrder, currentPlayerId, phase, turnNumber, revision + 1, newAssets, dice);
    }

    /** Same assets, new turn info (use after TurnManager.advance()), next revision. */
    public GameState withTurn(TurnManager turns) {
        return new GameState(board, turnOrder, turns.currentPlayerId(), turns.phase(),
                turns.turnNumber(), revision + 1, assets);
    }
}
