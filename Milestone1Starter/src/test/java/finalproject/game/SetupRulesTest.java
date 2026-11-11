package finalproject.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** The setup ("founding") phase: settlement/road placement rules and starting resources. */
class SetupRulesTest {

    private static GameState newGame(int seed) {
        Board board = BoardGenerator.generate(new Random(seed));
        TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(seed));
        return new GameState(board, turns.order(), turns.currentPlayerId(), turns.phase(), 0, 1);
    }

    @Test
    void boardGraphHasStandardTopology() {
        BoardGraph graph = BoardGraph.of(BoardGenerator.generate(new Random(0)));
        assertEquals(54, graph.vertices().size());
        assertEquals(72, graph.edges().size());
        for (BoardGraph.Vertex v : graph.vertices()) {
            assertTrue(v.tileIds().size() >= 1 && v.tileIds().size() <= 3, "vertex " + v.id() + " tiles");
            assertTrue(v.neighborIds().size() == 2 || v.neighborIds().size() == 3, "vertex " + v.id() + " neighbors");
            assertEquals(v.neighborIds().size(), v.edgeIds().size());
        }
        for (BoardGraph.Edge e : graph.edges()) {
            assertTrue(graph.vertex(e.a()).neighborIds().contains(e.b()), "edge " + e.id() + " endpoints adjacent");
        }
    }

    @Test
    void completeSetupForManyBoards() {
        for (int seed = 0; seed < 250; seed++) {
            GameState state = newGame(seed);
            Board board = state.board();
            BoardGraph graph = BoardGraph.of(board);
            TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(seed));
            Map<Integer, List<ResourceType>> expectedHands = new HashMap<>();
            for (int step = 0; step < 8; step++) {
                int player = state.currentPlayerId();
                String at = "seed " + seed + " step " + step;
                assertTrue(SetupRules.needsSettlement(state), at);
                assertTrue(SetupRules.legalRoads(state, player).isEmpty(), "road before settlement, " + at);

                int vertex = SetupRules.legalSettlements(state, player).getFirst();
                if (step >= 4) expectedHands.put(player, SetupRules.startingResources(board, vertex));
                state = SetupRules.placeSettlement(state, player, vertex);
                assertEquals(vertex, state.pieces().pendingSettlement(), at);
                assertTrue(SetupRules.needsRoad(state), at);
                assertTrue(SetupRules.legalSettlements(state, player).isEmpty(), "two settlements in one turn, " + at);
                if (step < 4) assertTrue(state.pieces().hand(player).isEmpty(), "first settlement gives no cards, " + at);

                List<Integer> roads = SetupRules.legalRoads(state, player);
                assertFalse(roads.isEmpty(), at);
                for (int road : roads) assertTrue(graph.edge(road).touches(vertex), "road must touch new settlement, " + at);
                state = SetupRules.placeRoad(state, player, roads.getFirst());
                assertEquals(-1, state.pieces().pendingSettlement(), at);

                turns.advance();
                state = state.withTurn(turns);
                for (int occupied : state.pieces().settlements().keySet()) {
                    for (int neighbor : graph.vertex(occupied).neighborIds()) {
                        assertFalse(state.pieces().settlements().containsKey(neighbor), "distance rule, " + at);
                    }
                }
            }
            assertEquals(GamePhase.PLAYING, state.phase(), "seed " + seed);
            assertFalse(SetupRules.isSetup(state));
            for (int player = 0; player < 4; player++) {
                assertEquals(2, state.pieces().settlementCount(player));
                assertEquals(2, state.pieces().roadCount(player));
                assertEquals(2, state.victoryPoints(player));
                assertEquals(expectedHands.get(player), state.pieces().hand(player), "starting resources");
            }
        }
    }

    @Test
    void onlyTheCurrentPlayerMayPlace() {
        GameState state = newGame(4);
        int me = state.currentPlayerId();
        int other = state.turnOrder().get(1);
        int vertex = SetupRules.legalSettlements(state, me).getFirst();
        assertFalse(SetupRules.canPlaceSettlement(state, other, vertex));
        assertTrue(SetupRules.legalSettlements(state, other).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SetupRules.placeSettlement(state, other, vertex));

        GameState placed = SetupRules.placeSettlement(state, me, vertex);
        int road = SetupRules.legalRoads(placed, me).getFirst();
        assertFalse(SetupRules.canPlaceRoad(placed, other, road));
        assertThrows(IllegalArgumentException.class, () -> SetupRules.placeRoad(placed, other, road));
    }

    @Test
    void rejectsOutOfRangeSpots() {
        GameState state = newGame(5);
        int me = state.currentPlayerId();
        assertFalse(SetupRules.canPlaceSettlement(state, me, -1));
        assertFalse(SetupRules.canPlaceSettlement(state, me, 54));
        GameState placed = SetupRules.placeSettlement(state, me, SetupRules.legalSettlements(state, me).getFirst());
        assertFalse(SetupRules.canPlaceRoad(placed, me, -1));
        assertFalse(SetupRules.canPlaceRoad(placed, me, 72));
    }

    @Test
    void distanceRuleBlocksOccupiedAndNeighboringCorners() {
        GameState state = newGame(6);
        BoardGraph graph = BoardGraph.of(state.board());
        int me = state.currentPlayerId();
        int vertex = 20;
        state = SetupRules.placeSettlement(state, me, vertex);
        state = SetupRules.placeRoad(state, me, SetupRules.legalRoads(state, me).getFirst());
        TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(6));
        turns.advance();
        state = state.withTurn(turns);
        int next = state.currentPlayerId();
        List<Integer> legal = SetupRules.legalSettlements(state, next);
        assertFalse(legal.contains(vertex), "occupied corner");
        for (int neighbor : graph.vertex(vertex).neighborIds()) assertFalse(legal.contains(neighbor), "neighbor " + neighbor);
        assertEquals(54 - 1 - graph.vertex(vertex).neighborIds().size(), legal.size());
    }

    @Test
    void roadMustTouchTheSettlementJustPlaced() {
        GameState state = newGame(7);
        BoardGraph graph = BoardGraph.of(state.board());
        int me = state.currentPlayerId();
        int vertex = SetupRules.legalSettlements(state, me).getFirst();
        GameState placed = SetupRules.placeSettlement(state, me, vertex);
        int farEdge = graph.edges().stream().filter(e -> !e.touches(vertex)).findFirst().orElseThrow().id();
        assertFalse(SetupRules.canPlaceRoad(placed, me, farEdge));
        assertThrows(IllegalArgumentException.class, () -> SetupRules.placeRoad(placed, me, farEdge));
        assertEquals(new HashSet<>(graph.vertex(vertex).edgeIds()), new HashSet<>(SetupRules.legalRoads(placed, me)));
    }

    @Test
    void noPlacementOutsideSetup() {
        GameState state = newGame(8);
        GameState playing = new GameState(state.board(), state.turnOrder(), state.currentPlayerId(),
                GamePhase.PLAYING, 1, 1);
        assertTrue(SetupRules.legalSettlements(playing, playing.currentPlayerId()).isEmpty());
        assertFalse(SetupRules.needsRoad(playing));
    }

    @Test
    void startingResourcesSkipTheDesert() {
        for (int seed = 0; seed < 50; seed++) {
            Board board = BoardGenerator.generate(new Random(seed));
            BoardGraph graph = BoardGraph.of(board);
            int desert = GameState.desertTile(board);
            for (BoardGraph.Vertex v : graph.vertices()) {
                List<ResourceType> cards = SetupRules.startingResources(board, v.id());
                assertFalse(cards.contains(ResourceType.DESERT));
                int expected = v.tileIds().size() - (v.tileIds().contains(desert) ? 1 : 0);
                assertEquals(expected, cards.size(), "one card per land tile, vertex " + v.id());
            }
        }
    }

    @Test
    void secondSettlementGrantsResourcesForThatSettlementOnly() {
        GameState state = newGame(9);
        TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(9));
        for (int step = 0; step < 4; step++) {
            int p = state.currentPlayerId();
            state = SetupRules.placeSettlement(state, p, SetupRules.legalSettlements(state, p).getFirst());
            state = SetupRules.placeRoad(state, p, SetupRules.legalRoads(state, p).getFirst());
            turns.advance();
            state = state.withTurn(turns);
        }
        assertEquals(GamePhase.SETUP_REVERSE, state.phase());
        int p = state.currentPlayerId();
        int vertex = SetupRules.legalSettlements(state, p).getLast();
        state = SetupRules.placeSettlement(state, p, vertex);
        assertEquals(SetupRules.startingResources(state.board(), vertex), state.pieces().hand(p));
    }
}
