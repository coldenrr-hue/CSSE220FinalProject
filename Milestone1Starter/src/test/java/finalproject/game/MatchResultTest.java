package finalproject.game;

import finalproject.network.GameStateCodec;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Standalone checks for the finish threshold and the public results message. */
public final class MatchResultTest {
    public static void main(String[] args) {
        Board board = BoardGenerator.generate(new Random(6));
        PlayerAssets assets = PlayerAssets.EMPTY;
        for (int vertex = 0; vertex < 5; vertex++) {
            assets = assets.withSettlement(vertex, 2);
            if (vertex < 4) assets = assets.withCity(vertex);
        }
        assets = assets.withRoad(0, 2).withCards(2, DevelopmentRules.COST);
        GameState game = new GameState(board, List.of(2, 1, 3, 0), 2, GamePhase.PLAYING, 30, 100,
                assets, new DiceResult(2, 3));
        if (game.victoryPoints(2) != 9 || MatchResult.ifWon(game, Map.of(), Map.of()) != null) {
            throw new AssertionError("Nine points must keep the match running");
        }

        DevelopmentRules.Result purchase = DevelopmentRules.buy(game, DevelopmentHand.EMPTY, 2,
                DevelopmentCard.VICTORY_POINT);
        Map<Integer, DevelopmentHand> hands = Map.of(
                2, purchase.hand(),
                3, DevelopmentHand.EMPTY.withBought(DevelopmentCard.MONOPOLY));
        MatchResult result = MatchResult.ifWon(purchase.state(), hands, Map.of(2, 3, 3, 1));
        MatchResult.PlayerStats expected = new MatchResult.PlayerStats(2, 10, 3, 0, 1, 4, 1);
        if (result == null || !result.winner().equals(expected) || result.turns() != 30
                || result.revision() != purchase.state().revision()
                || !result.players().getFirst().equals(expected)) {
            throw new AssertionError("A freshly bought victory point card must count toward the win and final standings");
        }
        List<String> message = List.of(GameStateCodec.encodeResult(result));
        if (message.stream().anyMatch(s -> s.equals("VICTORY_POINT") || s.equals("MONOPOLY"))
                || !result.equals(GameStateCodec.decodeResult(message))) {
            throw new AssertionError("Results must round-trip without revealing the contents of hands");
        }
        System.out.println("PASS: finish at ten on purchase, private victory points, standings and results codec");
    }
}
