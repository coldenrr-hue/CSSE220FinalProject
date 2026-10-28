package finalproject.game;

import finalproject.network.GameStateCodec;
import java.util.*;
import java.util.stream.Collectors;

/** Standalone deterministic checks; throws on failure even without -ea. */
public final class GameModelTest {
    public static void main(String[] args) {
        Set<Board> boards = new HashSet<>();
        Map<ResourceType, Long> expectedResources = Map.of(ResourceType.FOREST, 4L, ResourceType.PASTURE, 4L,
                ResourceType.FIELDS, 4L, ResourceType.HILLS, 3L, ResourceType.MOUNTAINS, 3L, ResourceType.DESERT, 1L);
        List<Integer> expectedNumbers = List.of(0, 2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12);
        for (int seed = 0; seed < 2000; seed++) {
            Board board = BoardGenerator.generate(new Random(seed));
            boards.add(board);
            check(board.equals(BoardGenerator.generate(new Random(seed))), "Seed reproducibility");
            check(board.tiles().stream().collect(Collectors.groupingBy(Tile::resource, Collectors.counting())).equals(expectedResources), "Resource counts");
            check(board.tiles().stream().map(Tile::number).sorted().toList().equals(expectedNumbers), "Token counts");
            for (Tile a : board.tiles()) for (Tile b : board.tiles()) {
                check(!(a.hasRedNumber() && b.hasRedNumber() && a.isAdjacentTo(b)), "Adjacent red tokens");
            }
            TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(seed));
            List<Integer> order = turns.order();
            List<Integer> setup = new ArrayList<>(order);
            List<Integer> reverse = new ArrayList<>(order);
            Collections.reverse(reverse);
            setup.addAll(reverse);
            for (int i = 0; i < 8; i++) {
                check(turns.currentPlayerId() == setup.get(i), "Setup snake order");
                check(turns.phase() == (i < 4 ? GamePhase.SETUP_FORWARD : GamePhase.SETUP_REVERSE), "Setup phase");
                turns.advance();
            }
            for (int i = 0; i < 12; i++) {
                check(turns.phase() == GamePhase.PLAYING && turns.currentPlayerId() == order.get(i % 4)
                        && turns.turnNumber() == i + 1, "Normal turn cycle");
                GameState snapshot = new GameState(board, order, turns.currentPlayerId(), turns.phase(), turns.turnNumber(), i);
                check(snapshot.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(snapshot)))), "Codec round trip");
                turns.advance();
            }
        }
        check(boards.size() > 1900, "Board variation");
        try {
            GameStateCodec.decode(List.of("GAME_STATE", "bogus"));
            throw new AssertionError("Malformed snapshot accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("PASS: 2000 seeded boards, terrain/token counts, red-token separation, codec, setup and normal turns");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
