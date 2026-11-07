package finalproject.game;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class Robber {
    String activator;
    Tile location;

    public Robber(String activator){
        this.activator = activator;
        // Have it set the activator to the player whose turn it is.
        // Upon activation:

    }

    public void moveRobber(Tile newLocation){
        location = newLocation;
        // discardCards();
        // neighbors = tileNeighbors(location)
        //steal(neighbors);



    }

    public ArrayList<String> tileNeighbors(Tile location){
        // Evaluate what players are adjacent to tile and make a list; exclude activator player
        // ArrayList<String> adjacentPlayers = new ArrayList<>();
        // return arrayList
        return null;
    }

    public void discardCards(){
        // Evaluate all players that have 7 or more resources
        // Create a list of players that meet criteria
        // Force them to discard cards.
        // Have players confirm their discard selection.
    }

    public void steal(ArrayList<String> neighbors){
        //Select a player to steal from
        // get a random card from that player
    }









}
