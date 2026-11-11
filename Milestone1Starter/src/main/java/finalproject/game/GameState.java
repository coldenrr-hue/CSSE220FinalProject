package finalproject.game;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * An immutable match snapshot shared by all clients. It contains the board, turn, pieces,
 * resource hands, dice, active trade, robber, pending discards, and public development-card
 * state. The revision identifies the state a command was based on.
 */
public record GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                        GamePhase phase, int turnNumber, long revision, PlayerAssets assets, DiceResult dice, TradeOffer offer,
                        int robberTile, DevelopmentState development, SevenResolution seven) {
    public GameState {
        Objects.requireNonNull(board);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(assets);
        Objects.requireNonNull(dice);
        Objects.requireNonNull(development);
        Objects.requireNonNull(seven);
        turnOrder = List.copyOf(turnOrder);
        if (turnOrder.size() != 4 || !new HashSet<>(turnOrder).equals(Set.of(0, 1, 2, 3))
                || !turnOrder.contains(currentPlayerId) || turnNumber < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid turn state");
        }
        if (board.tiles().stream().noneMatch(t -> t.id() == robberTile)) {
            throw new IllegalArgumentException("The robber must be on a tile");
        }
    }

    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, PlayerAssets assets, DiceResult dice,
                     TradeOffer offer, int robberTile, DevelopmentState development) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, assets, dice, offer,
                robberTile, development, SevenResolution.NONE);
    }

    /** Robber on the desert, no development cards bought yet. */
    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, PlayerAssets assets, DiceResult dice, TradeOffer offer) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, assets, dice, offer,
                desertTile(board), DevelopmentState.START);
    }

    public GameState(Board board, List<Integer> turnOrder, int currentPlayerId,
                     GamePhase phase, int turnNumber, long revision, PlayerAssets assets, DiceResult dice) {
        this(board, turnOrder, currentPlayerId, phase, turnNumber, revision, assets, dice, null);
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

    /** The robber starts on the desert. */
    public static int desertTile(Board board) {
        return board.tiles().stream().filter(t -> t.resource() == ResourceType.DESERT)
                .findFirst().orElseThrow().id();
    }

    /** Same turn, new assets, next revision. */
    public GameState withAssets(PlayerAssets newAssets) {
        return with(newAssets, robberTile, development);
    }

    /** Same turn, new assets, robber position, and development state, next revision. */
    public GameState with(PlayerAssets newAssets, int newRobberTile, DevelopmentState newDevelopment) {
        return with(newAssets, newRobberTile, newDevelopment, seven);
    }

    GameState with(PlayerAssets newAssets, int newRobberTile, DevelopmentState newDevelopment,
                   SevenResolution newSeven) {
        return new GameState(board, turnOrder, currentPlayerId, phase, turnNumber, revision + 1, newAssets, dice, offer,
                newRobberTile, newDevelopment, newSeven);
    }

    /** Same assets, new turn info (use after TurnManager.advance()), next revision. */
    public GameState withTurn(TurnManager turns) {
        return new GameState(board, turnOrder, turns.currentPlayerId(), turns.phase(),
                turns.turnNumber(), revision + 1, assets, DiceResult.NONE, null, robberTile, development.nextTurn());
    }

    /**
     * Victory points everyone can see: buildings plus Largest Army.
     * Victory point cards are secret, so they are added only on the owner's own screen.
     */
    public int victoryPoints(int playerId) {
        return assets.victoryPoints(playerId) + (development.largestArmy() == playerId ? 2 : 0);
    }
}
