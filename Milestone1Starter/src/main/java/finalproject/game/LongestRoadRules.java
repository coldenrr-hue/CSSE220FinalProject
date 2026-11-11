package finalproject.game;

import java.util.List;
import java.util.Objects;

/**
 * Counts each player's longest continuous road and determines who holds the two-point award.
 * A route can revisit a vertex, but it can use each road at most once and cannot pass
 * through an opponent's settlement or city.
 */
public final class LongestRoadRules {
    private static final int MINIMUM_LENGTH = 5;

    private LongestRoadRules() { }

    public static int holder(BoardGraph graph, PlayerAssets assets, List<Integer> players, int currentHolder) {
        int longest = MINIMUM_LENGTH - 1;
        int leader = -1;
        for (int player : players) {
            int length = length(graph, assets, player);
            if (length > longest) {
                longest = length;
                leader = player;
            } else if (length == longest && length >= MINIMUM_LENGTH) {
                if (player == currentHolder) leader = player;
                else if (leader != currentHolder) leader = -1;
            }
        }
        return leader;
    }

    public static int length(BoardGraph graph, PlayerAssets assets, int player) {
        int longest = 0;
        boolean[] used = new boolean[graph.edges().size()];
        for (var entry : assets.roads().entrySet()) {
            if (entry.getValue() != player) continue;
            BoardGraph.Edge road = graph.edge(entry.getKey());
            used[road.id()] = true;
            longest = Math.max(longest, 1 + extend(graph, assets, player, road.a(), used));
            longest = Math.max(longest, 1 + extend(graph, assets, player, road.b(), used));
            used[road.id()] = false;
        }
        return longest;
    }

    private static int extend(BoardGraph graph, PlayerAssets assets, int player, int vertex, boolean[] used) {
        int buildingOwner = assets.settlements().getOrDefault(vertex, player);
        if (buildingOwner != player) return 0;

        int longest = 0;
        for (int edgeId : graph.vertex(vertex).edgeIds()) {
            if (used[edgeId] || !Objects.equals(assets.roads().get(edgeId), player)) continue;
            used[edgeId] = true;
            int next = graph.edge(edgeId).other(vertex);
            longest = Math.max(longest, 1 + extend(graph, assets, player, next, used));
            used[edgeId] = false;
        }
        return longest;
    }
}
