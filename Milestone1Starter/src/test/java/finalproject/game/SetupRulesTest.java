package finalproject.game;

import finalproject.Dice;
import finalproject.network.GameStateCodec;
import java.util.*;

/** Integration checks for Andrew's setup model with George's dice snapshots. */
public final class SetupRulesTest {
    public static void main(String[] args) {
        for (int seed = 0; seed < 250; seed++) {
            Board board = BoardGenerator.generate(new Random(seed));
            BoardGraph graph = BoardGraph.of(board);
            check(graph.vertices().size() == 54 && graph.edges().size() == 72, "Board topology");
            TurnManager turns = new TurnManager(List.of(0,1,2,3), new Random(seed));
            GameState state = new GameState(board, turns.order(), turns.currentPlayerId(), turns.phase(), 0, 1);
            Map<Integer, List<ResourceType>> expectedHands = new HashMap<>();
            for (int step = 0; step < 8; step++) {
                int player = state.currentPlayerId();
                int vertex = SetupRules.legalSettlements(state, player).getFirst();
                check(!SetupRules.canPlaceSettlement(state, (player+1)%4, vertex), "Wrong player placement");
                check(!SetupRules.canPlaceSettlement(state, player, -1), "Bad vertex");
                check(SetupRules.legalRoads(state, player).isEmpty(), "Road before settlement");
                if (step >= 4) expectedHands.put(player, SetupRules.startingResources(board, vertex));
                state = SetupRules.placeSettlement(state, player, vertex);
                check(SetupRules.legalSettlements(state, player).isEmpty(), "Two settlements in one turn");
                int pendingVertex = vertex;
                for (int legal : SetupRules.legalRoads(state, player)) {
                    check(graph.edge(legal).touches(pendingVertex), "Road adjacency");
                }
                int road = SetupRules.legalRoads(state, player).getFirst();
                state = SetupRules.placeRoad(state, player, road);
                turns.advance();
                state = state.withTurn(turns);
                check(state.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(state)))), "Pieces round trip");
                for (int occupied : state.pieces().settlements().keySet()) {
                    for (int neighbor : graph.vertex(occupied).neighborIds()) {
                        check(!state.pieces().settlements().containsKey(neighbor), "Settlement distance");
                    }
                }
            }
            check(state.phase() == GamePhase.PLAYING, "Normal play after setup");
            for (int player = 0; player < 4; player++) {
                check(state.pieces().settlementCount(player) == 2 && state.pieces().roadCount(player) == 2, "Piece count");
                check(state.pieces().victoryPoints(player) == 2, "Starting victory points");
                check(state.pieces().hand(player).equals(expectedHands.get(player)), "Starting resources");
            }
            GameState rolled = new GameState(board, state.turnOrder(), state.currentPlayerId(), state.phase(),
                    state.turnNumber(), state.revision()+1, state.pieces(), new DiceResult(3,4));
            check(rolled.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(rolled)))), "Dice round trip");
            turns.advance();
            check(!rolled.withTurn(turns).dice().rolled(), "Dice reset with turn");
        }
        Dice dice = new Dice();
        Set<Integer> faces = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int face = dice.roll();
            check(face >= 1 && face <= 6, "Die face range");
            faces.add(face);
        }
        check(faces.size() == 6, "All die faces reachable");
        System.out.println("PASS: 250 complete setups, topology, distance/road rules, starting cards/VP, pieces/dice codec, dice range");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
