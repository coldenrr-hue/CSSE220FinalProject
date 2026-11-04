package finalproject.game;

import finalproject.network.GameStateCodec;
import java.util.*;

public final class TradingBuildingTest {
    private static final ResourceType WOOD = ResourceType.FOREST, BRICK = ResourceType.HILLS;
    private static final Board BOARD = BoardGenerator.generate(new Random(42));
    private static GameState state(Pieces p) {
        return new GameState(BOARD, List.of(0,1,2,3), 0, GamePhase.PLAYING, 1, 10, p, new DiceResult(3,3));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); } catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        for (int seed = 0; seed < 100; seed++) {
            Board board = BoardGenerator.generate(new Random(seed));
            Set<Integer> corners = new HashSet<>();
            var harbors = Harbors.of(board);
            check(harbors.size() == 9, "Nine harbors");
            check(harbors.stream().filter(h -> h.resource() == ResourceType.DESERT).count() == 4, "Four general harbors");
            for (var h : harbors) {
                check(corners.add(h.a()) && corners.add(h.b()), "Distinct dock corners");
                check(BoardGraph.of(board).edges().stream().anyMatch(e -> e.tileIds().size() == 1 && e.touches(h.a()) && e.touches(h.b())), "Coastal docks");
            }
        }
        Pieces rich = Pieces.EMPTY.withCards(0, Collections.nCopies(8, WOOD)).withCards(1, Collections.nCopies(3, BRICK));
        GameState s = state(rich);
        check(Harbors.rate(s, 0, WOOD) == 4, "Default rate");
        Pieces traded = PlayRules.bankTrade(s, 0, WOOD, BRICK, 2);
        check(traded.hand(0).equals(List.of(BRICK, BRICK)), "Batched 4:1 trade");
        rejects(() -> PlayRules.bankTrade(s, 1, WOOD, BRICK, 1));
        rejects(() -> PlayRules.bankTrade(s, 0, WOOD, WOOD, 1));
        rejects(() -> PlayRules.bankTrade(s, 0, WOOD, BRICK, 3));
        rejects(() -> PlayRules.bankTrade(state(rich.withCards(2, Collections.nCopies(16, BRICK))), 0, WOOD, BRICK, 1));
        for (var h : Harbors.of(BOARD)) {
            GameState dockState = state(rich.withSettlement(h.a(), 0));
            check(Harbors.rate(dockState, 0, WOOD) == (h.resource() == WOOD ? 2 : h.resource() == ResourceType.DESERT ? 3 : 4), "Owned dock rate");
            check(Harbors.rate(dockState, 1, WOOD) == 4, "Other player cannot use dock");
        }
        TradeOffer offer = new TradeOffer(0, 1, List.of(WOOD, WOOD), List.of(BRICK));
        GameState offered = new GameState(BOARD, s.turnOrder(), 0, s.phase(), 1, 11, rich, s.dice(), offer);
        check(offered.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(offered)))), "Offer round trip");
        Pieces accepted = PlayRules.accept(offered, 1);
        check(accepted.hand(0).size() == 7 && accepted.hand(1).size() == 4, "Both hands exchange atomically");
        check(rich.hand(0).size() == 8, "Original state unchanged");
        rejects(() -> PlayRules.accept(offered, 2));
        rejects(() -> new TradeOffer(0, 1, List.of(WOOD), List.of(WOOD)));
        rejects(() -> new TradeOffer(0, 1, List.of(), List.of(WOOD)));
        GameState poor = new GameState(BOARD, s.turnOrder(), 0, s.phase(), 1, 12, Pieces.EMPTY, s.dice(), offer);
        rejects(() -> PlayRules.accept(poor, 1));
        BoardGraph graph = BoardGraph.of(BOARD);
        int vertex = 0, edge = graph.vertex(vertex).edgeIds().getFirst();
        Pieces builder = Pieces.EMPTY.withSettlement(vertex, 0).withCards(0, PlayRules.cost(PlayRules.Build.ROAD));
        Pieces road = PlayRules.build(state(builder), 0, PlayRules.Build.ROAD, edge);
        check(road.hand(0).isEmpty() && road.roads().get(edge) == 0, "Paid road");
        rejects(() -> PlayRules.build(state(road), 0, PlayRules.Build.ROAD, edge));
        int opposite = graph.edge(edge).other(vertex);
        int extension = graph.vertex(opposite).edgeIds().stream().filter(id -> id != edge).findFirst().orElseThrow();
        Pieces blocked = road.withSettlement(opposite, 1).withCards(0, PlayRules.cost(PlayRules.Build.ROAD));
        check(!PlayRules.canBuild(state(blocked), 0, PlayRules.Build.ROAD, extension), "Opponent blocks road continuation");
        int distant = graph.vertices().stream().filter(v -> v.id() != vertex && !v.neighborIds().contains(vertex)).findFirst().orElseThrow().id();
        int connecting = graph.vertex(distant).edgeIds().getFirst();
        Pieces settler = Pieces.EMPTY.withRoad(connecting, 0).withCards(0, PlayRules.cost(PlayRules.Build.SETTLEMENT));
        Pieces built = PlayRules.build(state(settler), 0, PlayRules.Build.SETTLEMENT, distant);
        check(built.settlements().get(distant) == 0 && built.hand(0).isEmpty() && built.pendingSettlement() == -1, "Paid settlement");
        Pieces neighbor = settler.withSettlement(graph.vertex(distant).neighborIds().getFirst(), 1);
        check(!PlayRules.canBuild(state(neighbor), 0, PlayRules.Build.SETTLEMENT, distant), "Distance rule in normal play");
        Pieces city = PlayRules.build(state(road.withCards(0, PlayRules.cost(PlayRules.Build.CITY))), 0, PlayRules.Build.CITY, vertex);
        check(city.cities().contains(vertex) && city.victoryPoints(0) == 2 && city.hand(0).isEmpty(), "City upgrade and score");
        check(state(city).equals(GameStateCodec.decode(List.of(GameStateCodec.encode(state(city))))), "City round trip");
        Tile tile = BOARD.tiles().stream().filter(t -> t.number() != 0).findFirst().orElseThrow();
        int producingVertex = graph.vertices().stream().filter(v -> v.tileIds().contains(tile.id())).findFirst().orElseThrow().id();
        Pieces producer = Pieces.EMPTY.withSettlement(producingVertex, 0).withCity(producingVertex);
        int first = Math.min(6, tile.number() - 1), second = tile.number() - first;
        GameState production = new GameState(BOARD, s.turnOrder(), 0, s.phase(), 1, 12, producer, new DiceResult(first, second));
        check(PlayRules.produce(production).hand(0).stream().filter(r -> r == tile.resource()).count() >= 2, "Cities produce two");
        GameState unrolled = new GameState(BOARD, s.turnOrder(), 0, s.phase(), 1, 12, rich);
        rejects(() -> PlayRules.bankTrade(unrolled, 0, WOOD, BRICK, 1));
        System.out.println("PASS: docks, bank rates/stock, player offers, atomic exchanges, building costs, cities, production, snapshots");
    }
}
