package finalproject.network;

import static org.junit.jupiter.api.Assertions.*;

import finalproject.game.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Every laptop rebuilds the game from GAME_STATE, so the codec has to be exact. */
class GameStateCodecTest {

    private static GameState roundTrip(GameState state) {
        return GameStateCodec.decode(List.of(GameStateCodec.encode(state)));
    }

    @Test
    void roundTripsFreshGamesOnManyBoards() {
        for (int seed = 0; seed < 200; seed++) {
            Board board = BoardGenerator.generate(new Random(seed));
            TurnManager turns = new TurnManager(List.of(0, 1, 2, 3), new Random(seed));
            for (int i = 0; i < 12; i++) {
                GameState state = new GameState(board, turns.order(), turns.currentPlayerId(), turns.phase(),
                        turns.turnNumber(), i);
                assertEquals(state, roundTrip(state), "seed " + seed + " step " + i);
                turns.advance();
            }
        }
    }

    @Test
    void roundTripsAFullyPopulatedState() {
        Board board = BoardGenerator.generate(new Random(42));
        Pieces pieces = Pieces.EMPTY.withSettlement(3, 0).withSettlement(30, 2).withRoad(5, 0).withRoad(40, 2)
                .withSettlement(12, 1)                      // pending settlement 12
                .withCards(0, List.of(ResourceType.FOREST, ResourceType.FOREST, ResourceType.MOUNTAINS))
                .withHand(3, List.of());                    // a player with an empty hand
        int robber = board.tiles().stream().filter(t -> t.resource() != ResourceType.DESERT).findFirst().orElseThrow().id();
        DevelopmentState dev = new DevelopmentState(Map.of(0, 2, 1, 1), Map.of(0, 3, 2, 1), 0, 19, true, true, 1);
        GameState state = new GameState(board, List.of(2, 0, 3, 1), 0, GamePhase.PLAYING, 17, 123_456_789_012L,
                pieces, new DiceResult(6, 6), robber, dev);
        GameState decoded = roundTrip(state);
        assertEquals(state, decoded);
        assertEquals(12, decoded.pieces().pendingSettlement());
        assertEquals(List.of(), decoded.pieces().hand(3));
        assertEquals(robber, decoded.robberTile());
    }

    @Test
    void rejectsMalformedSnapshots() {
        String[] good = GameStateCodec.encode(new GameState(BoardGenerator.generate(new Random(1)), List.of(0, 1, 2, 3),
                0, GamePhase.PLAYING, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(List.of("GAME_STATE", "bogus")));

        List<String> wrongVersion = new ArrayList<>(List.of(good));
        wrongVersion.set(1, "3");
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(wrongVersion));

        List<String> wrongTag = new ArrayList<>(List.of(good));
        wrongTag.set(0, "STATE");
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(wrongTag));

        List<String> extraField = new ArrayList<>(List.of(good));
        extraField.add("x");
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(extraField));

        List<String> badNumber = new ArrayList<>(List.of(good));
        badNumber.set(2, "not-a-number");
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(badNumber));
    }

    @Test
    void privateHandRoundTrips() {
        DevelopmentHand hand = new DevelopmentHand(
                List.of(DevelopmentCard.KNIGHT, DevelopmentCard.KNIGHT, DevelopmentCard.MONOPOLY),
                List.of(DevelopmentCard.VICTORY_POINT));
        String[] encoded = GameStateCodec.encodeHand(9, hand);
        assertEquals("DEV_CARDS", encoded[0]);
        assertEquals("9", encoded[1]);
        assertEquals(hand, GameStateCodec.decodeHand(List.of(encoded)));
        assertEquals(DevelopmentHand.EMPTY,
                GameStateCodec.decodeHand(List.of(GameStateCodec.encodeHand(0, DevelopmentHand.EMPTY))));
    }

    @Test
    void rejectsMalformedHands() {
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decodeHand(List.of("DEV_CARDS", "1", "")));
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decodeHand(List.of("GAME_STATE", "1", "", "")));
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decodeHand(List.of("DEV_CARDS", "1", "DRAGON", "")));
    }
}
