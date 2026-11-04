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
                     int pendingSettlement, Map<Integer, List<ResourceType>> hands, java.util.Set<Integer> cities) {

    public static final Pieces EMPTY = new Pieces(Map.of(), Map.of(), -1, Map.of());

    public Pieces(Map<Integer, Integer> settlements, Map<Integer, Integer> roads,
                  int pendingSettlement, Map<Integer, List<ResourceType>> hands) {
        this(settlements, roads, pendingSettlement, hands, java.util.Set.of());
    }

    public Pieces {
        cities = java.util.Set.copyOf(cities);
        settlements = Map.copyOf(settlements);
        roads = Map.copyOf(roads);
        Map<Integer, List<ResourceType>> copy = new HashMap<>();
        hands.forEach((player, cards) -> copy.put(player, List.copyOf(cards)));
        hands = Map.copyOf(copy);
    }

    public Pieces withSettlement(int vertexId, int playerId) {
        Map<Integer, Integer> s = new HashMap<>(settlements);
        s.put(vertexId, playerId);
        return new Pieces(s, roads, vertexId, hands, cities);
    }

    public Pieces withRoad(int edgeId, int playerId) {
        Map<Integer, Integer> r = new HashMap<>(roads);
        r.put(edgeId, playerId);
        return new Pieces(settlements, r, -1, hands, cities);
    }

    public Pieces withCards(int playerId, List<ResourceType> newCards) {
        Map<Integer, List<ResourceType>> h = new HashMap<>(hands);
        List<ResourceType> cards = new ArrayList<>(hand(playerId));
        cards.addAll(newCards);
        h.put(playerId, cards);
        return new Pieces(settlements, roads, pendingSettlement, h, cities);
    }

    public Pieces replaceHand(int player, List<ResourceType> cards) {
        Map<Integer, List<ResourceType>> h = new HashMap<>(hands);
        h.put(player, cards);
        return new Pieces(settlements, roads, pendingSettlement, h, cities);
    }

    public Pieces withCity(int vertex) {
        var c = new java.util.HashSet<>(cities);
        c.add(vertex);
        return new Pieces(settlements, roads, -1, hands, c);
    }

    public List<ResourceType> hand(int playerId) {
        return hands.getOrDefault(playerId, List.of());
    }

    public int settlementCount(int playerId) {
        return (int) settlements.values().stream().filter(p -> p == playerId).count();
    }

    /**
     * A player's victory points, worked out from what they have on the board.
     * One point per settlement and two per city. Longest Road, Largest Army
     * and VP cards will be included when those features are built.
     * Every laptop gets the same pieces from the host, so every laptop
     * calculates the same score for each player.
     */
    public int victoryPoints(int playerId) {
        return settlementCount(playerId) + (int) cities.stream().filter(v -> settlements.get(v) == playerId).count();
    }

    public int cityCount(int playerId) {
        return (int) cities.stream().filter(v -> settlements.get(v) == playerId).count();
    }

    public int roadCount(int playerId) {
        return (int) roads.values().stream().filter(p -> p == playerId).count();
    }
}