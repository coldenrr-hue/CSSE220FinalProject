package finalproject;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Starter JavaFX app for CSSE220 Milestone 1.
 * Replace this simple window with the first version of your project.
 */
public class ResourceCardSystemTest extends Application {

    @Override
    public void start(Stage stage)
    {
        //create card images
        Image brickCard = new Image(getClass().getResource("/images/brickCard.png").toExternalForm());
        Image grainCard = new Image(getClass().getResource("/images/grainCard.png").toExternalForm());
        Image lumberCard = new Image(getClass().getResource("/images/lumberCard.png").toExternalForm());
        Image oreCard = new Image(getClass().getResource("/images/oreCard.png").toExternalForm());
        Image woolCard = new Image(getClass().getResource("/images/woolCard.png").toExternalForm());

        //create cards
        ResourceCard lumber = new ResourceCard("Lumber", lumberCard);
        ResourceCard wool = new ResourceCard("Wool", woolCard);
        ResourceCard brick = new ResourceCard("Brick", brickCard);
        ResourceCard grain = new ResourceCard("Grain", grainCard);
        ResourceCard ore = new ResourceCard("Ore", oreCard);
        ResourceCard[] resources = {lumber, wool, brick, grain, ore};

        //create confirmation button
        ConfirmButton confirm = new ConfirmButton(resources);

        //create window
        HBox root = new HBox(12, lumber.getView(), wool.getView(), brick.getView(), grain.getView(), ore.getView(), confirm.getView());
        root.setPadding(new Insets(20));

        //set the window title and size.
        Scene scene = new Scene(root, 1200, 600);
        stage.setTitle("Resource Card System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
