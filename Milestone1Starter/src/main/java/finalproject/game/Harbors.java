package finalproject.game;

import java.util.*;

/** Nine fixed coastal docks, shared by rendering and the host's trade rules. */
public final class Harbors {
    private Harbors() { }
    public record Harbor(int a, int b, ResourceType resource) {
        public String label() { return resource == ResourceType.DESERT ? "3:1" : "2:1"; }
    }
    public static List<Harbor> of(Board board) {
        BoardGraph g = BoardGraph.of(board);
        List<BoardGraph.Edge> coast = new ArrayList<>(g.edges().stream().filter(e -> e.tileIds().size() == 1).toList());
        coast.sort(Comparator.comparingDouble(e -> Math.atan2(g.vertex(e.a()).y() + g.vertex(e.b()).y(),
                g.vertex(e.a()).x() + g.vertex(e.b()).x())));
        ResourceType[] types = {ResourceType.DESERT, ResourceType.FOREST, ResourceType.DESERT,
                ResourceType.HILLS, ResourceType.PASTURE, ResourceType.DESERT, ResourceType.FIELDS,
                ResourceType.DESERT, ResourceType.MOUNTAINS};
        List<Harbor> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            var edge = coast.get(i * coast.size() / 9);
            result.add(new Harbor(edge.a(), edge.b(), types[i]));
        }
        return List.copyOf(result);
    }
    public static int rate(GameState state, int player, ResourceType resource) {
        int rate = 4;
        for (Harbor h : of(state.board())) {
            if (state.pieces().settlements().getOrDefault(h.a(), -1) != player
                    && state.pieces().settlements().getOrDefault(h.b(), -1) != player) continue;
            if (h.resource() == resource) rate = Math.min(rate, 2);
            else if (h.resource() == ResourceType.DESERT) rate = Math.min(rate, 3);
        }
        return rate;
    }
}
