package finalproject.game;

import static org.junit.jupiter.api.Assertions.*;

import finalproject.network.GameStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Development cards: the deck, buying, the one-card-per-turn rules, and each card's effect. */
class DevelopmentRulesTest {
    private static final ResourceType ORE = ResourceType.MOUNTAINS, WOOL = ResourceType.PASTURE,
            GRAIN = ResourceType.FIELDS, BRICK = ResourceType.HILLS, LUMBER = ResourceType.FOREST;

    private static DevelopmentHand ready(DevelopmentCard... cards) {
        return new DevelopmentHand(List.of(cards), List.of());
    }

    // ------------------------------------------------------------------ the deck

    @Test
    void deckHasTheStandard25Cards() {
        List<DevelopmentCard> deck = DevelopmentRules.shuffledDeck(new Random(1));
        assertEquals(DevelopmentCard.DECK_SIZE, deck.size());
        assertEquals(14, Collections.frequency(deck, DevelopmentCard.KNIGHT));
        assertEquals(5, Collections.frequency(deck, DevelopmentCard.VICTORY_POINT));
        assertEquals(2, Collections.frequency(deck, DevelopmentCard.ROAD_BUILDING));
        assertEquals(2, Collections.frequency(deck, DevelopmentCard.YEAR_OF_PLENTY));
        assertEquals(2, Collections.frequency(deck, DevelopmentCard.MONOPOLY));
    }

    @Test
    void deckIsShuffled() {
        assertNotEquals(DevelopmentRules.shuffledDeck(new Random(1)), DevelopmentRules.shuffledDeck(new Random(2)));
    }

    // ------------------------------------------------------------------ buying

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void buyingPaysTheCostAndHidesTheCard(int seed) {
        GameState state = withHand(afterSetup(seed), me(seed), List.of(ORE, WOOL, GRAIN, BRICK));
        int me = state.currentPlayerId();
        assertTrue(DevelopmentRules.buyProblem(state, me).contains("Roll"), "must roll before buying");
        state = rolled(state);
        assertNull(DevelopmentRules.buyProblem(state, me));

        DevelopmentRules.Result r = DevelopmentRules.buy(state, DevelopmentHand.EMPTY, me, DevelopmentCard.KNIGHT);
        assertEquals(List.of(BRICK), r.state().pieces().hand(me), "ore, wool and grain paid");
        assertEquals(1, r.state().development().cardCount(me), "public card count");
        assertEquals(24, r.state().development().deckSize());
        assertEquals(1, r.hand().freshCount(DevelopmentCard.KNIGHT), "new card is fresh, not ready");
        assertEquals(0, r.hand().readyCount(DevelopmentCard.KNIGHT));
        assertEquals(state.revision() + 1, r.state().revision());
    }

