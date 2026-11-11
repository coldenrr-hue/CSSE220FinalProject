package finalproject.game;

import java.util.*;

/** Pure host-side rules; failed actions never mutate hands or board pieces. */
public final class GameRules {
    private GameRules() { }
    // Setup: free settlement followed by an adjoining road.
    public static boolean isSetup(GameState state) {
        return state.phase() == GamePhase.SETUP_FORWARD || state.phase() == GamePhase.SETUP_REVERSE;
    }

    /** True when the current player still has to place their settlement this turn. */
    public static boolean needsSettlement(GameState state) {
        return isSetup(state) && state.assets().pendingSettlement() < 0;
    }

    /** True when the current player has placed a settlement and still needs its road. */
    public static boolean needsRoad(GameState state) {
        return isSetup(state) && state.assets().pendingSettlement() >= 0;
    }

    public static boolean canPlaceSetupSettlement(GameState state, int playerId, int vertexId) {
        BoardGraph graph = BoardGraph.of(state.board());
        PlayerAssets assets = state.assets();
        if (!needsSettlement(state) || playerId != state.currentPlayerId() || !graph.isVertex(vertexId)) return false;
        return isOpenSettlementLocation(graph, assets, vertexId);
    }

    public static boolean canPlaceSetupRoad(GameState state, int playerId, int edgeId) {
        BoardGraph graph = BoardGraph.of(state.board());
        PlayerAssets assets = state.assets();
        if (!needsRoad(state) || playerId != state.currentPlayerId() || !graph.isEdge(edgeId)) return false;
        if (assets.roads().containsKey(edgeId)) return false;
        return graph.edge(edgeId).touches(assets.pendingSettlement());  // must touch the new settlement
    }

    public static List<Integer> legalSetupSettlements(GameState state, int playerId) {
        List<Integer> spots = new ArrayList<>();
        for (BoardGraph.Vertex vertex : BoardGraph.of(state.board()).vertices()) {
            if (canPlaceSetupSettlement(state, playerId, vertex.id())) spots.add(vertex.id());
        }
        return spots;
    }

    public static List<Integer> legalSetupRoads(GameState state, int playerId) {
        List<Integer> spots = new ArrayList<>();
        for (BoardGraph.Edge edge : BoardGraph.of(state.board()).edges()) {
            if (canPlaceSetupRoad(state, playerId, edge.id())) spots.add(edge.id());
        }
        return spots;
    }

    /** Free setup placement; the second settlement grants resources from adjacent tiles. */
    public static GameState placeSetupSettlement(GameState state, int playerId, int vertexId) {
        if (!canPlaceSetupSettlement(state, playerId, vertexId)) {
            throw new IllegalArgumentException("You can't build a settlement there.");
        }
        PlayerAssets assets = state.assets().withSettlement(vertexId, playerId);
        if (state.phase() == GamePhase.SETUP_REVERSE) {
            assets = assets.withCards(playerId, startingResources(state.board(), vertexId));
        }
        return state.withAssets(assets);
    }

