package finalproject.game;

import java.util.*;

/** Nine fixed coastal docks, shared by rendering and the host's trade rules. */
public final class HarborRules {
    private HarborRules() { }
    private static Board cachedBoard;
    private static List<Harbor> cachedHarbors;

    /**
     * A coastal edge and its trading specialty. A desert specialty represents a generic 3-for-1
     * harbor.
     */
    public record Harbor(int a, int b, ResourceType resource) {
        public String label() { return resource == ResourceType.DESERT ? "3:1" : "2:1"; }
    }
    /** Reuse the same immutable layout for drawing and all five resource-rate checks. */
    public static synchronized List<Harbor> forBoard(Board board) {
        if (board.equals(cachedBoard)) return cachedHarbors;
        BoardGraph graph = BoardGraph.of(board);
        List<BoardGraph.Edge> coast = new ArrayList<>(graph.edges().stream().filter(e -> e.tileIds().size() == 1).toList());
        coast.sort(Comparator.comparingDouble(e -> Math.atan2(graph.vertex(e.a()).y() + graph.vertex(e.b()).y(),
                graph.vertex(e.a()).x() + graph.vertex(e.b()).x())));
        ResourceType[] types = {ResourceType.DESERT, ResourceType.FOREST, ResourceType.DESERT,
                ResourceType.HILLS, ResourceType.PASTURE, ResourceType.DESERT, ResourceType.FIELDS,
                ResourceType.DESERT, ResourceType.MOUNTAINS};
        List<Harbor> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            var edge = coast.get(i * coast.size() / 9);
            result.add(new Harbor(edge.a(), edge.b(), types[i]));
        }
        cachedBoard = board;
        cachedHarbors = List.copyOf(result);
        return cachedHarbors;
    }
    public static int tradeRate(GameState state, int player, ResourceType resource) {
        int rate = 4;
        for (Harbor harbor : forBoard(state.board())) {
            if (state.assets().settlements().getOrDefault(harbor.a(), -1) != player
                    && state.assets().settlements().getOrDefault(harbor.b(), -1) != player) continue;
            if (harbor.resource() == resource) rate = Math.min(rate, 2);
            else if (harbor.resource() == ResourceType.DESERT) rate = Math.min(rate, 3);
        }
        return rate;
    }
}
