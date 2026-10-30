package finalproject;

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
 * Starter JavaFX app for CSSE220 Milestone 1.
 * Replace this simple window with the first version of your project.
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
                String videoPath = getClass().getResource("/DiceCup.mp4") != null
                        ? getClass().getResource("/DiceCup.mp4").toExternalForm()
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
                    // Load dice images (using RoseDice prefix as per your file structure)
                    Image img1 = new Image(getClass().getResourceAsStream("/RoseDice" + roll1 + ".png"));
                    Image img2 = new Image(getClass().getResourceAsStream("/RoseDice" + roll2 + ".png"));

                    ImageView diceView1 = new ImageView(img1);
                    ImageView diceView2 = new ImageView(img2);

                    double diceSize = 100; // Original requested dice png size maintained
                    diceView1.setFitWidth(diceSize);
                    diceView1.setFitHeight(diceSize);
                    diceView1.setPreserveRatio(true);

                    diceView2.setFitWidth(diceSize);
                    diceView2.setFitHeight(diceSize);
                    diceView2.setPreserveRatio(true);

                    // --- Create PowerPoint-style Bottom-to-Top Wipe Clips ---
                    Rectangle clip1 = new Rectangle(diceSize, 0);
                    clip1.setY(diceSize);
                    diceView1.setClip(clip1);

                    Rectangle clip2 = new Rectangle(diceSize, 0);
                    clip2.setY(diceSize);
                    diceView2.setClip(clip2);

                    // Wipe Effect Timeline (0.5 seconds)
                    Timeline wipe1 = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(clip1.yProperty(), diceSize),
                                    new KeyValue(clip1.heightProperty(), 0)),
                            new KeyFrame(Duration.millis(500),
                                    new KeyValue(clip1.yProperty(), 0),
                                    new KeyValue(clip1.heightProperty(), diceSize))
                    );

                    Timeline wipe2 = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(clip2.yProperty(), diceSize),
                                    new KeyValue(clip2.heightProperty(), 0)),
                            new KeyFrame(Duration.millis(500),
                                    new KeyValue(clip2.yProperty(), 0),
                                    new KeyValue(clip2.heightProperty(), diceSize))
                    );

                    // Put dice side-by-side in an HBox
                    HBox diceBox = new HBox(30, diceView1, diceView2);
                    diceBox.setAlignment(Pos.CENTER);

                    // Requirement 2: Position dice so they appear slightly above the bottom of the animation area
                    StackPane.setAlignment(diceBox, Pos.BOTTOM_CENTER);
                    diceBox.setTranslateY(51); // Offset upwards from bottom edge

                    // Requirement 5: Motion Path (Line effect) to move dice from initial location to the center of window screen
                    TranslateTransition slideToCenter = new TranslateTransition(Duration.seconds(0.8), diceBox);
                    slideToCenter.setToX(0);
                    slideToCenter.setToY(0); // Moves from current offset back to StackPane center (0,0)

                    // Requirement 3: Extended Grow Effect lasting 3 seconds
                    ScaleTransition grow1 = new ScaleTransition(Duration.seconds(3), diceView1);
                    grow1.setToX(1.5); // Grow to 150%
                    grow1.setToY(1.5);

                    ScaleTransition grow2 = new ScaleTransition(Duration.seconds(3), diceView2);
                    grow2.setToX(1.5);
                    grow2.setToY(1.5);

                    // Sequence chain: Wipe finishes -> Slide to center runs -> Grow effect runs
                    wipe1.setOnFinished(wEvent -> slideToCenter.play());
                    slideToCenter.setOnFinished(sEvent -> {
                        grow1.play();
                        grow2.play();
                    });

                    // Add dice HBox to the same StackPane canvas (Layer 2 - Overlaid directly on top)
                    centerCanvas.getChildren().add(diceBox);

                    // Play initial wipe animation
                    wipe1.play();
                    wipe2.play();
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