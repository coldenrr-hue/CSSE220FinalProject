package finalproject.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Board generation, turn order, and the basic value records (Tile, Board, DiceResult, GameState). */
class GameModelTest {
    private static final int SEEDS = 2000;
    private static final Map<ResourceType, Long> EXPECTED_TERRAIN = Map.of(
            ResourceType.FOREST, 4L, ResourceType.PASTURE, 4L, ResourceType.FIELDS, 4L,
            ResourceType.HILLS, 3L, ResourceType.MOUNTAINS, 3L, ResourceType.DESERT, 1L);
    private static final List<Integer> EXPECTED_TOKENS =
            List.of(0, 2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12);

    // ------------------------------------------------------------------ board generation

    @Test
    void sameSeedGivesSameBoard() {
        for (int seed = 0; seed < 50; seed++) {
            assertEquals(BoardGenerator.generate(new Random(seed)), BoardGenerator.generate(new Random(seed)),
                    "seed " + seed);
        }
    }

    @Test
    void everyBoardHasStandardTerrainAndTokens() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Board board = BoardGenerator.generate(new Random(seed));
            assertEquals(EXPECTED_TERRAIN, board.tiles().stream()
                    .collect(Collectors.groupingBy(Tile::resource, Collectors.counting())), "terrain, seed " + seed);
            assertEquals(EXPECTED_TOKENS, board.tiles().stream().map(Tile::number).sorted().toList(),
                    "tokens, seed " + seed);
        }
    }

    @Test
    void desertHasNoTokenAndOnlyDesertHasNoToken() {
        for (int seed = 0; seed < SEEDS; seed++) {
            for (Tile t : BoardGenerator.generate(new Random(seed)).tiles()) {
                assertEquals(t.resource() == ResourceType.DESERT, t.number() == 0, "seed " + seed + " tile " + t);
            }
        }
    }

    @Test
    void redNumbersAreNeverAdjacent() {
        for (int seed = 0; seed < SEEDS; seed++) {
            List<Tile> tiles = BoardGenerator.generate(new Random(seed)).tiles();
            for (Tile a : tiles) for (Tile b : tiles) {
                assertFalse(a.hasRedNumber() && b.hasRedNumber() && a.isAdjacentTo(b),
                        "seed " + seed + ": " + a + " next to " + b);
            }
        }
    }

    @Test
    void tilesFormTheStandardHexagon() {
        Board board = BoardGenerator.generate(new Random(7));
        Set<String> positions = new HashSet<>();
        for (Tile t : board.tiles()) positions.add(t.q() + "," + t.r());
        Set<String> expected = new HashSet<>();
        for (int q = -2; q <= 2; q++) for (int r = -2; r <= 2; r++) {
            if (Math.abs(q + r) <= 2) expected.add(q + "," + r);
        }
        assertEquals(expected, positions);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18),
                board.tiles().stream().map(Tile::id).collect(Collectors.toSet()));
    }

    @Test
    void boardsAreRandomized() {
        Set<Board> boards = new HashSet<>();
        for (int seed = 0; seed < SEEDS; seed++) boards.add(BoardGenerator.generate(new Random(seed)));
        assertTrue(boards.size() > SEEDS * 0.95, "only " + boards.size() + " distinct boards");
    }

    @Test
    void tileAdjacency() {
        Tile center = new Tile(0, 0, 0, ResourceType.FOREST, 5);
        assertTrue(center.isAdjacentTo(new Tile(1, 1, 0, ResourceType.HILLS, 3)));
        assertTrue(center.isAdjacentTo(new Tile(1, 1, -1, ResourceType.HILLS, 3)));
        assertFalse(center.isAdjacentTo(new Tile(1, 1, 1, ResourceType.HILLS, 3)), "(1,1) is two steps away");
        assertFalse(center.isAdjacentTo(new Tile(1, 2, 0, ResourceType.HILLS, 3)));
    }

    @Test
    void tileRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new Tile(0, 3, 0, ResourceType.FOREST, 5), "off board");
        assertThrows(IllegalArgumentException.class, () -> new Tile(19, 0, 0, ResourceType.FOREST, 5), "bad id");
        assertThrows(IllegalArgumentException.class, () -> new Tile(0, 0, 0, ResourceType.FOREST, 7), "no 7 token");
        assertThrows(IllegalArgumentException.class, () -> new Tile(0, 0, 0, ResourceType.FOREST, 0), "land needs a token");
        assertThrows(IllegalArgumentException.class, () -> new Tile(0, 0, 0, ResourceType.DESERT, 6), "desert has no token");
    }

    @Test
    void boardRejectsWrongTileCountsAndDuplicates() {
        List<Tile> tiles = new ArrayList<>(BoardGenerator.generate(new Random(1)).tiles());
        assertThrows(IllegalArgumentException.class, () -> new Board(tiles.subList(0, 18)));
        List<Tile> duplicated = new ArrayList<>(tiles);
        duplicated.set(18, tiles.get(0));
        assertThrows(IllegalArgumentException.class, () -> new Board(duplicated));
    }

    // ------------------------------------------------------------------ turns

    @Test
    void setupSnakesThenNormalPlayCycles() {
        for (int seed = 0; seed < 200; seed++) {
            TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(seed));
            List<Integer> order = turns.order();
            List<Integer> setup = new ArrayList<>(order);
            List<Integer> reverse = new ArrayList<>(order);
            Collections.reverse(reverse);
            setup.addAll(reverse);
            for (int i = 0; i < 8; i++) {
                assertEquals(setup.get(i), turns.currentPlayerId(), "setup step " + i);
                assertEquals(i < 4 ? GamePhase.SETUP_FORWARD : GamePhase.SETUP_REVERSE, turns.phase(), "step " + i);
                assertEquals(0, turns.turnNumber(), "turn number stays 0 during setup");
                turns.advance();
            }
            for (int i = 0; i < 12; i++) {
                assertEquals(GamePhase.PLAYING, turns.phase());
                assertEquals(order.get(i % 4), turns.currentPlayerId(), "normal turn " + i);
                assertEquals(i + 1, turns.turnNumber());
                turns.advance();
            }
        }
    }

    @Test
    void turnOrderIsAShuffleOfAllFourSeats() {
        // One shared Random, like the server. (Fresh new Random(0), new Random(1), ... have
        // correlated first draws and only ever produce a handful of orders.)
        Random random = new Random(12345);
        Set<List<Integer>> orders = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            List<Integer> order = new TurnManager(List.of(0, 1, 2, 3), random).order();
            assertEquals(Set.of(0, 1, 2, 3), new HashSet<>(order));
            orders.add(order);
        }
        assertEquals(24, orders.size(), "every one of the 24 possible turn orders should appear");
    }

    @Test
    void turnManagerNeedsFourDistinctSeats() {
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(List.of(0, 1, 2), new Random()));
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(List.of(0, 1, 2, 2), new Random()));
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(List.of(0, 1, 2, 4), new Random()));
    }

    // ------------------------------------------------------------------ dice and game state

    @Test
    void diceResultValidation() {
        assertFalse(DiceResult.NONE.rolled());
        DiceResult r = new DiceResult(6, 1);
        assertTrue(r.rolled());
        assertEquals(7, r.total());
        assertThrows(IllegalArgumentException.class, () -> new DiceResult(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new DiceResult(7, 1));
        assertThrows(IllegalArgumentException.class, () -> new DiceResult(1, -1));
    }

    @Test
    void newGameStartsWithRobberOnDesertAndNoDevelopmentCards() {
        Board board = BoardGenerator.generate(new Random(3));
        GameState state = new GameState(board, List.of(2, 0, 3, 1), 2, GamePhase.SETUP_FORWARD, 0, 1);
        Tile robber = board.tiles().stream().filter(t -> t.id() == state.robberTile()).findFirst().orElseThrow();
        assertEquals(ResourceType.DESERT, robber.resource());
        assertEquals(DevelopmentState.START, state.development());
        assertEquals(Pieces.EMPTY, state.pieces());
        assertFalse(state.dice().rolled());
    }

    @Test
    void gameStateRejectsInvalidTurnInfo() {
        Board board = BoardGenerator.generate(new Random(3));
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(board, List.of(0, 1, 2), 0, GamePhase.PLAYING, 1, 1), "three players");
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(board, List.of(0, 1, 2, 3), 5, GamePhase.PLAYING, 1, 1), "unknown current player");
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(board, List.of(0, 1, 2, 3), 0, GamePhase.PLAYING, -1, 1), "negative turn");
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(board, List.of(0, 1, 2, 3), 0, GamePhase.PLAYING, 1, -1), "negative revision");
        assertThrows(IllegalArgumentException.class, () -> new GameState(board, List.of(0, 1, 2, 3), 0,
                GamePhase.PLAYING, 1, 1, Pieces.EMPTY, DiceResult.NONE, 99, DevelopmentState.START), "robber off board");
    }

    @Test
    void withTurnAdvancesRevisionAndClearsDiceAndTurnFlags() {
        Board board = BoardGenerator.generate(new Random(3));
        TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(3));
        for (int i = 0; i < 8; i++) turns.advance();
        DevelopmentState busy = new DevelopmentState(Map.of(), Map.of(), -1, 20, true, true, 0);
        GameState state = new GameState(board, turns.order(), turns.currentPlayerId(), turns.phase(),
                turns.turnNumber(), 10, Pieces.EMPTY, new DiceResult(3, 4), GameState.desertTile(board), busy);
        turns.advance();
        GameState next = state.withTurn(turns);
        assertEquals(11, next.revision());
        assertEquals(turns.currentPlayerId(), next.currentPlayerId());
        assertFalse(next.dice().rolled());
        assertFalse(next.development().playedThisTurn());
        assertFalse(next.development().movingRobber());
        assertEquals(20, next.development().deckSize(), "deck size carries over");
    }

    @Test
    void victoryPointsCountSettlementsAndLargestArmy() {
        Board board = BoardGenerator.generate(new Random(3));
        Pieces pieces = Pieces.EMPTY.withSettlement(0, 1).withSettlement(10, 1).withSettlement(20, 2);
        DevelopmentState dev = new DevelopmentState(Map.of(), Map.of(1, 3), 1, 25, false, false, 0);
        GameState state = new GameState(board, List.of(0, 1, 2, 3), 1, GamePhase.PLAYING, 1, 1, pieces,
                DiceResult.NONE, GameState.desertTile(board), dev);
        assertEquals(4, state.victoryPoints(1), "2 settlements + Largest Army");
        assertEquals(1, state.victoryPoints(2));
        assertEquals(0, state.victoryPoints(0));
    }

    @Test
    void piecesTrackCardsAndRefuseToOverspend() {
        Pieces p = Pieces.EMPTY.withCards(0, List.of(ResourceType.FOREST, ResourceType.FOREST, ResourceType.HILLS));
        assertTrue(p.hasCards(0, List.of(ResourceType.FOREST, ResourceType.FOREST)));
        assertFalse(p.hasCards(0, List.of(ResourceType.HILLS, ResourceType.HILLS)), "duplicates must count");
        assertEquals(List.of(ResourceType.FOREST), p.withoutCards(0, List.of(ResourceType.FOREST, ResourceType.HILLS)).hand(0));
        assertThrows(IllegalArgumentException.class, () -> p.withoutCards(0, List.of(ResourceType.MOUNTAINS)));
        assertEquals(List.of(), p.hand(3), "unknown player has an empty hand");
    }
}
