package finalproject.network;

import finalproject.game.*;
import java.util.ArrayList;
import java.util.List;

/** Explicit, versioned fields using the existing Wire framing. */
public final class GameStateCodec {
    private GameStateCodec() { }

    public static String[] encode(GameState state) {
        List<String> fields = new ArrayList<>(List.of("GAME_STATE", "1", Long.toString(state.revision()),
                state.phase().name(), Integer.toString(state.currentPlayerId()), Integer.toString(state.turnNumber())));
        for (int id : state.turnOrder()) fields.add(Integer.toString(id));
        fields.add(Integer.toString(state.board().tiles().size()));
        for (Tile tile : state.board().tiles()) {
            fields.addAll(List.of(Integer.toString(tile.id()), Integer.toString(tile.q()), Integer.toString(tile.r()),
                    tile.resource().name(), Integer.toString(tile.number())));
        }
        return fields.toArray(String[]::new);
    }

    public static GameState decode(List<String> fields) {
        if (fields.size() != 106 || !fields.get(0).equals("GAME_STATE") || !fields.get(1).equals("1")
                || !fields.get(10).equals("19")) throw new IllegalArgumentException("Invalid game snapshot");
        List<Integer> order = new ArrayList<>();
        for (int i = 6; i < 10; i++) order.add(Integer.parseInt(fields.get(i)));
        List<Tile> tiles = new ArrayList<>();
        for (int i = 11; i < fields.size(); i += 5) {
            tiles.add(new Tile(Integer.parseInt(fields.get(i)), Integer.parseInt(fields.get(i + 1)),
                    Integer.parseInt(fields.get(i + 2)), ResourceType.valueOf(fields.get(i + 3)), Integer.parseInt(fields.get(i + 4))));
        }
        return new GameState(new Board(tiles), order, Integer.parseInt(fields.get(4)),
                GamePhase.valueOf(fields.get(3)), Integer.parseInt(fields.get(5)), Long.parseLong(fields.get(2)));
    }
}
