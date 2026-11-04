package finalproject.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Development card rules (official Catan):
 *
 *  - A card costs 1 ore + 1 wool + 1 grain. Buy on your turn, after rolling.
 *  - You can't play a card on the turn you bought it.
 *  - At most ONE card played per turn, at any time during your turn (even before rolling).
 *  - Victory Point cards are never played; they stay hidden and count toward your score.
 *  - Knight: move the robber to another tile, then steal 1 random card from a
 *    player with a settlement on that tile. First to 3 knights gets Largest Army
 *    (+2 VP); someone with MORE knights takes it away.
 *  - Road Building: place 2 free roads, each connected to your roads/settlements.
 *  - Year of Plenty: take any 2 resources from the bank.
 *  - Monopoly: name a resource; every other player gives you all of it.
 *
 * Which cards a player holds is secret, so it lives in DevelopmentHand, not GameState.
 * Only the host should call buy / play / moveRobber / placeFreeRoad; clients use the
 * ...Problem methods (null = allowed) to enable buttons and explain why not.
 */
public final class DevelopmentRules {
    private DevelopmentRules() { }

    /** 1 ore, 1 wool, 1 grain (cards are named after the tile that makes them). */
    public static final List<ResourceType> COST =
            List.of(ResourceType.MOUNTAINS, ResourceType.PASTURE, ResourceType.FIELDS);
    public static final int LARGEST_ARMY_MINIMUM = 3;
    public static final int MAX_ROADS = 15;

    /** A move's new public state plus the acting player's new private hand. */
    public record Result(GameState state, DevelopmentHand hand) { }

    /** The 25-card deck in random order. The host draws from the end. */
    public static List<DevelopmentCard> shuffledDeck(Random random) {
        List<DevelopmentCard> deck = new ArrayList<>();
        for (DevelopmentCard card : DevelopmentCard.values()) {
            for (int i = 0; i < card.copies(); i++) deck.add(card);
        }
        Collections.shuffle(deck, random);
        return deck;
    }

    // =====================================================================
    // Buying
    // =====================================================================

    public static String buyProblem(GameState state, int playerId) {
        if (state.phase() != GamePhase.PLAYING) return "Development cards can be bought after setup.";
        if (state.currentPlayerId() != playerId) return "It is not your turn.";
        if (!state.dice().rolled()) return "Roll the dice before buying.";
        if (state.development().busy()) return "Finish your current development card first.";
        if (state.development().deckSize() == 0) return "There are no development cards left.";
        if (!state.pieces().hasCards(playerId, COST)) return "A development card costs 1 ore, 1 wool and 1 grain.";
        return null;
    }

    /** Host only. drawn is the card the host took off its deck. */
    public static Result buy(GameState state, DevelopmentHand hand, int playerId, DevelopmentCard drawn) {
        failIf(buyProblem(state, playerId));
        Pieces pieces = state.pieces().withoutCards(playerId, COST);
        return new Result(state.with(pieces, state.robberTile(), state.development().withBought(playerId)),
                hand.withBought(drawn));
    }

    // =====================================================================
    // Playing
    // =====================================================================

    public static String playProblem(GameState state, DevelopmentHand hand, int playerId, DevelopmentCard card) {
        if (state.phase() != GamePhase.PLAYING) return "Development cards can be played after setup.";
        if (state.currentPlayerId() != playerId) return "It is not your turn.";
        if (card == DevelopmentCard.VICTORY_POINT) return "Victory Point cards stay hidden and count automatically.";
        if (state.development().busy()) return "Finish your current development card first.";
        if (state.development().playedThisTurn()) return "You can only play one development card per turn.";
        if (hand.readyCount(card) == 0) {
            return hand.freshCount(card) > 0 ? "You can't play a card on the turn you bought it."
                    : "You don't have a " + card.label() + " card.";
        }
        if (card == DevelopmentCard.ROAD_BUILDING && freeRoadsFor(state, playerId) == 0) {
            return "You have nowhere to build a road.";
        }
        return null;
    }

    /**
     * Host only. choices are the resources picked for Year of Plenty (2) or Monopoly (1);
     * empty for the other cards.
     */
    public static Result play(GameState state, DevelopmentHand hand, int playerId,
                              DevelopmentCard card, List<ResourceType> choices) {
        failIf(playProblem(state, hand, playerId, card));
        int needed = card == DevelopmentCard.YEAR_OF_PLENTY ? 2 : card == DevelopmentCard.MONOPOLY ? 1 : 0;
        if (choices.size() != needed) throw new IllegalArgumentException(card.label() + " needs " + needed + " resource choice(s).");
        if (choices.contains(ResourceType.DESERT)) throw new IllegalArgumentException("The desert isn't a resource.");

        DevelopmentHand newHand = hand.withoutPlayed(card);
        DevelopmentState dev = state.development().withPlayed(playerId);
        Pieces pieces = state.pieces();
        switch (card) {
            case KNIGHT -> dev = dev.withKnight(playerId, largestArmyAfterKnight(dev, playerId));
            case ROAD_BUILDING -> dev = dev.withFreeRoads(freeRoadsFor(state, playerId));
            case YEAR_OF_PLENTY -> pieces = pieces.withCards(playerId, choices);
            case MONOPOLY -> {
                ResourceType wanted = choices.get(0);
                List<ResourceType> taken = new ArrayList<>();
                for (int other : state.turnOrder()) {
                    if (other == playerId) continue;
                    List<ResourceType> kept = new ArrayList<>();
                    for (ResourceType r : pieces.hand(other)) (r == wanted ? taken : kept).add(r);
                    pieces = pieces.withHand(other, kept);
                }
                pieces = pieces.withCards(playerId, taken);
            }
            case VICTORY_POINT -> throw new IllegalStateException();   // rejected by playProblem
        }
        return new Result(state.with(pieces, state.robberTile(), dev), newHand);
    }

