package finalproject.game;

import finalproject.network.GameStateCodec;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Exercises discard thresholds, card ownership, turn restrictions, and robber theft after a seven.
 * Runs independently of JavaFX and the LAN lobby.
 */
public final class SevenResolutionTest {
    public static void main(String[] args) {
        Board board = BoardGenerator.generate(new Random(1));
        int desert = GameState.desertTile(board);
        BoardGraph.Vertex victimVertex = BoardGraph.of(board).vertices().stream()
                .filter(vertex -> vertex.tileIds().stream().anyMatch(id -> id != desert))
                .findFirst().orElseThrow();
        int destination = victimVertex.tileIds().stream().filter(id -> id != desert).findFirst().orElseThrow();
        PlayerAssets assets = new PlayerAssets(Map.of(victimVertex.id(), 1), Map.of(), -1, Map.of(
                0, cards(6, ResourceType.FOREST), 1, cards(7, ResourceType.HILLS),
                2, cards(8, ResourceType.FIELDS), 3, cards(9, ResourceType.PASTURE))).withCity(victimVertex.id());
        GameState rolled = new GameState(board, List.of(0, 1, 2, 3), 0, GamePhase.PLAYING, 1, 10,
                assets, new DiceResult(3, 4));
        SevenResolution seven = SevenResolution.start(rolled);
        GameState waiting = rolled.with(assets, desert, rolled.development(), seven);

        check(seven.pendingDiscards().equals(Map.of(1, 3, 2, 4, 3, 4)), "Thresholds or rounding are wrong");
        check(waiting.equals(GameStateCodec.decode(List.of(GameStateCodec.encode(waiting)))), "Seven state did not round-trip");
        reject(() -> seven.discard(waiting, 0, List.of()));
        reject(() -> seven.discard(waiting, 1, cards(2, ResourceType.HILLS)));
        reject(() -> seven.discard(waiting, 1, cards(3, ResourceType.FOREST)));
        reject(() -> DevelopmentRules.moveRobber(waiting, 0, destination, 1, new Random(1)));
        reject(() -> GameRules.bankTrade(waiting, 0, ResourceType.FOREST, ResourceType.HILLS, 1));
        check(GameRules.legalBuilds(waiting, 0, GameRules.Build.ROAD).isEmpty(), "Building is allowed mid-seven");
        check(DevelopmentRules.playProblem(waiting, DevelopmentHand.EMPTY, 0, DevelopmentCard.KNIGHT)
                .contains("seven"), "Development card play is allowed mid-seven");

        GameState afterFirst = seven.discard(waiting, 1, cards(3, ResourceType.HILLS));
        reject(() -> afterFirst.seven().discard(afterFirst, 1, cards(3, ResourceType.HILLS)));
        GameState afterSecond = afterFirst.seven().discard(afterFirst, 2, cards(4, ResourceType.FIELDS));
        GameState ready = afterSecond.seven().discard(afterSecond, 3, cards(4, ResourceType.PASTURE));
        check(ready.seven().readyToMoveRobber() && ready.seven().rollRevision() == seven.rollRevision(),
                "All players must be able to use the same roll revision");
        check(GameRules.bankStock(ready.assets(), ResourceType.HILLS) == 15, "Discards did not return to the bank");
        reject(() -> DevelopmentRules.moveRobber(ready, 1, destination, -1, new Random(1)));
        reject(() -> DevelopmentRules.moveRobber(ready, 0, desert, -1, new Random(1)));
        GameState moved = DevelopmentRules.moveRobber(ready, 0, destination, 1, new Random(1));
        check(moved.robberTile() == destination && !moved.seven().active(), "Robber did not resolve the seven");
        check(moved.assets().hand(0).contains(ResourceType.HILLS) && moved.assets().hand(1).size() == 3,
                "Exactly one card should move from the city owner to the roller");
        check(moved.development().equals(rolled.development()), "Rolling a seven should not use a development card");
        reject(() -> DevelopmentRules.moveRobber(moved, 0, desert, -1, new Random(1)));
        System.out.println("PASS: seven discards, bank, blocked actions, robber, and city victim");
    }

    private static List<ResourceType> cards(int count, ResourceType resource) {
        return Collections.nCopies(count, resource);
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid action was accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
