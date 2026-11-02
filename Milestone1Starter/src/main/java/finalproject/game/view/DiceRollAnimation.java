package finalproject.game.view;

import finalproject.game.DiceResult;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Reveals dice from underneath the lifting cup, followed by the grow/fade animation. */
public final class DiceRollAnimation extends StackPane {
    private static final int FRAME_WIDTH = 300, FRAME_HEIGHT = 366, COLUMNS = 8, FRAMES = 56;
    private static final double FPS = 20, LIFT_FPS = 10, CUP_WIDTH = 250, CUP_HEIGHT = 305;
    private static final double DICE_SIZE = 70, DICE_TOP = 30;
    // Lower rim of the cup in source-image pixels for lift frames 36–51.
    private static final int[] CUP_BOTTOM = {344,341,338,329,324,299,299,276,262,229,209,164,137,78,44,-1};
    private static final Image CUP_FRAMES = loadCup();
    private static final CupFrameClips CUP_CLIPS = CUP_FRAMES == null ? null
            : new CupFrameClips(CUP_FRAMES, COLUMNS, FRAME_WIDTH, FRAME_HEIGHT, FRAMES, CUP_WIDTH, CUP_HEIGHT);
    private Animation animation;
    private long generation;

    public DiceRollAnimation() {
        setId("dice-animation");
        setMouseTransparent(true);
        setVisible(false);
    }

    public void play(DiceResult result, Runnable onRevealed) {
        clear();
        setVisible(true);
        long run = generation;
        List<Rectangle> masks = new ArrayList<>();
        HBox dice = createDice(result, masks);
        boolean[] published = {false};
        Runnable publish = () -> {
            if (run == generation && !published[0]) {
                published[0] = true;
                onRevealed.run();
            }
        };
        if (CUP_FRAMES == null) {
            getChildren().add(dice);
            revealBelow(0, masks);
            publish.run();
            finishDice(dice, run);
            return;
        }
        ImageView cup = new ImageView(CUP_FRAMES);
        cup.setId("dice-cup");
        cup.setFitWidth(CUP_WIDTH);
        cup.setFitHeight(CUP_HEIGHT);
        cup.setPreserveRatio(true);
        cup.setViewport(frame(0));
        cup.setClip(CUP_CLIPS.frame(0));
        // The dice remain covered until the cup's lower rim rises above them.
        getChildren().addAll(cup, dice);
        Timeline shake = new Timeline();
        for (int i = 0; i < FRAMES; i++) {
            int index = i;
            shake.getKeyFrames().add(new KeyFrame(frameTime(i), e -> {
                if (run != generation) return;
                cup.setViewport(frame(index));
                cup.setClip(CUP_CLIPS.frame(index));
                int bottom = index < 36 ? FRAME_HEIGHT : index < 52 ? CUP_BOTTOM[index - 36] : -1;
                double uncoverY = (bottom + 4) * CUP_WIDTH / FRAME_WIDTH - CUP_HEIGHT / 2 - DICE_TOP;
                revealBelow(uncoverY, masks);
                if (uncoverY <= 0) publish.run();
            }));
        }
        shake.getKeyFrames().add(new KeyFrame(frameTime(FRAMES)));
        shake.setOnFinished(e -> {
            if (run != generation) return;
            getChildren().remove(cup);
            revealBelow(0, masks);
            publish.run();
            finishDice(dice, run);
        });
        animation = shake;
        shake.play();
    }

    private HBox createDice(DiceResult result, List<Rectangle> masks) {
        HBox dice = new HBox(10);
        dice.setAlignment(Pos.CENTER);
        dice.setMaxSize(180, 95);
        dice.setTranslateY(65);
        int index = 0;
        for (int value : new int[]{result.first(), result.second()}) {
            URL url = getClass().getResource("/RoseDice" + value + ".png");
            Node face;
            if (url == null) {
                Label number = new Label(Integer.toString(value));
                number.setPrefSize(DICE_SIZE, DICE_SIZE);
                number.setAlignment(Pos.CENTER);
                number.setStyle("-fx-font-size: 32px; -fx-text-fill: white;");
                face = number;
            } else {
                ImageView image = new ImageView(new Image(url.toExternalForm(), 140, 140, true, true));
                image.setFitWidth(DICE_SIZE);
                image.setFitHeight(DICE_SIZE);
                image.setPreserveRatio(true);
                face = image;
            }
            face.setId("dice-face-" + index++);
            Rectangle mask = new Rectangle(DICE_SIZE, 0);
            mask.setY(DICE_SIZE);
            face.setClip(mask);
            masks.add(mask);
            dice.getChildren().add(face);
        }
        return dice;
    }

    private static void revealBelow(double y, List<Rectangle> masks) {
        double top = Math.max(0, Math.min(DICE_SIZE, y));
        for (Rectangle mask : masks) {
            mask.setY(top);
            mask.setHeight(DICE_SIZE - top);
        }
    }

    private void finishDice(HBox dice, long run) {
        dice.setStyle("-fx-padding: 10;");
        TranslateTransition slide = new TranslateTransition(Duration.seconds(0.5), dice);
        slide.setToY(0);
        ScaleTransition grow = new ScaleTransition(Duration.seconds(1.5), dice);
        grow.setToX(2); grow.setToY(2);
        FadeTransition fade = new FadeTransition(Duration.seconds(1), dice);
        fade.setFromValue(1); fade.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(slide, grow,
                new PauseTransition(Duration.seconds(1.5)), fade);
        sequence.setOnFinished(e -> { if (run == generation) clear(); });
        animation = sequence;
        sequence.play();
    }

    public void clear() {
        generation++;
        if (animation != null) { animation.stop(); animation = null; }
        getChildren().clear();
        setVisible(false);
    }

    private static Rectangle2D frame(int index) {
        return new Rectangle2D((index % COLUMNS) * FRAME_WIDTH, (index / COLUMNS) * FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
    }

    private static Duration frameTime(int index) {
        return Duration.seconds(index < 36 ? index / FPS : 36 / FPS + (index - 36) / LIFT_FPS);
    }

    private static Image loadCup() {
        URL url = DiceRollAnimation.class.getResource("/dice/cup-shake.png");
        if (url == null) return null;
        Image image = new Image(url.toExternalForm());
        return image.isError() ? null : image;
    }
}