    /** Whoever holds Largest Army after this player's knight count went up by one. */
    private static int largestArmyAfterKnight(DevelopmentState dev, int playerId) {
        int holder = dev.largestArmy();
        int mine = dev.knights(playerId) + 1;
        if (mine < LARGEST_ARMY_MINIMUM || holder == playerId) return holder;
        if (holder < 0 || mine > dev.knights(holder)) return playerId;
        return holder;
    }

    // =====================================================================
    // Knight: moving the robber
    // =====================================================================

    /** Tiles the robber may move to: anywhere except where it is now. */
    public static List<Integer> robberTiles(GameState state) {
        List<Integer> tiles = new ArrayList<>();
        for (Tile t : state.board().tiles()) if (t.id() != state.robberTile()) tiles.add(t.id());
        return tiles;
    }

    /** Players the mover could steal from at this tile: others with a settlement there and at least 1 card. */
    public static List<Integer> victims(GameState state, int playerId, int tileId) {
        BoardGraph graph = BoardGraph.of(state.board());
        TreeSet<Integer> found = new TreeSet<>();
        for (BoardGraph.Vertex v : graph.vertices()) {
            if (!v.tileIds().contains(tileId)) continue;
            Integer owner = state.pieces().settlements().get(v.id());
            if (owner != null && owner != playerId && !state.pieces().hand(owner).isEmpty()) found.add(owner);
        }
        return new ArrayList<>(found);
    }

    /** victim is -1 when nobody can be stolen from. */
    public static String robberProblem(GameState state, int playerId, int tileId, int victim) {
        if (state.currentPlayerId() != playerId) return "It is not your turn.";
        if (!state.development().movingRobber()) return "You don't need to move the robber right now.";
        if (!robberTiles(state).contains(tileId)) return "The robber has to move to a different tile.";
        List<Integer> options = victims(state, playerId, tileId);
        if (options.isEmpty() ? victim != -1 : !options.contains(victim)) return "You can't steal from that player.";
        return null;
    }

    /** Host only. Moves the robber and steals one random card from the victim. */
    public static GameState moveRobber(GameState state, int playerId, int tileId, int victim, Random random) {
        failIf(robberProblem(state, playerId, tileId, victim));
        Pieces pieces = state.pieces();
        if (victim >= 0) {
            List<ResourceType> theirs = new ArrayList<>(pieces.hand(victim));
            ResourceType stolen = theirs.remove(random.nextInt(theirs.size()));
            pieces = pieces.withHand(victim, theirs).withCards(playerId, List.of(stolen));
        }
        return state.with(pieces, tileId, state.development().withRobberMoved());
    }

    // =====================================================================
    // Road Building: free roads
    // =====================================================================

    /**
     * Empty edges joined to this player's network: an end has their settlement,
     * or has one of their roads and isn't blocked by someone else's settlement.
     */
    public static List<Integer> connectedRoadSpots(GameState state, int playerId) {
        BoardGraph graph = BoardGraph.of(state.board());
        Pieces pieces = state.pieces();
        List<Integer> spots = new ArrayList<>();
        for (BoardGraph.Edge e : graph.edges()) {
            if (pieces.roads().containsKey(e.id())) continue;
            if (reaches(graph, pieces, playerId, e.a(), e.id()) || reaches(graph, pieces, playerId, e.b(), e.id())) {
                spots.add(e.id());
            }
        }
        return spots;
    }

    private static boolean reaches(BoardGraph graph, Pieces pieces, int playerId, int vertexId, int fromEdge) {
        Integer owner = pieces.settlements().get(vertexId);
        if (owner != null) return owner == playerId;
        for (int edgeId : graph.vertex(vertexId).edgeIds()) {
            if (edgeId != fromEdge && Integer.valueOf(playerId).equals(pieces.roads().get(edgeId))) return true;
        }
        return false;
    }

    /** Edges the current player may click while placing Road Building roads. */
    public static List<Integer> legalFreeRoads(GameState state, int playerId) {
        if (state.currentPlayerId() != playerId || state.development().freeRoads() == 0) return List.of();
        return connectedRoadSpots(state, playerId);
    }

    /** Host only. Places one free road; the card ends when both are placed or no spot is left. */
    public static GameState placeFreeRoad(GameState state, int playerId, int edgeId) {
        if (state.currentPlayerId() != playerId) throw new IllegalArgumentException("It is not your turn.");
        if (state.development().freeRoads() == 0) throw new IllegalArgumentException("You don't have any free roads to place.");
        if (!legalFreeRoads(state, playerId).contains(edgeId)) {
            throw new IllegalArgumentException("Your road has to connect to your own road or settlement.");
        }
        Pieces pieces = state.pieces().withRoad(edgeId, playerId);
        int left = Math.min(state.development().freeRoads() - 1, freeRoadsFor(state.withPieces(pieces), playerId));
        return state.with(pieces, state.robberTile(), state.development().withFreeRoads(left));
    }

    /** How many free roads Road Building would give: 2, fewer if out of pieces or space. */
    private static int freeRoadsFor(GameState state, int playerId) {
        int piecesLeft = MAX_ROADS - state.pieces().roadCount(playerId);
        int spots = connectedRoadSpots(state, playerId).isEmpty() ? 0 : 2;
        return Math.max(0, Math.min(spots, piecesLeft));
    }

    private static void failIf(String problem) {
        if (problem != null) throw new IllegalArgumentException(problem);
    }
}
