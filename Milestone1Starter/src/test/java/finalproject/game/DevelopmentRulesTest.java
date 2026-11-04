package finalproject.game;

import finalproject.network.GameStateCodec;
import java.util.*;

/** Standalone checks for development cards; throws on failure even without -ea. */
public final class DevelopmentRulesTest {
    private static final ResourceType ORE = ResourceType.MOUNTAINS, WOOL = ResourceType.PASTURE,
            GRAIN = ResourceType.FIELDS, BRICK = ResourceType.HILLS, LUMBER = ResourceType.FOREST;

    public static void main(String[] args) {
        // ---- the deck ----
        List<DevelopmentCard> deck = DevelopmentRules.shuffledDeck(new Random(1));
        check(deck.size() == 25, "Deck size");
        check(Collections.frequency(deck, DevelopmentCard.KNIGHT) == 14
                && Collections.frequency(deck, DevelopmentCard.VICTORY_POINT) == 5
                && Collections.frequency(deck, DevelopmentCard.ROAD_BUILDING) == 2
                && Collections.frequency(deck, DevelopmentCard.YEAR_OF_PLENTY) == 2
                && Collections.frequency(deck, DevelopmentCard.MONOPOLY) == 2, "Deck contents");
        check(!deck.equals(DevelopmentRules.shuffledDeck(new Random(2))), "Deck is shuffled");

        for (int seed = 0; seed < 100; seed++) runGame(seed);
        System.out.println("PASS: deck, buying cost/timing, one card per turn, no playing new cards, knight/robber/steal, "
                + "Largest Army, Road Building, Year of Plenty, Monopoly, hidden VP, codec");
    }

