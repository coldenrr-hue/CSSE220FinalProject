package finalproject.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Everything players have put on the board, plus their resource cards.
 *
 * settlements:        vertex ID -> player ID (includes vertices upgraded to cities)
 * roads:              edge ID   -> player ID
 * pendingSettlement:  during a setup turn, the settlement just placed that still
 *                     needs its road (-1 when the player must place a settlement)
 * hands:              player ID -> resource cards (FOREST = lumber, PASTURE = wool, ...)
 */
public record PlayerAssets(Map<Integer, Integer> settlements, Map<Integer, Integer> roads,
                     int pendingSettlement, Map<Integer, List<ResourceType>> hands, Set<Integer> cities) {

    public static final PlayerAssets EMPTY = new PlayerAssets(Map.of(), Map.of(), -1, Map.of());

    public PlayerAssets(Map<Integer, Integer> settlements, Map<Integer, Integer> roads,
                  int pendingSettlement, Map<Integer, List<ResourceType>> hands) {
        this(settlements, roads, pendingSettlement, hands, Set.of());
    }

    public PlayerAssets {
        cities = Set.copyOf(cities);
        settlements = Map.copyOf(settlements);
        roads = Map.copyOf(roads);
        Map<Integer, List<ResourceType>> copy = new HashMap<>();
        hands.forEach((player, cards) -> copy.put(player, List.copyOf(cards)));
        hands = Map.copyOf(copy);
    }

    public PlayerAssets withSettlement(int vertexId, int playerId) {
        Map<Integer, Integer> updatedSettlements = new HashMap<>(settlements);
        updatedSettlements.put(vertexId, playerId);
        return new PlayerAssets(updatedSettlements, roads, vertexId, hands, cities);
    }

    public PlayerAssets withRoad(int edgeId, int playerId) {
        Map<Integer, Integer> updatedRoads = new HashMap<>(roads);
        updatedRoads.put(edgeId, playerId);
        return new PlayerAssets(settlements, updatedRoads, -1, hands, cities);
    }

    public PlayerAssets withCards(int playerId, List<ResourceType> newCards) {
        List<ResourceType> cards = new ArrayList<>(hand(playerId));
        cards.addAll(newCards);
        return replaceHand(playerId, cards);
    }

    public PlayerAssets replaceHand(int player, List<ResourceType> cards) {
        Map<Integer, List<ResourceType>> updatedHands = new HashMap<>(hands);
        updatedHands.put(player, cards);
        return new PlayerAssets(settlements, roads, pendingSettlement, updatedHands, cities);
    }

    public PlayerAssets withCity(int vertex) {
        var updatedCities = new HashSet<>(cities);
        updatedCities.add(vertex);
        return new PlayerAssets(settlements, roads, -1, hands, updatedCities);
    }

    public List<ResourceType> hand(int playerId) {
        return hands.getOrDefault(playerId, List.of());
    }

    /** Physical settlement pieces still on the board; upgraded cities are excluded. */
    public int settlementCount(int playerId) {
        return (int) settlements.values().stream().filter(owner -> owner == playerId).count() - cityCount(playerId);
    }

    /**
     * A player's victory points, worked out from what they have on the board.
     * One point per settlement and two per city.
     */
    public int victoryPoints(int playerId) {
        return settlementCount(playerId) + 2 * cityCount(playerId);
    }

    public int cityCount(int playerId) {
        return (int) cities.stream().filter(v -> settlements.get(v) == playerId).count();
    }

    public int roadCount(int playerId) {
        return (int) roads.values().stream().filter(p -> p == playerId).count();
    }
}
