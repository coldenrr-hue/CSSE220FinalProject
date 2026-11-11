package finalproject.game.view;

import static org.junit.jupiter.api.Assertions.*;

import finalproject.game.*;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Real-time JavaFX check of the dice roll: cup shake, reveal timing, repeated
 * snapshots, back-to-back rolls and cancellation. Opens a window for ~12 seconds.
 * Skip it with: mvn test -DexcludedGroups=ui
 */
@Tag("ui")
class DiceAnimationTest {
    private final Board board = BoardGenerator.generate(new Random(22));
    private final Map<Integer, String> names = Map.of(0, "Host", 1, "Guest A", 2, "Guest B", 3, "Guest C");

    @BeforeAll
    static void startJavaFx() {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) { }
        Platform.setImplicitExit(false);
    }

    @Test
    void diceRollAnimationSequence() throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                new Run(result).start();
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            result.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
    }

    /** One run of the animation, driven by a 50 ms clock on the JavaFX thread. */
    private final class Run {
        private final CompletableFuture<Void> result;
        private final PlayingScreen screen = new PlayingScreen();
        private final Stage stage = new Stage();
        private Timeline clock;
        private long start, secondStart, cancelStart;
        private int phase;
        private boolean sampled, resent, partialReveal;

        Run(CompletableFuture<Void> result) { this.result = result; }

        void start() {
            screen.onRollDice(() -> { });
            screen.onEndTurn(() -> { });
            stage.setScene(new Scene(screen, 1280, 800));
            stage.show();
            screen.render(game(1, 1, DiceResult.NONE), 0, names);
            assertEquals("Roll to begin your turn.", summary());
            screen.render(game(1, 2, new DiceResult(2, 5)), 0, names);
            assertEquals("Rolling…", summary(), "result exposed before shaking");
            assertTrue(endTurn().isDisabled(), "End turn enabled during shaking");
            start = System.nanoTime();
            clock = new Timeline(new KeyFrame(Duration.millis(50), event -> {
                try {
                    tick();
                } catch (Throwable failure) {
                    finish(failure);
                }
            }));
            clock.setCycleCount(Timeline.INDEFINITE);
            clock.play();
        }

        private void finish(Throwable failure) {
            clock.stop();
            screen.reset();
            stage.close();
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        }

        private void tick() {
            double elapsed = secondsSince(start);
            assertTrue(elapsed < 20, "animation test timed out in phase " + phase);
            if (phase == 0) {
                if (elapsed > 0.5 && !sampled) {
                    ImageView cup = (ImageView) screen.lookup("#dice-cup");
                    assertNotNull(cup, "cup missing while shaking");
                    assertTrue(cup.getViewport().getMinX() > 0 || cup.getViewport().getMinY() > 0, "cup frames did not advance");
                    assertNotNull(cup.getClip(), "cup has no matte clip");
                    assertFalse(cup.getClip().contains(0, 0), "cup matte is not transparent");
                    assertTrue(cup.getClip().contains(125, 150), "removing the matte also removed the cup");
                    assertEquals(0, faceMask().getHeight(), "dice appeared before the cup lifted");
                    assertEquals("Rolling…", summary(), "result panel leaked the dice during cup playback");
                    sampled = true;
                }
                double mask = faceMask().getHeight();
                if (mask > 0 && mask < 70) {
                    assertNotNull(screen.lookup("#dice-cup"), "cup disappeared before the partial reveal");
                    assertEquals("Rolling…", summary(), "result appeared before full uncover");
                    assertTrue(endTurn().isDisabled(), "turn unlocked before full uncover");
                    partialReveal = true;
                }
                if (elapsed > 1 && !resent) {
                    screen.render(game(1, 3, new DiceResult(2, 5)), 0, names);
                    assertEquals("Rolling…", summary(), "repeated snapshot bypassed the reveal");
                    resent = true;
                }
                if (summary().startsWith("Dice:")) {
                    assertTrue(sampled, "result shown before the cup was sampled");
                    assertTrue(partialReveal, "dice were not progressively uncovered");
                    assertNotNull(screen.lookup("#dice-cup"), "result waited for the cup to disappear");
                    assertTrue(elapsed >= 2.6 && elapsed < 3.8, "reveal at " + elapsed + "s did not overlap the cup lift");
                    assertEquals("Dice: 2 + 5 = 7", summary());
                    assertFalse(endTurn().isDisabled(), "End turn did not unlock after reveal");
                    screen.render(game(2, 4, DiceResult.NONE), 0, names);
                    screen.render(game(2, 5, new DiceResult(6, 1)), 0, names);
                    assertEquals("Rolling…", summary(), "second roll showed old values");
                    secondStart = System.nanoTime();
                    phase = 1;
                }
            } else if (phase == 1 && secondsSince(secondStart) > 4.9) {
                assertEquals("Dice: 6 + 1 = 7", summary(), "second result missing");
                assertTrue(screen.lookup("#dice-animation").isVisible(), "old fade cleared the newer roll");
                screen.render(game(3, 6, DiceResult.NONE), 0, names);
                screen.render(game(3, 7, new DiceResult(3, 4)), 0, names);
                cancelStart = System.nanoTime();
                phase = 2;
            } else if (phase == 2 && secondsSince(cancelStart) > 0.5) {
                assertEquals("Rolling…", summary(), "third roll did not start shaking");
                screen.reset();
                cancelStart = System.nanoTime();
                phase = 3;
            } else if (phase == 3 && secondsSince(cancelStart) > 3.5) {
                assertEquals("Roll to begin your turn.", summary(), "cancelled roll published stale numbers");
                assertFalse(screen.lookup("#dice-animation").isVisible(), "cancelled animation still visible");
                finish(null);
            }
        }

        private Rectangle faceMask() { return (Rectangle) screen.lookup("#dice-face-0").getClip(); }
        private String summary() { return ((Label) screen.lookup("#dice-summary")).getText(); }
        private Button endTurn() {
            return screen.lookupAll(".button").stream()
                    .filter(n -> n instanceof Button b && b.getText().equals("End turn"))
                    .map(n -> (Button) n).findFirst().orElseThrow();
        }
    }

    private GameState game(int turn, long revision, DiceResult dice) {
        return new GameState(board, List.of(0, 1, 2, 3), 0, GamePhase.PLAYING, turn, revision, Pieces.EMPTY, dice);
    }

    private static double secondsSince(long time) { return (System.nanoTime() - time) / 1_000_000_000.0; }
}
