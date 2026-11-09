package finalproject;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class ResourceCard
{
    private Group view;
    private int numResource;
    private int numChange;
    private Label numDisplay;

    public ResourceCard(String name, Image image)
    {
        //initialize variables
        numResource = 0;
        numDisplay = new Label(numResource + "");
        numDisplay.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + "#12202c" + ";"
                + "-fx-background-color: " + "#f3cc80" + "; -fx-background-radius: 12; -fx-padding: 1 8 1 8;"
                + "-fx-border-color: " + "#12202c" + "; -fx-border-radius: 12; -fx-border-width: 2;");
        ImageView cardImage = new ImageView(image);
<<<<<<< Updated upstream
=======
        cardImage.setFitWidth(64);
        cardImage.setFitHeight(90);
>>>>>>> Stashed changes

        //create card buttons
        Button collectButton = new Button("Collect " + name);
        collectButton.setOnAction(event ->{collect(1);});
        Button spendButton = new Button("Spend " + name);
        spendButton.setOnAction(event ->{Spend(1);});

        //create card group
        VBox cardBox = new VBox(12, numDisplay, cardImage, collectButton, spendButton);
        view = new Group(cardBox);
    }

    private void updateDisplay()
    {
        int displayNum = numResource + numChange;
        numDisplay.setText(displayNum + "");

<<<<<<< Updated upstream
=======
        //change num color when changes are unconfirmed
        if (displayNum > numResource)
        {
            numDisplay.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: green;"
                    + "-fx-background-color: " + "#f3cc80" + "; -fx-background-radius: 12; -fx-padding: 1 8 1 8;"
                    + "-fx-border-color: " + "#12202c" + "; -fx-border-radius: 12; -fx-border-width: 2;");
        }
        else if (displayNum < numResource)
        {
            numDisplay.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: red;"
                    + "-fx-background-color: " + "#f3cc80" + "; -fx-background-radius: 12; -fx-padding: 1 8 1 8;"
                    + "-fx-border-color: " + "#12202c" + "; -fx-border-radius: 12; -fx-border-width: 2;");
        }
        else
        {
            numDisplay.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + "#12202c" + ";"
                    + "-fx-background-color: " + "#f3cc80" + "; -fx-background-radius: 12; -fx-padding: 1 8 1 8;"
                    + "-fx-border-color: " + "#12202c" + "; -fx-border-radius: 12; -fx-border-width: 2;");
        }
    }

>>>>>>> Stashed changes
    private void collect(int amountCollected)
    {
        numChange += amountCollected;
        updateDisplay();
    }

    private void Spend(int amountSpent)
    {
        //don't allow card count to fall below 0
        if (numResource + (numChange - amountSpent) >= 0)
        {
            numChange -= amountSpent;
            updateDisplay();
        }
    }

    public void confirmChange()
    {
        numResource += numChange;
        numChange = 0;
        updateDisplay();
    }

    public Group getView()
    {
        return view;
    }
<<<<<<< Updated upstream
}
=======

    public Label getNumDisplay()
    {
        return numDisplay;
    }
}
>>>>>>> Stashed changes