    @Test
    void cannotBuyWithoutTheCost() {
        GameState state = rolled(afterSetup(0));
        int me = state.currentPlayerId();
        GameState poor = withHand(state, me, List.of(ORE, WOOL, WOOL));
        assertTrue(DevelopmentRules.buyProblem(poor, me).contains("costs"));
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.buy(poor, DevelopmentHand.EMPTY, me, DevelopmentCard.KNIGHT));
    }

    @Test
    void onlyTheCurrentPlayerCanBuy() {
        GameState state = rolled(afterSetup(0));
        int other = state.turnOrder().get(1);
        state = withHand(state, other, DevelopmentRules.COST);
        assertTrue(DevelopmentRules.buyProblem(state, other).contains("not your turn"));
    }

    @Test
    void cannotBuyDuringSetup() {
        Board board = BoardGenerator.generate(new Random(0));
        GameState setup = new GameState(board, List.of(0, 1, 2, 3), 0, GamePhase.SETUP_FORWARD, 0, 1,
                Pieces.EMPTY.withCards(0, DevelopmentRules.COST), new DiceResult(1, 1));
        assertNotNull(DevelopmentRules.buyProblem(setup, 0));
        assertNotNull(DevelopmentRules.playProblem(setup, ready(DevelopmentCard.KNIGHT), 0, DevelopmentCard.KNIGHT));
    }

    @Test
    void cannotBuyFromAnEmptyDeck() {
        GameState state = rolled(afterSetup(0));
        int me = state.currentPlayerId();
        DevelopmentState d = state.development();
        state = state.with(state.pieces().withHand(me, DevelopmentRules.COST), state.robberTile(),
                new DevelopmentState(d.cardCounts(), d.knights(), d.largestArmy(), 0, false, false, 0));
        assertTrue(DevelopmentRules.buyProblem(state, me).contains("no development cards"));
    }

    // ------------------------------------------------------------------ playing: timing rules

    @Test
    void cannotPlayACardTheTurnItWasBought() {
        GameState state = rolled(withHand(afterSetup(0), me(0), DevelopmentRules.COST));
        int me = state.currentPlayerId();
        DevelopmentRules.Result r = DevelopmentRules.buy(state, DevelopmentHand.EMPTY, me, DevelopmentCard.KNIGHT);
        assertTrue(DevelopmentRules.playProblem(r.state(), r.hand(), me, DevelopmentCard.KNIGHT).contains("bought"));

        DevelopmentHand nextTurnHand = r.hand().endTurn();
        assertEquals(1, nextTurnHand.readyCount(DevelopmentCard.KNIGHT));
        assertNull(DevelopmentRules.playProblem(nextTurn(r.state()), nextTurnHand, me, DevelopmentCard.KNIGHT));
    }

    @Test
    void cardsCanBePlayedBeforeRolling() {
        GameState state = afterSetup(0);
        assertFalse(state.dice().rolled());
        assertNull(DevelopmentRules.playProblem(state, ready(DevelopmentCard.MONOPOLY), state.currentPlayerId(),
                DevelopmentCard.MONOPOLY));
    }

    @Test
    void onlyOneCardPerTurn() {
        GameState state = rolled(afterSetup(0));
        int me = state.currentPlayerId();
        DevelopmentHand hand = ready(DevelopmentCard.YEAR_OF_PLENTY, DevelopmentCard.MONOPOLY);
        DevelopmentRules.Result r = DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE, ORE));
        assertTrue(r.state().development().playedThisTurn());
        assertTrue(DevelopmentRules.playProblem(r.state(), r.hand(), me, DevelopmentCard.MONOPOLY).contains("one"));
        assertNull(DevelopmentRules.playProblem(nextTurn(r.state()), r.hand(), me, DevelopmentCard.MONOPOLY),
                "allowed again next turn");
    }

    @Test
    void onlyTheCurrentPlayerCanPlay() {
        GameState state = afterSetup(0);
        int other = state.turnOrder().get(1);
        assertTrue(DevelopmentRules.playProblem(state, ready(DevelopmentCard.KNIGHT), other, DevelopmentCard.KNIGHT)
                .contains("not your turn"));
    }

    @Test
    void cannotPlayACardYouDontHave() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        assertTrue(DevelopmentRules.playProblem(state, DevelopmentHand.EMPTY, me, DevelopmentCard.KNIGHT).contains("don't have"));
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.play(state, DevelopmentHand.EMPTY, me, DevelopmentCard.KNIGHT, List.of()));
    }

    @Test
    void victoryPointCardsAreNeverPlayedButCountPrivately() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        DevelopmentHand hand = new DevelopmentHand(List.of(DevelopmentCard.VICTORY_POINT),
                List.of(DevelopmentCard.VICTORY_POINT));
        assertNotNull(DevelopmentRules.playProblem(state, hand, me, DevelopmentCard.VICTORY_POINT));
        assertEquals(2, hand.victoryPoints(), "a VP card counts even the turn it is bought");
        assertEquals(2, state.victoryPoints(me), "public score does not include hidden VP cards");
    }

    // ------------------------------------------------------------------ knight and robber

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void knightMovesRobberAndStealsOneCard(int seed) {
        GameState state = afterSetup(seed);
        int me = state.currentPlayerId();
        DevelopmentRules.Result r = DevelopmentRules.play(state, ready(DevelopmentCard.KNIGHT), me, DevelopmentCard.KNIGHT, List.of());
        state = r.state();
        assertEquals(0, r.hand().size());
        assertEquals(1, state.development().knights(me));
        assertTrue(state.development().movingRobber());
        assertTrue(DevelopmentRules.buyProblem(rolled(withHand(state, me, DevelopmentRules.COST)), me).contains("Finish"),
                "can't buy while the robber is pending");

        int victim = otherSettlementOwner(state, me);
        int tile = tileTouching(state, victim);
        state = withHand(state, victim, List.of(LUMBER, BRICK));
        state = withHand(state, me, List.of());
        assertNotNull(DevelopmentRules.robberProblem(state, me, state.robberTile(), -1), "robber must move");
        assertTrue(DevelopmentRules.victims(state, me, tile).contains(victim));
        assertFalse(DevelopmentRules.victims(state, me, tile).contains(me), "can't steal from yourself");
        assertNotNull(DevelopmentRules.robberProblem(state, me, tile, -1), "must steal when someone is there");

        GameState moved = DevelopmentRules.moveRobber(state, me, tile, victim, new Random(seed));
        assertEquals(tile, moved.robberTile());
        assertFalse(moved.development().movingRobber());
        assertEquals(1, moved.pieces().hand(victim).size());
        assertEquals(1, moved.pieces().hand(me).size());
        List<ResourceType> all = new ArrayList<>(moved.pieces().hand(victim));
        all.addAll(moved.pieces().hand(me));
        all.sort(null);
        assertEquals(List.of(LUMBER, BRICK).stream().sorted().toList(), all, "the stolen card moved, nothing was created");
    }

    @Test
    void robberCannotStealFromSomeoneNotOnTheTile() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        state = DevelopmentRules.play(state, ready(DevelopmentCard.KNIGHT), me, DevelopmentCard.KNIGHT, List.of()).state();
        for (int p : state.turnOrder()) state = withHand(state, p, List.of(LUMBER));
        for (int tile : DevelopmentRules.robberTiles(state)) {
            List<Integer> victims = DevelopmentRules.victims(state, me, tile);
            for (int p : state.turnOrder()) {
                if (p != me && !victims.contains(p)) {
                    assertNotNull(DevelopmentRules.robberProblem(state, me, tile, p), "tile " + tile + " player " + p);
                }
            }
        }
    }

    @Test
    void playersWithNoCardsAreNotVictims() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        state = DevelopmentRules.play(state, ready(DevelopmentCard.KNIGHT), me, DevelopmentCard.KNIGHT, List.of()).state();
        for (int p : state.turnOrder()) state = withHand(state, p, List.of());
        int victim = otherSettlementOwner(state, me);
        int tile = tileTouching(state, victim);
        assertTrue(DevelopmentRules.victims(state, me, tile).isEmpty());
        assertNull(DevelopmentRules.robberProblem(state, me, tile, -1), "nobody to steal from: victim -1 is fine");
        GameState moved = DevelopmentRules.moveRobber(state, me, tile, -1, new Random());
        assertEquals(state.pieces().hands(), moved.pieces().hands());
    }

    @Test
    void robberCanOnlyBeMovedAfterAKnight() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        int tile = DevelopmentRules.robberTiles(state).getFirst();
        assertTrue(DevelopmentRules.robberProblem(state, me, tile, -1).contains("don't need"));
        GameState knighted = DevelopmentRules.play(state, ready(DevelopmentCard.KNIGHT), me, DevelopmentCard.KNIGHT, List.of()).state();
        int other = state.turnOrder().get(1);
        assertTrue(DevelopmentRules.robberProblem(knighted, other, tile, -1).contains("not your turn"));
    }

    @Test
    void robberTilesExcludeOnlyTheCurrentTile() {
        GameState state = afterSetup(0);
        List<Integer> tiles = DevelopmentRules.robberTiles(state);
        assertEquals(18, tiles.size());
        assertFalse(tiles.contains(state.robberTile()));
    }

    // ------------------------------------------------------------------ largest army

    @Test
    void largestArmyGoesToFirstWithThreeAndOnlyChangesForMoreKnights() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        int other = state.turnOrder().get(1);
        DevelopmentHand knights = ready(DevelopmentCard.KNIGHT);
        int base = state.victoryPoints(me);

        for (int i = 0; i < 2; i++) state = playKnightAndMove(state, knights, me);
        assertEquals(-1, state.development().largestArmy(), "2 knights is not enough");
        state = playKnightAndMove(state, knights, me);
        assertEquals(me, state.development().largestArmy());
        assertEquals(base + 2, state.victoryPoints(me), "Largest Army is worth 2 VP");

        state = switchTo(state, other);
        for (int i = 0; i < 3; i++) state = playKnightAndMove(state, knights, other);
        assertEquals(me, state.development().largestArmy(), "a tie does not take Largest Army");
        state = playKnightAndMove(state, knights, other);
        assertEquals(other, state.development().largestArmy(), "more knights takes it");
        assertEquals(base, state.victoryPoints(me), "the previous holder loses the 2 VP");
    }

    // ------------------------------------------------------------------ progress cards

    @Test
    void monopolyTakesEveryOtherPlayersCardsOfThatType() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        state = withHand(state, me, List.of(WOOL, ORE));
        for (int p : state.turnOrder()) if (p != me) state = withHand(state, p, List.of(WOOL, WOOL, ORE));
        GameState after = DevelopmentRules.play(state, ready(DevelopmentCard.MONOPOLY), me, DevelopmentCard.MONOPOLY,
                List.of(WOOL)).state();
        assertEquals(7, Collections.frequency(after.pieces().hand(me), WOOL), "1 own + 2 from each of 3 players");
        assertEquals(1, Collections.frequency(after.pieces().hand(me), ORE));
        for (int p : after.turnOrder()) if (p != me) assertEquals(List.of(ORE), after.pieces().hand(p));
    }

    @Test
    void monopolyNeedsExactlyOneRealResource() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        DevelopmentHand hand = ready(DevelopmentCard.MONOPOLY);
        assertThrows(IllegalArgumentException.class, () -> DevelopmentRules.play(state, hand, me, DevelopmentCard.MONOPOLY, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.play(state, hand, me, DevelopmentCard.MONOPOLY, List.of(ORE, WOOL)));
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.play(state, hand, me, DevelopmentCard.MONOPOLY, List.of(ResourceType.DESERT)));
    }

    @Test
    void yearOfPlentyGivesTwoChosenCards() {
        GameState state = withHand(afterSetup(0), me(0), List.of());
        int me = state.currentPlayerId();
        DevelopmentHand hand = ready(DevelopmentCard.YEAR_OF_PLENTY);
        assertEquals(List.of(ORE, BRICK),
                DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE, BRICK)).state().pieces().hand(me));
        assertEquals(List.of(GRAIN, GRAIN),
                DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(GRAIN, GRAIN)).state().pieces().hand(me),
                "the same resource twice is allowed");
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE)));
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE, ORE, ORE)));
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE, ResourceType.DESERT)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void roadBuildingPlacesTwoConnectedFreeRoads(int seed) {
        GameState state = afterSetup(seed);
        int me = state.currentPlayerId();
        int other = state.turnOrder().get(1);
        int roadsBefore = state.pieces().roadCount(me);
        List<ResourceType> handBefore = state.pieces().hand(me);
        state = DevelopmentRules.play(state, ready(DevelopmentCard.ROAD_BUILDING), me, DevelopmentCard.ROAD_BUILDING, List.of()).state();
        assertEquals(2, state.development().freeRoads());
        assertTrue(DevelopmentRules.buyProblem(rolled(state), me).contains("Finish"), "busy until roads are placed");

        for (int i = 0; i < 2; i++) {
            assertTrue(DevelopmentRules.legalFreeRoads(state, other).isEmpty(), "only the player places free roads");
            List<Integer> spots = DevelopmentRules.legalFreeRoads(state, me);
            for (int spot : spots) assertTrue(connectedToMe(state, me, spot), "free road must connect");
            int edge = spots.getFirst();
            final GameState s = state;
            assertThrows(IllegalArgumentException.class, () -> DevelopmentRules.placeFreeRoad(s, other, edge));
            state = DevelopmentRules.placeFreeRoad(state, me, edge);
        }
        assertEquals(roadsBefore + 2, state.pieces().roadCount(me));
        assertEquals(0, state.development().freeRoads());
        assertEquals(handBefore, state.pieces().hand(me), "free roads cost nothing");
        final GameState done = state;
        assertThrows(IllegalArgumentException.class,
                () -> DevelopmentRules.placeFreeRoad(done, me, DevelopmentRules.connectedRoadSpots(done, me).getFirst()),
                "a third free road");
    }

    @Test
    void freeRoadCannotBeDisconnectedOrOnAnOccupiedEdge() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        state = DevelopmentRules.play(state, ready(DevelopmentCard.ROAD_BUILDING), me, DevelopmentCard.ROAD_BUILDING, List.of()).state();
        List<Integer> legal = DevelopmentRules.legalFreeRoads(state, me);
        for (int occupied : state.pieces().roads().keySet()) assertFalse(legal.contains(occupied));
        final GameState s = state;
        int far = BoardGraph.of(state.board()).edges().stream().map(BoardGraph.Edge::id)
                .filter(e -> !legal.contains(e) && !s.pieces().roads().containsKey(e)).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> DevelopmentRules.placeFreeRoad(s, me, far));
    }

    @Test
    void roadBuildingIsLimitedByTheFifteenRoadPieces() {
        GameState state = afterSetup(0);
        int me = state.currentPlayerId();
        state = giveRoads(state, me, DevelopmentRules.MAX_ROADS - 1);
        state = DevelopmentRules.play(state, ready(DevelopmentCard.ROAD_BUILDING), me, DevelopmentCard.ROAD_BUILDING, List.of()).state();
        assertEquals(1, state.development().freeRoads(), "only one road piece left");

        GameState full = giveRoads(afterSetup(0), me, DevelopmentRules.MAX_ROADS);
        assertTrue(DevelopmentRules.playProblem(full, ready(DevelopmentCard.ROAD_BUILDING), me, DevelopmentCard.ROAD_BUILDING)
                .contains("nowhere"));
    }

    // ------------------------------------------------------------------ the private hand

    @Test
    void developmentHandBookkeeping() {
        DevelopmentHand hand = DevelopmentHand.EMPTY.withBought(DevelopmentCard.KNIGHT).withBought(DevelopmentCard.MONOPOLY);
        assertEquals(2, hand.size());
        assertEquals(0, hand.readyCount(DevelopmentCard.KNIGHT));
        assertThrows(IllegalArgumentException.class, () -> hand.withoutPlayed(DevelopmentCard.KNIGHT), "fresh card played");
        DevelopmentHand later = hand.endTurn();
        assertEquals(List.of(), later.fresh());
        assertEquals(1, later.readyCount(DevelopmentCard.KNIGHT));
        assertEquals(1, later.withoutPlayed(DevelopmentCard.KNIGHT).size());
    }

    @Test
    void publicSnapshotNeverRevealsWhichCardsAreHeld() {
        GameState state = rolled(withHand(afterSetup(0), me(0), DevelopmentRules.COST));
        int me = state.currentPlayerId();
        DevelopmentHand hand = DevelopmentHand.EMPTY;
        for (DevelopmentCard card : DevelopmentCard.values()) {
            state = withHand(state, me, DevelopmentRules.COST);
            DevelopmentRules.Result r = DevelopmentRules.buy(state, hand, me, card);
            state = r.state();
            hand = r.hand();
        }
        String wire = String.join("|", GameStateCodec.encode(state));
        for (DevelopmentCard card : DevelopmentCard.values()) {
            assertFalse(wire.contains(card.name()), card + " leaked in GAME_STATE");
        }
        assertEquals(5, state.development().cardCount(me), "but the count is public");
        assertEquals(state, GameStateCodec.decode(List.of(GameStateCodec.encode(state))));
    }

    // ------------------------------------------------------------------ helpers

    private static int me(int seed) { return afterSetup(seed).currentPlayerId(); }

    private static GameState playKnightAndMove(GameState state, DevelopmentHand hand, int player) {
        state = DevelopmentRules.play(state, hand, player, DevelopmentCard.KNIGHT, List.of()).state();
        int tile = DevelopmentRules.robberTiles(state).getFirst();
        List<Integer> victims = DevelopmentRules.victims(state, player, tile);
        state = DevelopmentRules.moveRobber(state, player, tile, victims.isEmpty() ? -1 : victims.getFirst(), new Random(0));
        return nextTurn(state);
    }

    private static GameState switchTo(GameState s, int player) {
        return new GameState(s.board(), s.turnOrder(), player, s.phase(), s.turnNumber() + 1, s.revision() + 1,
                s.pieces(), DiceResult.NONE, s.robberTile(), s.development().nextTurn());
    }

    /** The same player's next turn (dice and the one-card-per-turn flag reset). */
    private static GameState nextTurn(GameState s) {
        return new GameState(s.board(), s.turnOrder(), s.currentPlayerId(), s.phase(), s.turnNumber() + 4,
                s.revision() + 1, s.pieces(), DiceResult.NONE, s.robberTile(), s.development().nextTurn());
    }

    private static GameState rolled(GameState s) {
        return new GameState(s.board(), s.turnOrder(), s.currentPlayerId(), s.phase(), s.turnNumber(),
                s.revision() + 1, s.pieces(), new DiceResult(2, 3), s.robberTile(), s.development());
    }

    private static GameState withHand(GameState s, int player, List<ResourceType> cards) {
        return s.with(s.pieces().withHand(player, cards), s.robberTile(), s.development());
    }

    /** Extends the player's road network until they own exactly count roads. */
    private static GameState giveRoads(GameState s, int player, int count) {
        Pieces pieces = s.pieces();
        while (pieces.roadCount(player) < count) {
            GameState current = s.withPieces(pieces);
            List<Integer> spots = DevelopmentRules.connectedRoadSpots(current, player);
            assertFalse(spots.isEmpty(), "ran out of road spots");
            pieces = pieces.withRoad(spots.getFirst(), player);
        }
        return s.withPieces(pieces);
    }

    private static int otherSettlementOwner(GameState s, int me) {
        return s.pieces().settlements().values().stream().filter(p -> p != me).findFirst().orElseThrow();
    }

    /** A tile (not the robber's) touching one of victim's settlements. */
    private static int tileTouching(GameState s, int victim) {
        BoardGraph graph = BoardGraph.of(s.board());
        for (var entry : s.pieces().settlements().entrySet()) {
            if (entry.getValue() != victim) continue;
            for (int tile : graph.vertex(entry.getKey()).tileIds()) if (tile != s.robberTile()) return tile;
        }
        throw new AssertionError("No tile next to the victim");
    }

    private static boolean connectedToMe(GameState s, int me, int edgeId) {
        BoardGraph graph = BoardGraph.of(s.board());
        BoardGraph.Edge e = graph.edge(edgeId);
        for (int v : List.of(e.a(), e.b())) {
            if (Integer.valueOf(me).equals(s.pieces().settlements().get(v))) return true;
            for (int adj : graph.vertex(v).edgeIds()) if (Integer.valueOf(me).equals(s.pieces().roads().get(adj))) return true;
        }
        return false;
    }

    private static GameState afterSetup(int seed) {
        Board board = BoardGenerator.generate(new Random(seed));
        TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(seed));
        GameState state = new GameState(board, turns.order(), turns.currentPlayerId(), turns.phase(), 0, 1);
        for (int step = 0; step < 8; step++) {
            int player = state.currentPlayerId();
            state = SetupRules.placeSettlement(state, player, SetupRules.legalSettlements(state, player).getFirst());
            state = SetupRules.placeRoad(state, player, SetupRules.legalRoads(state, player).getFirst());
            turns.advance();
            state = state.withTurn(turns);
        }
        assertEquals(GamePhase.PLAYING, state.phase());
        return state;
    }
}
