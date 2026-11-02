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
        numDisplay = new Label(numResource + " + (" + numChange + ")");
        ImageView cardImage = new ImageView(image);

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
        numDisplay.setText(numResource + " + (" + numChange + ")");
    }

    private void collect(int amountCollected)
    {
        numChange += amountCollected;
        updateDisplay();
    }

    private void Spend(int amountSpent)
    {
        numChange -= amountSpent;
        updateDisplay();
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
}