    private static void runGame(int seed) {
        GameState state = afterSetup(seed);
        int me = state.currentPlayerId();
        int next = state.turnOrder().get(1);
        DevelopmentHand hand = DevelopmentHand.EMPTY;
        check(state.robberTile() == GameState.desertTile(state.board()), "Robber starts on the desert");

        // ---- buying ----
        state = withHand(state, me, List.of(ORE, WOOL, GRAIN, BRICK));
        check(DevelopmentRules.buyProblem(state, me).contains("Roll"), "Must roll before buying");
        state = rolled(state);
        check(DevelopmentRules.buyProblem(state, next) != null, "Only the current player buys");
        check(DevelopmentRules.buyProblem(withHand(state, me, List.of(ORE, WOOL)), me).contains("costs"), "Cost check");
        check(DevelopmentRules.buyProblem(state, me) == null, "Can buy");
        DevelopmentRules.Result r = DevelopmentRules.buy(state, hand, me, DevelopmentCard.KNIGHT);
        state = r.state();
        hand = r.hand();
        check(state.pieces().hand(me).equals(List.of(BRICK)), "Cost paid");
        check(state.development().cardCount(me) == 1 && state.development().deckSize() == 24, "Public counts after buying");
        check(hand.freshCount(DevelopmentCard.KNIGHT) == 1, "Card goes to private hand");
        check(DevelopmentRules.playProblem(state, hand, me, DevelopmentCard.KNIGHT).contains("bought"),
                "Can't play a card the turn it was bought");

        // ---- privacy: the public snapshot never contains card names ----
        String wire = String.join("|", GameStateCodec.encode(state));
        for (DevelopmentCard card : DevelopmentCard.values()) check(!wire.contains(card.name()), "Card leaked in GAME_STATE");
        check(state.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(state)))), "Codec round trip");
        check(GameStateCodec.decodeHand(List.of(GameStateCodec.encodeHand(5, hand))).equals(hand), "Hand codec round trip");

        // ---- next turn of mine: knight ----
        hand = hand.endTurn();
        state = rolled(nextTurn(state));
        check(DevelopmentRules.playProblem(state, hand, me, DevelopmentCard.KNIGHT) == null, "Knight playable next turn");
        r = DevelopmentRules.play(state, hand, me, DevelopmentCard.KNIGHT, List.of());
        state = r.state();
        hand = r.hand();
        check(hand.size() == 0 && state.development().cardCount(me) == 0, "Knight removed from hand");
        check(state.development().knights(me) == 1 && state.development().movingRobber(), "Knight counted; robber pending");
        check(DevelopmentRules.buyProblem(withHand(state, me, DevelopmentRules.COST), me).contains("Finish"),
                "Can't buy while the robber is pending");

        int victim = otherSettlementOwner(state, me);
        int tile = tileTouching(state, victim, me);
        state = withHand(state, victim, List.of(LUMBER, LUMBER));
        int myCardsBefore = state.pieces().hand(me).size();
        check(DevelopmentRules.robberProblem(state, me, state.robberTile(), -1) != null, "Robber must move");
        check(DevelopmentRules.victims(state, me, tile).contains(victim), "Victim found");
        check(DevelopmentRules.robberProblem(state, me, tile, -1) != null, "Must steal when someone is there");
        state = DevelopmentRules.moveRobber(state, me, tile, victim, new Random(seed));
        check(state.robberTile() == tile && !state.development().movingRobber(), "Robber moved");
        check(state.pieces().hand(victim).size() == 1 && state.pieces().hand(me).size() == myCardsBefore + 1, "One card stolen");

        // ---- one card per turn ----
        hand = new DevelopmentHand(List.of(DevelopmentCard.MONOPOLY), List.of());
        check(DevelopmentRules.playProblem(state, hand, me, DevelopmentCard.MONOPOLY).contains("one"), "One card per turn");

        // ---- Monopoly ----
        state = rolled(nextTurn(state));
        state = withHand(state, me, List.of());
        for (int other : state.turnOrder()) if (other != me) state = withHand(state, other, List.of(WOOL, WOOL, ORE));
        r = DevelopmentRules.play(state, hand, me, DevelopmentCard.MONOPOLY, List.of(WOOL));
        state = r.state();
        check(Collections.frequency(state.pieces().hand(me), WOOL) == 6, "Monopoly takes all wool");
        for (int other : state.turnOrder()) {
            if (other != me) check(state.pieces().hand(other).equals(List.of(ORE)), "Monopoly leaves other cards");
        }

        // ---- Year of Plenty ----
        state = rolled(nextTurn(state));
        hand = new DevelopmentHand(List.of(DevelopmentCard.YEAR_OF_PLENTY), List.of());
        state = withHand(state, me, List.of());
        try {
            DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE));
            throw new AssertionError("Year of Plenty accepted one choice");
        } catch (IllegalArgumentException expected) { }
        state = DevelopmentRules.play(state, hand, me, DevelopmentCard.YEAR_OF_PLENTY, List.of(ORE, BRICK)).state();
        check(state.pieces().hand(me).equals(List.of(ORE, BRICK)), "Year of Plenty grants two cards");

        // ---- Road Building ----
        state = nextTurn(state);   // playable before rolling
        hand = new DevelopmentHand(List.of(DevelopmentCard.ROAD_BUILDING), List.of());
        int roadsBefore = state.pieces().roadCount(me);
        state = DevelopmentRules.play(state, hand, me, DevelopmentCard.ROAD_BUILDING, List.of()).state();
        check(state.development().freeRoads() == 2, "Two free roads");
        for (int i = 0; i < 2; i++) {
            List<Integer> spots = DevelopmentRules.legalFreeRoads(state, me);
            check(DevelopmentRules.legalFreeRoads(state, next).isEmpty(), "Only the player places free roads");
            for (int spot : spots) check(connectedToMe(state, me, spot), "Free road must connect");
            state = DevelopmentRules.placeFreeRoad(state, me, spots.getFirst());
        }
        check(state.pieces().roadCount(me) == roadsBefore + 2 && state.development().freeRoads() == 0, "Free roads placed");
        try {
            DevelopmentRules.placeFreeRoad(state, me, DevelopmentRules.connectedRoadSpots(state, me).getFirst());
            throw new AssertionError("Third free road accepted");
        } catch (IllegalArgumentException expected) { }

        // ---- Victory point cards can't be played ----
        state = nextTurn(state);
        hand = new DevelopmentHand(List.of(DevelopmentCard.VICTORY_POINT), List.of());
        check(DevelopmentRules.playProblem(state, hand, me, DevelopmentCard.VICTORY_POINT) != null, "VP cards aren't played");
        check(hand.victoryPoints() == 1, "VP card counted privately");

        // ---- Largest Army: first to 3, then only taken by MORE knights ----
        int other = next;
        DevelopmentHand knights = new DevelopmentHand(Collections.nCopies(4, DevelopmentCard.KNIGHT), List.of());
        int base = state.victoryPoints(me);
        for (int i = 0; i < 2; i++) state = playKnightAndMove(state, knights, me, seed);   // 3 knights total
        check(state.development().knights(me) == 3 && state.development().largestArmy() == me, "Largest Army at 3");
        check(state.victoryPoints(me) == base + 2, "Largest Army worth 2 VP");
        state = switchTo(state, other);
        for (int i = 0; i < 3; i++) state = playKnightAndMove(state, knights, other, seed);
        check(state.development().largestArmy() == me, "A tie doesn't take Largest Army");
        state = playKnightAndMove(state, knights, other, seed);
        check(state.development().largestArmy() == other, "More knights takes Largest Army");
        check(state.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(state)))), "Codec round trip at end");
    }

    // ---------------------------------------------------------------- helpers

    private static GameState playKnightAndMove(GameState state, DevelopmentHand hand, int player, int seed) {
        state = DevelopmentRules.play(state, hand, player, DevelopmentCard.KNIGHT, List.of()).state();
        int tile = DevelopmentRules.robberTiles(state).getFirst();
        List<Integer> victims = DevelopmentRules.victims(state, player, tile);
        state = DevelopmentRules.moveRobber(state, player, tile, victims.isEmpty() ? -1 : victims.getFirst(), new Random(seed));
        return nextRound(state, player);
    }

    /** Back to this player's next turn (clears the one-card-per-turn flag). */
    private static GameState nextRound(GameState s, int player) {
        return switchTo(nextTurn(s), player);
    }

    private static GameState switchTo(GameState s, int player) {
        return new GameState(s.board(), s.turnOrder(), player, s.phase(), s.turnNumber() + 1, s.revision() + 1,
                s.pieces(), DiceResult.NONE, s.robberTile(), s.development().nextTurn());
    }

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

    private static int otherSettlementOwner(GameState s, int me) {
        return s.pieces().settlements().values().stream().filter(p -> p != me).findFirst().orElseThrow();
    }

    /** A non-robber tile touching one of victim's settlements (and preferably none of mine). */
    private static int tileTouching(GameState s, int victim, int me) {
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
        check(state.phase() == GamePhase.PLAYING, "Setup finished");
        return state;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
