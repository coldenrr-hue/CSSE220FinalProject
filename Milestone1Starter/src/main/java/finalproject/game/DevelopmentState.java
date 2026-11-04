package finalproject.game;

import java.util.HashMap;
import java.util.Map;

/**
 * The PUBLIC side of development cards: what everyone at a real table can see.
 * Which cards a player holds is private (see DevelopmentHand); only how many is public.
 *
 * cardCounts:      player ID -> how many unplayed development cards they hold
 * knights:         player ID -> knights played (their army)
 * largestArmy:     player holding Largest Army (+2 VP), or -1
 * deckSize:        cards left to buy
 * playedThisTurn:  the current player has already played a card (limit is one per turn)
 * movingRobber:    a knight was just played; the current player must move the robber
 * freeRoads:       Road Building roads the current player still has to place
 */
public record DevelopmentState(Map<Integer, Integer> cardCounts, Map<Integer, Integer> knights,
                               int largestArmy, int deckSize, boolean playedThisTurn,
                               boolean movingRobber, int freeRoads) {

    public static final DevelopmentState START =
            new DevelopmentState(Map.of(), Map.of(), -1, DevelopmentCard.DECK_SIZE, false, false, 0);

    public DevelopmentState {
        cardCounts = Map.copyOf(cardCounts);
        knights = Map.copyOf(knights);
        if (deckSize < 0 || deckSize > DevelopmentCard.DECK_SIZE || freeRoads < 0 || freeRoads > 2
                || largestArmy < -1 || largestArmy > 3) {
            throw new IllegalArgumentException("Invalid development card state");
        }
    }

    public int cardCount(int playerId) { return cardCounts.getOrDefault(playerId, 0); }
    public int knights(int playerId) { return knights.getOrDefault(playerId, 0); }

    /** True while a played card still needs the player to finish it (robber or free roads). */
    public boolean busy() { return movingRobber || freeRoads > 0; }

    DevelopmentState withBought(int playerId) {
        return new DevelopmentState(adjust(cardCounts, playerId, 1), knights, largestArmy, deckSize - 1,
                playedThisTurn, movingRobber, freeRoads);
    }

    DevelopmentState withPlayed(int playerId) {
        return new DevelopmentState(adjust(cardCounts, playerId, -1), knights, largestArmy, deckSize,
                true, movingRobber, freeRoads);
    }

    DevelopmentState withKnight(int playerId, int newLargestArmy) {
        return new DevelopmentState(cardCounts, adjust(knights, playerId, 1), newLargestArmy, deckSize,
                playedThisTurn, true, freeRoads);
    }

    DevelopmentState withRobberMoved() {
        return new DevelopmentState(cardCounts, knights, largestArmy, deckSize, playedThisTurn, false, freeRoads);
    }

    DevelopmentState withFreeRoads(int roads) {
        return new DevelopmentState(cardCounts, knights, largestArmy, deckSize, playedThisTurn, movingRobber, roads);
    }

    /** The next player starts with no card played. */
    public DevelopmentState nextTurn() {
        return new DevelopmentState(cardCounts, knights, largestArmy, deckSize, false, false, 0);
    }

    private static Map<Integer, Integer> adjust(Map<Integer, Integer> map, int playerId, int delta) {
        Map<Integer, Integer> copy = new HashMap<>(map);
        copy.merge(playerId, delta, Integer::sum);
        return copy;
    }
}
