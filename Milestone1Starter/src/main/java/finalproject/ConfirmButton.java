package finalproject;

import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class ConfirmButton
{
    private Group view;
    private ResourceCard[] resources;

    public ConfirmButton(ResourceCard[] resources)
    {
        //initialize variables
        this.resources = resources;

        //create button
        Button confirm = new Button("Confirm Changes");
        confirm.setOnAction(event -> {confirmResourceChanges();});

        //create view
        view = new Group(confirm);
    }

    private void confirmResourceChanges()
    {
        //confirm all resource changes
        for (int i = 0; i < resources.length; i++)
        {
            resources[i].confirmChange();
        }
    }

    public Group getView()
    {
        return view;
    }
}
