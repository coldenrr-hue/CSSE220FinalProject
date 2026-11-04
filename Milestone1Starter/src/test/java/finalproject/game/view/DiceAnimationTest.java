package finalproject.game.view;

import finalproject.game.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Real-time JavaFX regression check: shake, reveal timing, repeat snapshots and cancellation. */
public final class DiceAnimationTest extends Application {
    private final PlayingScreen screen = new PlayingScreen();
    private final Board board = BoardGenerator.generate(new Random(22));
    private final Map<Integer, String> names = Map.of(0,"Host",1,"Guest A",2,"Guest B",3,"Guest C");
    private long start, secondStart, cancelStart;
    private int phase;
    private boolean sampled, resent, partialReveal;
    private Stage stage;

    @Override public void start(Stage stage) {
        this.stage = stage;
        screen.onRollDice(() -> { });
        screen.onEndTurn(() -> { });
        stage.setScene(new Scene(screen, 1280, 800));
        stage.show();
        screen.render(game(1,1,DiceResult.NONE),0,names);
        screen.render(game(1,2,new DiceResult(2,5)),0,names);
        check(summary().equals("Rolling…"), "Result was exposed before shaking");
        check(endTurn().isDisabled(), "End turn enabled during shaking");
        start = System.nanoTime();
        Timeline clock = new Timeline(new KeyFrame(Duration.millis(50), event -> {
            try { tick(); }
            catch (Throwable failure) {
                failure.printStackTrace();
                screen.reset(); stage.close(); Platform.exit(); System.exit(1);
            }
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private void tick() {
        double elapsed = secondsSince(start);
        check(elapsed < 20, "Animation test timed out");
        if (phase == 0) {
            if (elapsed > 0.5 && !sampled) {
                ImageView cup = (ImageView) screen.lookup("#dice-cup");
                check(cup != null && (cup.getViewport().getMinX() > 0 || cup.getViewport().getMinY() > 0), "Cup frames did not advance");
                check(cup.getClip() != null && !cup.getClip().contains(0,0), "Cup matte is not transparent");
                check(cup.getClip().contains(125,150), "Removing the matte also removed the dark cup");
                Rectangle mask = (Rectangle) screen.lookup("#dice-face-0").getClip();
                check(mask.getHeight() == 0, "Dice appeared before the cup lifted");
                check(summary().equals("Rolling…"), "Result panel leaked the dice during cup playback");
                sampled = true;
            }
            Rectangle mask = (Rectangle) screen.lookup("#dice-face-0").getClip();
            if (mask.getHeight() > 0 && mask.getHeight() < 70) {
                check(screen.lookup("#dice-cup") != null, "Cup disappeared before the partial reveal");
                check(summary().equals("Rolling…"), "Panel result appeared before full uncover");
                check(endTurn().isDisabled(), "Turn unlocked before full uncover");
                partialReveal = true;
            }
            if (elapsed > 1 && !resent) {
                screen.render(game(1,3,new DiceResult(2,5)),0,names);
                check(summary().equals("Rolling…"), "Repeated snapshot bypassed reveal");
                resent = true;
            }
            if (summary().startsWith("Dice:")) {
                check(partialReveal, "Dice were not progressively uncovered");
                check(screen.lookup("#dice-cup") != null, "Result waited for the cup to disappear");
                check(elapsed >= 2.6 && elapsed < 3.8, "Reveal did not overlap the cup lift");
                check(summary().equals("Dice: 2 + 5 = 7"), "Wrong first result");
                check(!endTurn().isDisabled(), "End turn did not unlock after reveal");
                screen.render(game(2,4,DiceResult.NONE),0,names);
                screen.render(game(2,5,new DiceResult(6,1)),0,names);
                check(summary().equals("Rolling…"), "Second roll showed old values");
                secondStart = System.nanoTime();
                phase = 1;
            }
        } else if (phase == 1 && secondsSince(secondStart) > 4.9) {
            check(summary().equals("Dice: 6 + 1 = 7"), "Second result missing");
            check(screen.lookup("#dice-animation").isVisible(), "Old fade cleared the newer roll");
            screen.render(game(3,6,DiceResult.NONE),0,names);
            screen.render(game(3,7,new DiceResult(3,4)),0,names);
            cancelStart = System.nanoTime();
            phase = 2;
        } else if (phase == 2 && secondsSince(cancelStart) > 0.5) {
            check(summary().equals("Rolling…"), "Third roll did not start shaking");
            screen.reset();
            cancelStart = System.nanoTime();
            phase = 3;
        } else if (phase == 3 && secondsSince(cancelStart) > 3.5) {
            check(summary().equals("Roll to begin your turn."), "Cancelled roll published stale numbers");
            check(!screen.lookup("#dice-animation").isVisible(), "Cancelled animation still visible");
            System.out.println("PASS: cup shake, progressive uncover during lift, result timing, repeat snapshots, consecutive rolls and cancellation");
            stage.close(); Platform.exit();
        }
    }

    private GameState game(int turn, long revision, DiceResult dice) {
        return new GameState(board,List.of(0,1,2,3),0,GamePhase.PLAYING,turn,revision,Pieces.EMPTY,dice);
    }
    private String summary() { return ((Label)screen.lookup("#dice-summary")).getText(); }
    private Button endTurn() {
        return screen.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("End turn"))
                .map(n -> (Button)n).findFirst().orElseThrow();
    }
    private static double secondsSince(long time) { return (System.nanoTime()-time)/1_000_000_000.0; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) { launch(args); }
}
