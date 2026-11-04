package finalproject.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Host-owned turn order. Setup goes A,B,C,D,D,C,B,A, then normal play A,B,C,D. */
public final class TurnManager {
    private final List<Integer> order;
    private int index;
    private GamePhase phase = GamePhase.SETUP_FORWARD;
    private int turnNumber;

    public TurnManager(List<Integer> players, Random random) {
        if (players.size() != 4 || !new HashSet<>(players).equals(java.util.Set.of(0, 1, 2, 3))) {
            throw new IllegalArgumentException("Four distinct player seats are required");
        }
        List<Integer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);
        order = List.copyOf(shuffled);
    }

    public List<Integer> order() { return order; }
    public int currentPlayerId() { return order.get(index); }
    public GamePhase phase() { return phase; }
    public int turnNumber() { return turnNumber; }

    public void advance() {
        switch (phase) {
            case SETUP_FORWARD -> {
                if (index == order.size() - 1) phase = GamePhase.SETUP_REVERSE;
                else index++;
            }
            case SETUP_REVERSE -> {
                if (index == 0) { phase = GamePhase.PLAYING; turnNumber = 1; }
                else index--;
            }
            case PLAYING -> { index = (index + 1) % order.size(); turnNumber++; }
        }
    }
}
