package finalproject.game;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Full public game snapshot. Revision prevents stale or duplicate turn commands.
 * Everything here is sent to every player, so secret information (like which
 * development cards someone holds) must NOT go in here. See DevelopmentHand.
 */
public record GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                        GamePhase phase, int turnNumber, long revision, Pieces pieces, DiceResult dice,
                        int robberTile, DevelopmentState development) {
    public GameState {
        Objects.requireNonNull(board);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(pieces);
        Objects.requireNonNull(dice);
        Objects.requireNonNull(development);
        turnOrder = List.copyOf(turnOrder);
        if (turnOrder.size() != 4 || !new HashSet<>(turnOrder).equals(Set.of(0, 1, 2, 3))
                || !turnOrder.contains(currentPlayerId) || turnNumber < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid turn state");
        }
        if (board.tiles().stream().noneMatch(t -> t.id() == robberTile)) {
            throw new IllegalArgumentException("The robber must be on a tile");
        }
    }

    /** Robber on the desert, no development cards bought yet. */
    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, Pieces pieces, DiceResult dice) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, pieces, dice,
                desertTile(board), DevelopmentState.START);
    }

    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, Pieces pieces) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, pieces, DiceResult.NONE);
    }

    /** The original constructor, kept so existing code still compiles: starts with no pieces. */
    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, Pieces.EMPTY);
    }

    /** The robber starts on the desert. */
    public static int desertTile(Board board) {
        return board.tiles().stream().filter(t -> t.resource() == ResourceType.DESERT)
                .findFirst().orElseThrow().id();
    }

    /** Same turn, new pieces, next revision. */
    public GameState withPieces(Pieces newPieces) {
        return new GameState(board, turnOrder, currentPlayerId, phase, turnNumber, revision + 1, newPieces, dice,
                robberTile, development);
    }

    /** Same turn, new pieces / robber / development cards, next revision. */
    public GameState with(Pieces newPieces, int newRobberTile, DevelopmentState newDevelopment) {
        return new GameState(board, turnOrder, currentPlayerId, phase, turnNumber, revision + 1, newPieces, dice,
                newRobberTile, newDevelopment);
    }

    /** Same pieces, new turn info (use after TurnManager.advance()), next revision. */
    public GameState withTurn(TurnManager turns) {
        return new GameState(board, turnOrder, turns.currentPlayerId(), turns.phase(),
                turns.turnNumber(), revision + 1, pieces, DiceResult.NONE, robberTile, development.nextTurn());
    }

    /**
     * Victory points everyone can see: buildings plus Largest Army.
     * Victory point cards are secret, so they are added only on the owner's own screen.
     */
    public int victoryPoints(int playerId) {
        return pieces.victoryPoints(playerId) + (development.largestArmy() == playerId ? 2 : 0);
    }
}
