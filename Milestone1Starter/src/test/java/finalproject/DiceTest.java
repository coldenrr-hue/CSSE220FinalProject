package finalproject;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiceTest {
    @Test
    void rollsAreAlwaysOneToSix() {
        Dice dice = new Dice();
        for (int i = 0; i < 10_000; i++) {
            int face = dice.roll();
            assertTrue(face >= 1 && face <= 6, "rolled " + face);
        }
    }

    @Test
    void everyFaceComesUpRoughlyEvenly() {
        Dice dice = new Dice();
        Map<Integer, Integer> counts = new HashMap<>();
        int rolls = 60_000;
        for (int i = 0; i < rolls; i++) counts.merge(dice.roll(), 1, Integer::sum);
        assertEquals(6, counts.size(), "faces seen: " + counts.keySet());
        // Expected 10,000 each; +/-1,000 is more than 10 standard deviations.
        counts.forEach((face, n) -> assertTrue(Math.abs(n - rolls / 6) < 1000, "face " + face + " came up " + n + " times"));
    }
}
