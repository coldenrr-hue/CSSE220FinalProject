package finalproject;

import java.util.Random;

/** Rolls a six-sided die. The host rolls it twice to generate the turn's shared result. */
public class Dice {
    private final Random random = new Random();

    public int roll() {
        return random.nextInt(1, 7);
    }
}
