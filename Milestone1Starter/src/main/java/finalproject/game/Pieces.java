package finalproject.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything players have put on the board, plus their resource cards.
 *
 * settlements:        vertex ID -> player ID
 * roads:              edge ID   -> player ID
 * pendingSettlement:  during a setup turn, the settlement just placed that still
 *                     needs its road (-1 when the player must place a settlement)
 * hands:              player ID -> resource cards (FOREST = lumber, PASTURE = wool, ...)
 */
public record Pieces(Map<Integer, Integer> settlements, Map<Integer, Integer> roads,
                     int pendingSettlement, Map<Integer, List<ResourceType>> hands) {

    public static final Pieces EMPTY = new Pieces(Map.of(), Map.of(), -1, Map.of());

    public Pieces {
        settlements = Map.copyOf(settlements);
        roads = Map.copyOf(roads);
        Map<Integer, List<ResourceType>> copy = new HashMap<>();
        hands.forEach((player, cards) -> copy.put(player, List.copyOf(cards)));
        hands = Map.copyOf(copy);
    }

    public Pieces withSettlement(int vertexId, int playerId) {
        Map<Integer, Integer> s = new HashMap<>(settlements);
        s.put(vertexId, playerId);
        return new Pieces(s, roads, vertexId, hands);
    }

    public Pieces withRoad(int edgeId, int playerId) {
        Map<Integer, Integer> r = new HashMap<>(roads);
        r.put(edgeId, playerId);
        return new Pieces(settlements, r, -1, hands);
    }

    public Pieces withCards(int playerId, List<ResourceType> newCards) {
        Map<Integer, List<ResourceType>> h = new HashMap<>(hands);
        List<ResourceType> cards = new ArrayList<>(hand(playerId));
        cards.addAll(newCards);
        h.put(playerId, cards);
        return new Pieces(settlements, roads, pendingSettlement, h);
    }

    /** Removes one card for each entry in spent. Throws if the player doesn't have them all. */
    public Pieces withoutCards(int playerId, List<ResourceType> spent) {
        List<ResourceType> cards = new ArrayList<>(hand(playerId));
        for (ResourceType card : spent) {
            if (!cards.remove(card)) throw new IllegalArgumentException("Not enough resource cards.");
        }
        return withHand(playerId, cards);
    }

    /** Replaces a player's whole hand. */
    public Pieces withHand(int playerId, List<ResourceType> cards) {
        Map<Integer, List<ResourceType>> h = new HashMap<>(hands);
        h.put(playerId, cards);
        return new Pieces(settlements, roads, pendingSettlement, h);
    }

    /** True if the player holds at least these cards (duplicates count). */
    public boolean hasCards(int playerId, List<ResourceType> needed) {
        List<ResourceType> cards = new ArrayList<>(hand(playerId));
        for (ResourceType card : needed) if (!cards.remove(card)) return false;
        return true;
    }

    public List<ResourceType> hand(int playerId) {
        return hands.getOrDefault(playerId, List.of());
    }

    public int settlementCount(int playerId) {
        return (int) settlements.values().stream().filter(p -> p == playerId).count();
    }

    /**
     * A player's victory points, worked out from what they have on the board.
     * Right now: 1 point per settlement. Add cities (2 each) and Longest Road
     * here as those features are built. Largest Army is added in
     * GameState.victoryPoints(); VP cards are secret and only shown to their owner.
     * Every laptop gets the same pieces from the host, so every laptop
     * calculates the same score for each player.
     */
    public int victoryPoints(int playerId) {
        return settlementCount(playerId);
    }

    public int roadCount(int playerId) {
        return (int) roads.values().stream().filter(p -> p == playerId).count();
    }
}