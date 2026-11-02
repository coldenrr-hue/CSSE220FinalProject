package finalproject;

import java.util.Random;

public class Dice {
    Random rand = new Random();

    public int roll() {
        return rand.nextInt(1, 7);
    }



}