    /**
     * Host only. Places the road and finishes the setup turn: after this the host
     * should call turnManager.advance() and send the new turn to everyone.
     */
    public static GameState placeSetupRoad(GameState state, int playerId, int edgeId) {
        if (!canPlaceSetupRoad(state, playerId, edgeId)) {
            throw new IllegalArgumentException("Your road has to touch the settlement you just placed.");
        }
        return state.withAssets(state.assets().withRoad(edgeId, playerId));
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

    // Normal play: paid building, trading, and dice production.
    /** Costs and physical piece limits are shared by the host and building menu. */
    public enum Build {
        ROAD(15, ResourceType.FOREST, ResourceType.HILLS),
        SETTLEMENT(5, ResourceType.FOREST, ResourceType.HILLS, ResourceType.FIELDS, ResourceType.PASTURE),
        CITY(4, ResourceType.MOUNTAINS, ResourceType.MOUNTAINS, ResourceType.MOUNTAINS, ResourceType.FIELDS, ResourceType.FIELDS);

        private final int pieceLimit;
        private final List<ResourceType> cost;

        Build(int pieceLimit, ResourceType... cost) {
            this.pieceLimit = pieceLimit;
            this.cost = List.of(cost);
        }

        public List<ResourceType> cost() { return cost; }

        public int remainingPieces(PlayerAssets assets, int player) {
            int used = switch (this) {
                case ROAD -> assets.roadCount(player);
                case SETTLEMENT -> assets.settlementCount(player);
                case CITY -> assets.cityCount(player);
            };
            return pieceLimit - used;
        }
    }

    public static void requireTurn(GameState state, int player) {
        if (state.phase() != GamePhase.PLAYING || state.currentPlayerId() != player || !state.dice().rolled())
            throw new IllegalArgumentException("Wait for your turn and roll before trading or building.");
        if (state.seven().active())
            throw new IllegalArgumentException("Resolve the seven before trading or building.");
        if (state.development().busy())
            throw new IllegalArgumentException("Finish your development card before trading or building.");
    }
    public static boolean hasResources(PlayerAssets assets, int player, List<ResourceType> cards) {
        List<ResourceType> hand = new ArrayList<>(assets.hand(player));
        for (ResourceType card : cards) if (!hand.remove(card)) return false;
        return true;
    }
    static PlayerAssets exchange(PlayerAssets assets, int player, List<ResourceType> give, List<ResourceType> receive) {
        if (!hasResources(assets, player, give)) throw new IllegalArgumentException("Not enough resource cards.");
        List<ResourceType> hand = new ArrayList<>(assets.hand(player));
        give.forEach(hand::remove);
        hand.addAll(receive);
        return assets.replaceHand(player, hand);
    }
    public static int bankStock(PlayerAssets assets, ResourceType type) {
        return 19 - (int) assets.hands().values().stream().flatMap(List::stream).filter(r -> r == type).count();
    }
    public static PlayerAssets bankTrade(GameState state, int player, ResourceType give, ResourceType receive, int quantity) {
        requireTurn(state, player);
        if (give == receive || give == ResourceType.DESERT || receive == ResourceType.DESERT || quantity < 1 || quantity > 19)
            throw new IllegalArgumentException("Choose different resources and a quantity from 1 to 19.");
        if (bankStock(state.assets(), receive) < quantity) throw new IllegalArgumentException("The bank does not have enough of that resource.");
        return exchange(state.assets(), player, Collections.nCopies(HarborRules.tradeRate(state, player, give) * quantity, give), Collections.nCopies(quantity, receive));
    }
    public static void validateOffer(GameState state, TradeOffer offer) {
        requireTurn(state, offer.from());
        if (!hasResources(state.assets(), offer.from(), offer.give())) throw new IllegalArgumentException("You do not have the offered cards.");
    }
    public static PlayerAssets acceptTrade(GameState state, int player) {
        TradeOffer offer = state.offer();
        if (offer == null || player != offer.to()) throw new IllegalArgumentException("This offer is not addressed to you.");
        validateOffer(state, offer);
        if (!hasResources(state.assets(), player, offer.receive())) throw new IllegalArgumentException("You do not have the requested cards.");
        PlayerAssets proposerPaid = exchange(state.assets(), offer.from(), offer.give(), offer.receive());
        return exchange(proposerPaid, player, offer.receive(), offer.give());
    }
    private static boolean canAffordBuild(GameState state, int player, Build build) {
        return state.phase() == GamePhase.PLAYING
                && player == state.currentPlayerId()
                && state.dice().rolled()
                && !state.seven().active()
                && !state.development().busy()
                && build.remainingPieces(state.assets(), player) > 0
                && hasResources(state.assets(), player, build.cost());
    }

    public static boolean canBuild(GameState state, int player, Build build, int spot) {
        return canAffordBuild(state, player, build)
                && isLegalBuildLocation(BoardGraph.of(state.board()), state.assets(), player, build, spot);
    }

    /** Location-only checks, shared by the host and the board's placement highlights. */
    private static boolean isLegalBuildLocation(BoardGraph graph, PlayerAssets assets,
                                                int player, Build build, int spot) {
        return switch (build) {
            case CITY -> graph.isVertex(spot)
                    && assets.settlements().getOrDefault(spot, -1) == player
                    && !assets.cities().contains(spot);
            case SETTLEMENT -> isOpenSettlementLocation(graph, assets, spot)
                    && hasOwnRoadAt(graph, assets, player, spot);
            case ROAD -> canConnectRoad(graph, assets, player, spot);
        };
    }

    static boolean canConnectRoad(BoardGraph graph, PlayerAssets assets, int player, int spot) {
        return graph.isEdge(spot) && !assets.roads().containsKey(spot)
                && (canConnectRoadAt(graph, assets, player, graph.edge(spot).a())
                    || canConnectRoadAt(graph, assets, player, graph.edge(spot).b()));
    }

    private static boolean isOpenSettlementLocation(BoardGraph graph, PlayerAssets assets, int vertex) {
        return graph.isVertex(vertex) && !assets.settlements().containsKey(vertex)
                && graph.vertex(vertex).neighborIds().stream().noneMatch(assets.settlements()::containsKey);
    }

    private static boolean hasOwnRoadAt(BoardGraph graph, PlayerAssets assets, int player, int vertex) {
        return graph.vertex(vertex).edgeIds().stream()
                .anyMatch(edge -> assets.roads().getOrDefault(edge, -1) == player);
    }

    private static boolean canConnectRoadAt(BoardGraph graph, PlayerAssets assets, int player, int vertex) {
        Integer owner = assets.settlements().get(vertex);
        // An opponent's settlement or city interrupts a road network.
        if (owner != null) return owner == player;
        return hasOwnRoadAt(graph, assets, player, vertex);
    }

    public static List<Integer> legalBuilds(GameState state, int player, Build build) {
        if (!canAffordBuild(state, player, build)) return List.of();
        BoardGraph graph = BoardGraph.of(state.board());
        int locationCount = build == Build.ROAD ? graph.edges().size() : graph.vertices().size();
        List<Integer> legalLocations = new ArrayList<>();
        // Check the hand and piece supply once, rather than once per board location.
        for (int spot = 0; spot < locationCount; spot++) {
            if (isLegalBuildLocation(graph, state.assets(), player, build, spot)) legalLocations.add(spot);
        }
        return List.copyOf(legalLocations);
    }

    public static PlayerAssets build(GameState state, int player, Build build, int spot) {
        if (!canBuild(state, player, build, spot)) throw new IllegalArgumentException("Cannot build there: check your cards, connections and remaining assets.");
        PlayerAssets paid = exchange(state.assets(), player, build.cost(), List.of());
        return switch (build) {
            case ROAD -> paid.withRoad(spot, player);
            case SETTLEMENT -> new PlayerAssets(paid.withSettlement(spot, player).settlements(), paid.roads(), -1, paid.hands(), paid.cities());
            case CITY -> paid.withCity(spot);
        };
    }
    /** Pay matching tiles; a shortage pays nobody unless only one player is owed that resource. */
    public static PlayerAssets collectDiceResources(GameState state) {
        if (state.dice().total() == 7) return state.assets();
        PlayerAssets result = state.assets();
        BoardGraph graph = BoardGraph.of(state.board());
        for (ResourceType resource : ResourceType.values()) {
            if (resource == ResourceType.DESERT) continue;
            Map<Integer, Integer> resourcesOwed = new HashMap<>();
            state.assets().settlements().forEach((vertex, player) -> {
                for (int tile : graph.vertex(vertex).tileIds()) {
                    if (tile == state.robberTile()) continue;
                    if (graph.tile(tile).resource() == resource && graph.tile(tile).number() == state.dice().total())
                        resourcesOwed.merge(player, state.assets().cities().contains(vertex) ? 2 : 1, Integer::sum);
                }
            });
            int stock = bankStock(result, resource);
            int total = resourcesOwed.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= stock || resourcesOwed.size() == 1) {
                for (var entry : resourcesOwed.entrySet()) result = result.withCards(entry.getKey(), Collections.nCopies(Math.min(stock, entry.getValue()), resource));
            }
        }
        return result;
    }
}
