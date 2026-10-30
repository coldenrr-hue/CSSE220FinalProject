package finalproject;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;


/**
 - Starter JavaFX app for CSSE220 Milestone 1.


 - Replace this simple window with the first version of your project.


 */
public class StarterApp extends Application {

    @Override
    public void start(Stage stage) {
        // TODO 1: Choose a simple app title and create a small root layout.
        Label titleLabel = new Label("CSSE220 Final Project");
        Label statusLabel = new Label("Starter app is ready.");

        // TODO 2: Add your first interactive control (button, text field, etc.).
        Button actionButton = new Button("Tap to update status");
        actionButton.setOnAction(event -> {
            statusLabel.setText("Your team has launched the shared starter.");
        });

        // TODO 69: Dice roll functionality setup.
        Dice die1 = new Dice();
        Dice die2 = new Dice();
        Button diceButton = new Button("Roll the dice");

        // ==========================================
        // LAYOUT & SCREEN ORGANIZATION (Overlay & Centering)
        // ==========================================
        // We use a StackPane as the central canvas so elements can overlay each other
        // without causing layout shifts when items are removed.
        StackPane centerCanvas = new StackPane();
        centerCanvas.setAlignment(Pos.CENTER);

        VBox controlsBox = new VBox(12, titleLabel, statusLabel, actionButton, diceButton);
        controlsBox.setPadding(new Insets(20));
        controlsBox.setAlignment(Pos.CENTER);

        // Main root layout combining top controls and the center overlay canvas
        VBox root = new VBox(20, controlsBox, centerCanvas);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        diceButton.setOnAction(event -> {
            int roll1 = die1.roll();
            int roll2 = die2.roll();
            int total = roll1 + roll2;

            statusLabel.setText("You rolled a " + roll1 + " and a " + roll2 + "!" +
                    " That is a total of " + total + "!");

            // Clear any existing overlay content on re-roll
            centerCanvas.getChildren().clear();

            // --- Video & Overlay Reveal Logic ---
            try {
                java.net.URL videoUrl = getClass().getResource("/DiceCup.mp4");
                String videoPath = videoUrl != null
                        ? videoUrl.toExternalForm()
                        : new java.io.File("src/main/resources/DiceCup.mp4").toURI().toString();

                Media media = new Media(videoPath);
                MediaPlayer mediaPlayer = new MediaPlayer(media);
                MediaView mediaView = new MediaView(mediaPlayer);

                // Requirement 1: Scaled animation down to half size (250 width) while remaining centered
                mediaView.setFitWidth(250);
                mediaView.setPreserveRatio(true);
                mediaPlayer.setCycleCount(1);

                // Clean up video when it fully finishes
                mediaPlayer.setOnEndOfMedia(() -> {
                    centerCanvas.getChildren().remove(mediaView);
                    mediaPlayer.dispose();
                });

                // Add video view to the center canvas (Layer 1)
                centerCanvas.getChildren().add(mediaView);
                mediaPlayer.play();

                // Requirement 4: Trigger the Wipe Reveal after a 4-second delay
                PauseTransition revealTimer = new PauseTransition(Duration.seconds(4));
                revealTimer.setOnFinished(e -> {
                    int[] rolls = {roll1, roll2};
                    ImageView[] diceViews = new ImageView[2];
                    Rectangle[] clips = new Rectangle[2];
                    Timeline[] wipes = new Timeline[2];

                    double diceSize = 70;

                    for (int i = 0; i < 2; i++) {
                        Image img = new Image(getClass().getResourceAsStream("/RoseDice" + rolls[i] + ".png"));
                        diceViews[i] = new ImageView(img);
                        diceViews[i].setFitWidth(diceSize);
                        diceViews[i].setFitHeight(diceSize);
                        diceViews[i].setPreserveRatio(true);

                        clips[i] = new Rectangle(diceSize, 0);
                        clips[i].setY(diceSize);
                        diceViews[i].setClip(clips[i]);

                        wipes[i] = new Timeline(
                                new KeyFrame(Duration.ZERO,
                                        new KeyValue(clips[i].yProperty(), diceSize),
                                        new KeyValue(clips[i].heightProperty(), 0)),
                                new KeyFrame(Duration.millis(500),
                                        new KeyValue(clips[i].yProperty(), 0),
                                        new KeyValue(clips[i].heightProperty(), diceSize))
                        );
                    }

                    // Put dice side-by-side in an HBox
                    HBox diceBox = new HBox(10, diceViews[0], diceViews[1]);
                    diceBox.setAlignment(Pos.CENTER);

                    // Requirement 2: Position dice so they appear slightly above the bottom of the animation area
                    StackPane.setAlignment(diceBox, Pos.BOTTOM_CENTER);
                    diceBox.setTranslateY(65);

                    // Requirement 5: Motion Path (Line effect) to move dice from initial location to the center of window screen
                    TranslateTransition slideToCenter = new TranslateTransition(Duration.seconds(0.5), diceBox);
                    slideToCenter.setToX(0);
                    slideToCenter.setToY(0);

                    // Scale the entire HBox container to 2.0x so spacing and centering are perfectly preserved
                    ScaleTransition growBox = new ScaleTransition(Duration.seconds(1.5), diceBox);
                    growBox.setToX(2.0);
                    growBox.setToY(2.0);

                    // Pause before fading out (1.5 seconds)
                    PauseTransition pauseBeforeFade = new PauseTransition(Duration.seconds(1.5));

                    // Fade Out Effect (PowerPoint-style fade to transparent over 1 second)
                    FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.0), diceBox);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    // Remove from layout entirely once the fade-out completes
                    fadeOut.setOnFinished(fEvent -> centerCanvas.getChildren().remove(diceBox));

                    // Sequence chain: Wipe finishes -> Slide to center -> Container grow -> Pause -> Fade out
                    wipes[0].setOnFinished(wEvent -> slideToCenter.play());
                    slideToCenter.setOnFinished(sEvent -> growBox.play());
                    growBox.setOnFinished(gEvent -> pauseBeforeFade.play());
                    pauseBeforeFade.setOnFinished(pEvent -> fadeOut.play());

                    // Add dice HBox to the same StackPane canvas (Layer 2 - Overlaid directly on top)
                    centerCanvas.getChildren().add(diceBox);

                    // Play initial wipe animations
                    wipes[0].play();
                    wipes[1].play();
                });

                revealTimer.play();

            } catch (Exception ex) {
                statusLabel.setText("Error loading animation: " + ex.getMessage());
            }
        });

        // ==========================================
        // TODO 4: WINDOW SIZE & SCREEN MANAGEMENT
        // ==========================================
        // The window dimensions are managed here. We set a large default size (1280x800)
        // AND call stage.setMaximized(true) to launch the application near fullscreen.
        Scene scene = new Scene(root, 1280, 800);
        stage.setTitle("CSSE220 Team Starter");
        stage.setScene(scene);
        stage.setMaximized(true); // <-- Forces window to near fullscreen on launch
        stage.show();

        // TODO 5: Replace this starter view with your real app design.
    }

    public static void main(String[] args) {
        launch(args);
    }
}