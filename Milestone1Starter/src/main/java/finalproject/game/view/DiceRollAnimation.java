package finalproject.game.view;

import finalproject.game.DiceResult;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.net.URL;

/** George's cup, wipe, grow and fade animation, driven by the host's dice result. */
public final class DiceRollAnimation extends StackPane {
    private MediaPlayer player;
    private Animation animation;

    public DiceRollAnimation() {
        setId("dice-animation");
        setMouseTransparent(true);
        setVisible(false);
    }

    public void play(DiceResult result) {
        clear();
        setVisible(true);
        URL cup = getClass().getResource("/DiceCup.mp4");
        if (cup != null) {
            try {
                MediaPlayer mediaPlayer = new MediaPlayer(new Media(cup.toExternalForm()));
                player = mediaPlayer;
                MediaView video = new MediaView(mediaPlayer);
                video.setFitWidth(250);
                video.setPreserveRatio(true);
                getChildren().add(video);
                Runnable dispose = () -> {
                    getChildren().remove(video);
                    mediaPlayer.dispose();
                    if (player == mediaPlayer) player = null;
                };
                mediaPlayer.setOnEndOfMedia(dispose);
                mediaPlayer.setOnError(dispose);
                mediaPlayer.play();
            } catch (RuntimeException e) {
                if (player != null) { player.dispose(); player = null; }
                // Keep the shared result usable if this laptop cannot play the video.
            }
        }
        PauseTransition reveal = new PauseTransition(Duration.seconds(cup == null ? 0 : 4));
        reveal.setOnFinished(e -> reveal(result));
        animation = reveal;
        reveal.play();
    }

    private void reveal(DiceResult result) {
        HBox dice = new HBox(10);
        dice.setAlignment(Pos.CENTER);
        dice.setMaxSize(180, 95);
        dice.setStyle("-fx-background-color: rgba(18,32,44,0.92); -fx-background-radius: 14; -fx-padding: 10;");
        ParallelTransition wipes = new ParallelTransition();
        for (int value : new int[]{result.first(), result.second()}) {
            URL url = getClass().getResource("/RoseDice" + value + ".png");
            if (url == null) { dice.getChildren().add(new Label(Integer.toString(value))); continue; }
            ImageView face = new ImageView(new Image(url.toExternalForm(), 140, 140, true, true));
            face.setFitWidth(70);
            face.setFitHeight(70);
            face.setPreserveRatio(true);
            Rectangle clip = new Rectangle(70, 0);
            clip.setY(70);
            face.setClip(clip);
            dice.getChildren().add(face);
            wipes.getChildren().add(new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(clip.yProperty(),70), new KeyValue(clip.heightProperty(),0)),
                    new KeyFrame(Duration.millis(500), new KeyValue(clip.yProperty(),0), new KeyValue(clip.heightProperty(),70))));
        }
        dice.setTranslateY(65);
        getChildren().add(dice);
        TranslateTransition slide = new TranslateTransition(Duration.seconds(0.5), dice);
        slide.setToY(0);
        ScaleTransition grow = new ScaleTransition(Duration.seconds(1.5), dice);
        grow.setToX(2); grow.setToY(2);
        FadeTransition fade = new FadeTransition(Duration.seconds(1), dice);
        fade.setFromValue(1); fade.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(wipes, slide, grow,
                new PauseTransition(Duration.seconds(1.5)), fade);
        sequence.setOnFinished(e -> clear());
        animation = sequence;
        sequence.play();
    }

    public void clear() {
        if (animation != null) { animation.stop(); animation = null; }
        if (player != null) { player.dispose(); player = null; }
        getChildren().clear();
        setVisible(false);
    }
}
