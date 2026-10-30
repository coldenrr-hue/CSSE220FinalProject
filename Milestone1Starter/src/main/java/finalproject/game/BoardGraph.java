package finalproject.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The corners (vertices) and sides (edges) of a board: 54 vertices and 72 edges.
 * Settlements and cities go on vertices, roads go on edges.
 *
 * IDs are worked out from the tiles in ID order, so every laptop that receives
 * the same Board gets the same vertex and edge IDs. That means a message like
 * "PLACE_SETTLEMENT 17" means the same corner on every screen.
 */
public final class BoardGraph {

    /** x, y are in hex units: 1 = distance from a tile's center to its corner. */
    public record Vertex(int id, double x, double y, List<Integer> tileIds,
                         List<Integer> neighborIds, List<Integer> edgeIds) { }

    public record Edge(int id, int a, int b, List<Integer> tileIds) {
        public boolean touches(int vertexId) { return a == vertexId || b == vertexId; }
        public int other(int vertexId) { return vertexId == a ? b : a; }
    }

    private final Board board;
    private final List<Vertex> vertices;
    private final List<Edge> edges;

    private static BoardGraph cached;

    /** Builds (or reuses) the graph for a board. */
    public static synchronized BoardGraph of(Board board) {
        if (cached == null || !cached.board.equals(board)) cached = new BoardGraph(board);
        return cached;
    }

    private BoardGraph(Board board) {
        this.board = board;

        // Mutable working copies while we build.
        List<double[]> pos = new ArrayList<>();
        List<List<Integer>> vTiles = new ArrayList<>(), vNeighbors = new ArrayList<>(), vEdges = new ArrayList<>();
        List<int[]> ends = new ArrayList<>();
        List<List<Integer>> eTiles = new ArrayList<>();
        Map<String, Integer> vertexByPos = new HashMap<>();
        Map<String, Integer> edgeByEnds = new HashMap<>();

        List<Tile> tiles = new ArrayList<>(board.tiles());
        tiles.sort(Comparator.comparingInt(Tile::id));

        for (Tile tile : tiles) {
            double cx = Math.sqrt(3) * (tile.q() + tile.r() / 2.0);
            double cy = 1.5 * tile.r();
            int[] corners = new int[6];
            for (int i = 0; i < 6; i++) {                  // corner 0 = top point, then clockwise
                double a = Math.toRadians(60 * i - 90);
                double x = cx + Math.cos(a), y = cy + Math.sin(a);
                String key = Math.round(x * 1000) + "," + Math.round(y * 1000);
                Integer id = vertexByPos.get(key);
                if (id == null) {
                    id = pos.size();
                    vertexByPos.put(key, id);
                    pos.add(new double[]{x, y});
                    vTiles.add(new ArrayList<>());
                    vNeighbors.add(new ArrayList<>());
                    vEdges.add(new ArrayList<>());
                }
                vTiles.get(id).add(tile.id());
                corners[i] = id;
            }
            for (int i = 0; i < 6; i++) {
                int a = corners[i], b = corners[(i + 1) % 6];
                String key = Math.min(a, b) + "-" + Math.max(a, b);
                Integer id = edgeByEnds.get(key);
                if (id == null) {
                    id = ends.size();
                    edgeByEnds.put(key, id);
                    ends.add(new int[]{a, b});
                    eTiles.add(new ArrayList<>());
                    vEdges.get(a).add(id);
                    vEdges.get(b).add(id);
                    vNeighbors.get(a).add(b);
                    vNeighbors.get(b).add(a);
                }
                eTiles.get(id).add(tile.id());
            }
        }

        List<Vertex> vs = new ArrayList<>();
        for (int i = 0; i < pos.size(); i++) {
            vs.add(new Vertex(i, pos.get(i)[0], pos.get(i)[1],
                    List.copyOf(vTiles.get(i)), List.copyOf(vNeighbors.get(i)), List.copyOf(vEdges.get(i))));
        }
        List<Edge> es = new ArrayList<>();
        for (int i = 0; i < ends.size(); i++) {
            es.add(new Edge(i, ends.get(i)[0], ends.get(i)[1], List.copyOf(eTiles.get(i))));
        }
        vertices = List.copyOf(vs);
        edges = List.copyOf(es);
    }

    public List<Vertex> vertices() { return vertices; }
    public List<Edge> edges() { return edges; }
    public Vertex vertex(int id) { return vertices.get(id); }
    public Edge edge(int id) { return edges.get(id); }
    public boolean isVertex(int id) { return id >= 0 && id < vertices.size(); }
    public boolean isEdge(int id) { return id >= 0 && id < edges.size(); }

    public Tile tile(int tileId) {
        for (Tile t : board.tiles()) if (t.id() == tileId) return t;
        throw new IllegalArgumentException("No tile " + tileId);
    }
}