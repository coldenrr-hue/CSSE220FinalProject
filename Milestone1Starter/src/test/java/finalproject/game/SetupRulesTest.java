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
                int vertex = GameRules.legalSetupSettlements(state, player).getFirst();
                check(!GameRules.canPlaceSetupSettlement(state, (player+1)%4, vertex), "Wrong player placement");
                check(!GameRules.canPlaceSetupSettlement(state, player, -1), "Bad vertex");
                check(GameRules.legalSetupRoads(state, player).isEmpty(), "Road before settlement");
                if (step >= 4) expectedHands.put(player, GameRules.startingResources(board, vertex));
                state = GameRules.placeSetupSettlement(state, player, vertex);
                check(GameRules.legalSetupSettlements(state, player).isEmpty(), "Two settlements in one turn");
                int pendingVertex = vertex;
                for (int legal : GameRules.legalSetupRoads(state, player)) {
                    check(graph.edge(legal).touches(pendingVertex), "Road adjacency");
                }
                int road = GameRules.legalSetupRoads(state, player).getFirst();
                state = GameRules.placeSetupRoad(state, player, road);
                turns.advance();
                state = state.withTurn(turns);
                check(state.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(state)))), "PlayerAssets round trip");
                for (int occupied : state.assets().settlements().keySet()) {
                    for (int neighbor : graph.vertex(occupied).neighborIds()) {
                        check(!state.assets().settlements().containsKey(neighbor), "Settlement distance");
                    }
                }
            }
            check(state.phase() == GamePhase.PLAYING, "Normal play after setup");
            for (int player = 0; player < 4; player++) {
                check(state.assets().settlementCount(player) == 2 && state.assets().roadCount(player) == 2, "Piece count");
                check(state.assets().victoryPoints(player) == 2, "Starting victory points");
                check(state.assets().hand(player).equals(expectedHands.get(player)), "Starting resources");
            }
            GameState rolled = new GameState(board, state.turnOrder(), state.currentPlayerId(), state.phase(),
                    state.turnNumber(), state.revision()+1, state.assets(), new DiceResult(3,4));
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
