package finalproject.game;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's PRIVATE development cards. This is never part of GameState:
 * the host keeps every player's hand and sends each one only to its owner.
 *
 * ready: cards that can be played (bought on an earlier turn)
 * fresh: cards bought this turn. Catan doesn't let you play a card on the turn
 *        you bought it, so these move into ready when your turn ends.
 */
public record DevelopmentHand(List<DevelopmentCard> ready, List<DevelopmentCard> fresh) {

    public static final DevelopmentHand EMPTY = new DevelopmentHand(List.of(), List.of());

    public DevelopmentHand {
        ready = List.copyOf(ready);
        fresh = List.copyOf(fresh);
    }

    public int size() { return ready.size() + fresh.size(); }

    public int count(DevelopmentCard card) { return readyCount(card) + freshCount(card); }
    public int readyCount(DevelopmentCard card) { return (int) ready.stream().filter(c -> c == card).count(); }
    public int freshCount(DevelopmentCard card) { return (int) fresh.stream().filter(c -> c == card).count(); }

    /** Victory point cards count even on the turn they're bought. */
    public int victoryPoints() { return count(DevelopmentCard.VICTORY_POINT); }

    public DevelopmentHand withBought(DevelopmentCard card) {
        List<DevelopmentCard> f = new ArrayList<>(fresh);
        f.add(card);
        return new DevelopmentHand(ready, f);
    }

    /** Removes one ready copy of the card (the caller checks that there is one). */
    public DevelopmentHand withoutPlayed(DevelopmentCard card) {
        List<DevelopmentCard> r = new ArrayList<>(ready);
        if (!r.remove(card)) throw new IllegalArgumentException("You don't have a " + card.label() + " card to play.");
        return new DevelopmentHand(r, fresh);
    }

    /** At the end of your turn, the cards you bought become playable. */
    public DevelopmentHand endTurn() {
        List<DevelopmentCard> r = new ArrayList<>(ready);
        r.addAll(fresh);
        return new DevelopmentHand(r, List.of());
    }
}
