package finalproject.game;

import java.util.*;

/** Pure host-side rules; failed actions never mutate hands or board pieces. */
public final class PlayRules {
    private PlayRules() { }
    public enum Build { ROAD, SETTLEMENT, CITY }
    public static List<ResourceType> cost(Build build) {
        return switch (build) {
            case ROAD -> List.of(ResourceType.FOREST, ResourceType.HILLS);
            case SETTLEMENT -> List.of(ResourceType.FOREST, ResourceType.HILLS, ResourceType.FIELDS, ResourceType.PASTURE);
            case CITY -> List.of(ResourceType.MOUNTAINS, ResourceType.MOUNTAINS, ResourceType.MOUNTAINS, ResourceType.FIELDS, ResourceType.FIELDS);
        };
    }
    public static void requireTurn(GameState s, int player) {
        if (s.phase() != GamePhase.PLAYING || s.currentPlayerId() != player || !s.dice().rolled())
            throw new IllegalArgumentException("Wait for your turn and roll before trading or building.");
    }
    public static boolean owns(Pieces pieces, int player, List<ResourceType> cards) {
        List<ResourceType> hand = new ArrayList<>(pieces.hand(player));
        for (ResourceType card : cards) if (!hand.remove(card)) return false;
        return true;
    }
    private static Pieces exchange(Pieces pieces, int player, List<ResourceType> give, List<ResourceType> receive) {
        if (!owns(pieces, player, give)) throw new IllegalArgumentException("Not enough resource cards.");
        List<ResourceType> hand = new ArrayList<>(pieces.hand(player));
        give.forEach(hand::remove);
        hand.addAll(receive);
        return pieces.replaceHand(player, hand);
    }
    public static int bankStock(Pieces pieces, ResourceType type) {
        return 19 - (int) pieces.hands().values().stream().flatMap(List::stream).filter(r -> r == type).count();
    }
    public static Pieces bankTrade(GameState s, int player, ResourceType give, ResourceType receive, int quantity) {
        requireTurn(s, player);
        if (give == receive || give == ResourceType.DESERT || receive == ResourceType.DESERT || quantity < 1 || quantity > 19)
            throw new IllegalArgumentException("Choose different resources and a quantity from 1 to 19.");
        if (bankStock(s.pieces(), receive) < quantity) throw new IllegalArgumentException("The bank does not have enough of that resource.");
        return exchange(s.pieces(), player, Collections.nCopies(Harbors.rate(s, player, give) * quantity, give), Collections.nCopies(quantity, receive));
    }
    public static void validateOffer(GameState s, TradeOffer offer) {
        requireTurn(s, offer.from());
        if (!owns(s.pieces(), offer.from(), offer.give())) throw new IllegalArgumentException("You do not have the offered cards.");
    }
    public static Pieces accept(GameState s, int player) {
        TradeOffer o = s.offer();
        if (o == null || player != o.to()) throw new IllegalArgumentException("This offer is not addressed to you.");
        validateOffer(s, o);
        if (!owns(s.pieces(), player, o.receive())) throw new IllegalArgumentException("You do not have the requested cards.");
        return exchange(exchange(s.pieces(), o.from(), o.give(), o.receive()), player, o.receive(), o.give());
    }
    public static boolean canBuild(GameState s, int player, Build build, int spot) {
        if (s.phase() != GamePhase.PLAYING || player != s.currentPlayerId() || !s.dice().rolled()
                || !owns(s.pieces(), player, cost(build))) return false;
        BoardGraph g = BoardGraph.of(s.board());
        Pieces p = s.pieces();
        long cities = p.cities().stream().filter(v -> p.settlements().get(v) == player).count();
        if (build == Build.CITY) return g.isVertex(spot) && p.settlements().getOrDefault(spot, -1) == player
                && !p.cities().contains(spot) && cities < 4;
        if (build == Build.SETTLEMENT) {
            if (!g.isVertex(spot) || p.settlementCount(player) - cities >= 5 || p.settlements().containsKey(spot)) return false;
            var v = g.vertex(spot);
            return v.neighborIds().stream().noneMatch(p.settlements()::containsKey)
                    && v.edgeIds().stream().anyMatch(e -> p.roads().getOrDefault(e, -1) == player);
        }
        if (!g.isEdge(spot) || p.roadCount(player) >= 15 || p.roads().containsKey(spot)) return false;
        var e = g.edge(spot);
        for (int v : List.of(e.a(), e.b())) {
            if (p.settlements().containsKey(v)) {
                if (p.settlements().get(v) == player) return true;
            } else if (g.vertex(v).edgeIds().stream().anyMatch(id -> p.roads().getOrDefault(id, -1) == player)) return true;
        }
        return false;
    }
    public static List<Integer> legalBuilds(GameState s, int player, Build build) {
        int size = build == Build.ROAD ? BoardGraph.of(s.board()).edges().size() : BoardGraph.of(s.board()).vertices().size();
        return java.util.stream.IntStream.range(0, size).filter(i -> canBuild(s, player, build, i)).boxed().toList();
    }
    public static Pieces build(GameState s, int player, Build build, int spot) {
        if (!canBuild(s, player, build, spot)) throw new IllegalArgumentException("Cannot build there: check your cards, connections and remaining pieces.");
        Pieces paid = exchange(s.pieces(), player, cost(build), List.of());
        return switch (build) {
            case ROAD -> paid.withRoad(spot, player);
            case SETTLEMENT -> new Pieces(paid.withSettlement(spot, player).settlements(), paid.roads(), -1, paid.hands(), paid.cities());
            case CITY -> paid.withCity(spot);
        };
    }
    public static Pieces produce(GameState s) {
        if (s.dice().total() == 7) return s.pieces();
        Pieces result = s.pieces();
        BoardGraph g = BoardGraph.of(s.board());
        for (ResourceType resource : ResourceType.values()) {
            if (resource == ResourceType.DESERT) continue;
            Map<Integer, Integer> due = new HashMap<>();
            s.pieces().settlements().forEach((vertex, player) -> {
                for (int tile : g.vertex(vertex).tileIds()) {
                    if (g.tile(tile).resource() == resource && g.tile(tile).number() == s.dice().total())
                        due.merge(player, s.pieces().cities().contains(vertex) ? 2 : 1, Integer::sum);
                }
            });
            int stock = bankStock(result, resource);
            int total = due.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= stock || due.size() == 1) {
                for (var entry : due.entrySet()) result = result.withCards(entry.getKey(), Collections.nCopies(Math.min(stock, entry.getValue()), resource));
            }
        }
        return result;
    }
}
