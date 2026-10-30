package finalproject.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules for the setup phase (official Catan "founding phase"):
 *
 *  - Turn order snakes: A, B, C, D, then D, C, B, A (TurnManager already does this).
 *  - On each setup turn the player places ONE settlement, then ONE road touching it.
 *    Placing the road ends their setup turn.
 *  - Distance rule: a settlement can't be on a corner next to any other settlement
 *    (yours or anyone else's). In setup it does NOT need to connect to a road.
 *  - After placing their SECOND settlement (reverse round), the player gets one
 *    resource card for each land tile touching it (the desert gives nothing).
 *
 * Only the host should call the place... methods; clients use the can... methods
 * to highlight legal spots.
 */
public final class SetupRules {
    private SetupRules() { }

    public static boolean isSetup(GameState state) {
        return state.phase() == GamePhase.SETUP_FORWARD || state.phase() == GamePhase.SETUP_REVERSE;
    }

    /** True when the current player still has to place their settlement this turn. */
    public static boolean needsSettlement(GameState state) {
        return isSetup(state) && state.pieces().pendingSettlement() < 0;
    }

    /** True when the current player has placed a settlement and still needs its road. */
    public static boolean needsRoad(GameState state) {
        return isSetup(state) && state.pieces().pendingSettlement() >= 0;
    }

    public static boolean canPlaceSettlement(GameState state, int playerId, int vertexId) {
        BoardGraph graph = BoardGraph.of(state.board());
        Pieces pieces = state.pieces();
        if (!needsSettlement(state) || playerId != state.currentPlayerId() || !graph.isVertex(vertexId)) return false;
        if (pieces.settlements().containsKey(vertexId)) return false;
        for (int neighbor : graph.vertex(vertexId).neighborIds()) {
            if (pieces.settlements().containsKey(neighbor)) return false;   // distance rule
        }
        return true;
    }

    public static boolean canPlaceRoad(GameState state, int playerId, int edgeId) {
        BoardGraph graph = BoardGraph.of(state.board());
        Pieces pieces = state.pieces();
        if (!needsRoad(state) || playerId != state.currentPlayerId() || !graph.isEdge(edgeId)) return false;
        if (pieces.roads().containsKey(edgeId)) return false;
        return graph.edge(edgeId).touches(pieces.pendingSettlement());  // must touch the new settlement
    }

    public static List<Integer> legalSettlements(GameState state, int playerId) {
        List<Integer> spots = new ArrayList<>();
        for (BoardGraph.Vertex v : BoardGraph.of(state.board()).vertices()) {
            if (canPlaceSettlement(state, playerId, v.id())) spots.add(v.id());
        }
        return spots;
    }

    public static List<Integer> legalRoads(GameState state, int playerId) {
        List<Integer> spots = new ArrayList<>();
        for (BoardGraph.Edge e : BoardGraph.of(state.board()).edges()) {
            if (canPlaceRoad(state, playerId, e.id())) spots.add(e.id());
        }
        return spots;
    }

    /** Host only. Returns the new state (same turn), or throws if the move is illegal. */
    public static GameState placeSettlement(GameState state, int playerId, int vertexId) {
        if (!canPlaceSettlement(state, playerId, vertexId)) {
            throw new IllegalArgumentException("You can't build a settlement there.");
        }
        Pieces pieces = state.pieces().withSettlement(vertexId, playerId);
        if (state.phase() == GamePhase.SETUP_REVERSE) {
            pieces = pieces.withCards(playerId, startingResources(state.board(), vertexId));
        }
        return state.withPieces(pieces);
    }

    /**
     * Host only. Places the road and finishes the setup turn: after this the host
     * should call turnManager.advance() and send the new turn to everyone.
     */
    public static GameState placeRoad(GameState state, int playerId, int edgeId) {
        if (!canPlaceRoad(state, playerId, edgeId)) {
            throw new IllegalArgumentException("Your road has to touch the settlement you just placed.");
        }
        return state.withPieces(state.pieces().withRoad(edgeId, playerId));
    }

    /** One card per land tile touching the corner. */
    public static List<ResourceType> startingResources(Board board, int vertexId) {
        BoardGraph graph = BoardGraph.of(board);
        List<ResourceType> cards = new ArrayList<>();
        for (int tileId : graph.vertex(vertexId).tileIds()) {
            ResourceType type = graph.tile(tileId).resource();
            if (type != ResourceType.DESERT) cards.add(type);
        }
        return cards;
    }
}