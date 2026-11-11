package finalproject.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks a rolled seven from the initial discard requirements through the robber move.
 * The roll revision lets players submit discards independently against the same roll.
 */
public record SevenResolution(long rollRevision, Map<Integer, Integer> pendingDiscards) {
    private static final int DISCARD_THRESHOLD = 7;
    public static final SevenResolution NONE = new SevenResolution(-1, Map.of());

    public SevenResolution {
        pendingDiscards = Map.copyOf(pendingDiscards);
    }

    public static SevenResolution start(GameState state) {
        if (state.dice().total() != 7) {
            throw new IllegalArgumentException("A seven must be rolled to start discarding.");
        }
        Map<Integer, Integer> pending = new HashMap<>();
        for (int player : state.turnOrder()) {
            int handSize = state.assets().hand(player).size();
            if (handSize >= DISCARD_THRESHOLD) pending.put(player, handSize / 2);
        }
        return new SevenResolution(state.revision() + 1, pending);
    }

    public boolean active() {
        return rollRevision >= 0;
    }

    public boolean readyToMoveRobber() {
        return active() && pendingDiscards.isEmpty();
    }

    public int discardCount(int playerId) {
        return pendingDiscards.getOrDefault(playerId, 0);
    }

    public GameState discard(GameState state, int playerId, List<ResourceType> cards) {
        int required = discardCount(playerId);
        if (required == 0) {
            throw new IllegalArgumentException("You have no cards to discard for this roll.");
        }
        if (cards.size() != required) {
            throw new IllegalArgumentException("Discard exactly " + required + " resource cards.");
        }
        PlayerAssets assets = GameRules.exchange(state.assets(), playerId, cards, List.of());
        Map<Integer, Integer> remaining = new HashMap<>(pendingDiscards);
        remaining.remove(playerId);
        return state.with(assets, state.robberTile(), state.development(),
                new SevenResolution(rollRevision, remaining));
    }
}
