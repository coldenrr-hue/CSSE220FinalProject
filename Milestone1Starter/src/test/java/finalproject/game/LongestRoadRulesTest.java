package finalproject.game;

import finalproject.network.GameStateCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Verifies road lengths, award transfers and ties, and the two-point win bonus. */
public final class LongestRoadRulesTest {
    public static void main(String[] args) {
        Board board = BoardGenerator.generate(new Random(11));
        BoardGraph graph = BoardGraph.of(board);
        List<Integer> seats = List.of(0, 1, 2, 3);
        List<BoardGraph.Edge> coast = coastalPath(graph);
        PlayerAssets assets = addRoads(PlayerAssets.EMPTY, coast.subList(0, 4), 0);
        check(LongestRoadRules.holder(graph, assets, seats, -1) == -1, "Four roads cannot earn the card");
        assets = addRoads(assets, coast.subList(4, 5), 0);
        check(LongestRoadRules.holder(graph, assets, seats, -1) == 0, "Five roads should earn the card");

        assets = addRoads(assets, coast.subList(8, 13), 1);
        check(LongestRoadRules.holder(graph, assets, seats, 0) == 0, "The holder should keep a tie");
        check(LongestRoadRules.holder(graph, assets, seats, 1) == 1, "The holder can appear later in seat order");
        check(LongestRoadRules.holder(graph, assets, seats, -1) == -1, "A tie without a holder has no winner");
        PlayerAssets overtaken = addRoads(assets, coast.subList(13, 14), 1);
        check(LongestRoadRules.holder(graph, overtaken, seats, 0) == 1, "A longer route takes the award");

        int junction = sharedVertex(coast.get(1), coast.get(2));
        PlayerAssets blocked = assets.withSettlement(junction, 2);
        check(LongestRoadRules.length(graph, blocked, 0) == 3, "An opponent's settlement should split the road");
        check(LongestRoadRules.holder(graph, blocked, seats, 0) == 1, "The other qualifying route takes the card");
        PlayerAssets tiedAfterBlock = addRoads(blocked, coast.subList(16, 21), 3);
        check(LongestRoadRules.holder(graph, tiedAfterBlock, seats, 0) == -1, "Award should return on a tie after a split");
        check(LongestRoadRules.length(graph, assets.withSettlement(junction, 0), 0) == 5, "Own settlement blocks road");

        List<BoardGraph.Edge> hex = graph.edges().stream().filter(edge -> edge.tileIds().contains(0)).toList();
        check(LongestRoadRules.length(graph, addRoads(PlayerAssets.EMPTY, hex, 0), 0) == 6, "Loop length is wrong");
        BoardGraph.Vertex fork = graph.vertices().stream().filter(v -> v.edgeIds().size() == 3).findFirst().orElseThrow();
        List<BoardGraph.Edge> branches = fork.edgeIds().stream().map(graph::edge).toList();
        check(LongestRoadRules.length(graph, addRoads(PlayerAssets.EMPTY, branches, 0), 0) == 2, "Fork counts all branches");

        for (int vertex : List.of(0, 4, 8, 12)) assets = assets.withSettlement(vertex, 0).withCity(vertex);
        GameState winning = new GameState(board, seats, 0, GamePhase.PLAYING, 8, 10, assets, DiceResult.NONE, null,
                GameState.desertTile(board), DevelopmentState.START, SevenResolution.NONE, 0);
        check(winning.victoryPoints(0) == 10, "Road award did not add two points");
        check(MatchResult.ifWon(winning, Map.of(), Map.of()).winnerId() == 0, "Road award cannot win the game");
        check(GameStateCodec.decode(List.of(GameStateCodec.encode(winning))).equals(winning), "Road award did not sync");
        System.out.println("PASS: Longest Road routes, ties, transfers, scoring, and snapshot codec");
    }

    private static List<BoardGraph.Edge> coastalPath(BoardGraph graph) {
        List<BoardGraph.Edge> coast = new ArrayList<>();
        BoardGraph.Edge edge = graph.edges().stream().filter(e -> e.tileIds().size() == 1).findFirst().orElseThrow();
        int vertex = edge.a();
        while (!coast.contains(edge)) {
            coast.add(edge);
            vertex = edge.other(vertex);
            BoardGraph.Edge previous = edge;
            edge = graph.vertex(vertex).edgeIds().stream().map(graph::edge)
                    .filter(e -> e.tileIds().size() == 1 && e.id() != previous.id()).findFirst().orElseThrow();
        }
        return coast;
    }

    private static int sharedVertex(BoardGraph.Edge a, BoardGraph.Edge b) {
        return b.touches(a.a()) ? a.a() : a.b();
    }

    private static PlayerAssets addRoads(PlayerAssets assets, List<BoardGraph.Edge> roads, int player) {
        for (BoardGraph.Edge road : roads) assets = assets.withRoad(road.id(), player);
        return assets;
    }

    private static void check(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
}
