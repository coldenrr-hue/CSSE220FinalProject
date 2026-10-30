package finalproject.network;

import finalproject.game.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, versioned fields using the existing Wire framing.
 * Version 2 adds four fields at the end for the pieces:
 *   settlements "vertex:player,vertex:player"   roads "edge:player,..."
 *   pending settlement "-1" or a vertex ID       hands "player:FOREST.FIELDS;player:..."
 */
public final class GameStateCodec {
    private GameStateCodec() { }

    public static String[] encode(GameState state) {
        List<String> fields = new ArrayList<>(List.of("GAME_STATE", "2", Long.toString(state.revision()),
                state.phase().name(), Integer.toString(state.currentPlayerId()), Integer.toString(state.turnNumber())));
        for (int id : state.turnOrder()) fields.add(Integer.toString(id));
        fields.add(Integer.toString(state.board().tiles().size()));
        for (Tile tile : state.board().tiles()) {
            fields.addAll(List.of(Integer.toString(tile.id()), Integer.toString(tile.q()), Integer.toString(tile.r()),
                    tile.resource().name(), Integer.toString(tile.number())));
        }
        Pieces pieces = state.pieces();
        fields.add(encodeOwners(pieces.settlements()));
        fields.add(encodeOwners(pieces.roads()));
        fields.add(Integer.toString(pieces.pendingSettlement()));
        fields.add(encodeHands(pieces.hands()));
        return fields.toArray(String[]::new);
    }

    public static GameState decode(List<String> fields) {
        if (fields.size() != 110 || !fields.get(0).equals("GAME_STATE") || !fields.get(1).equals("2")
                || !fields.get(10).equals("19")) throw new IllegalArgumentException("Invalid game snapshot");
        List<Integer> order = new ArrayList<>();
        for (int i = 6; i < 10; i++) order.add(Integer.parseInt(fields.get(i)));
        List<Tile> tiles = new ArrayList<>();
        for (int i = 11; i < 106; i += 5) {
            tiles.add(new Tile(Integer.parseInt(fields.get(i)), Integer.parseInt(fields.get(i + 1)),
                    Integer.parseInt(fields.get(i + 2)), ResourceType.valueOf(fields.get(i + 3)), Integer.parseInt(fields.get(i + 4))));
        }
        Pieces pieces = new Pieces(decodeOwners(fields.get(106)), decodeOwners(fields.get(107)),
                Integer.parseInt(fields.get(108)), decodeHands(fields.get(109)));
        return new GameState(new Board(tiles), order, Integer.parseInt(fields.get(4)),
                GamePhase.valueOf(fields.get(3)), Integer.parseInt(fields.get(5)), Long.parseLong(fields.get(2)), pieces);
    }

    private static String encodeOwners(Map<Integer, Integer> owners) {
        List<String> parts = new ArrayList<>();
        owners.forEach((spot, player) -> parts.add(spot + ":" + player));
        return String.join(",", parts);
    }

    private static Map<Integer, Integer> decodeOwners(String text) {
        Map<Integer, Integer> owners = new HashMap<>();
        if (text.isEmpty()) return owners;
        for (String part : text.split(",")) {
            String[] kv = part.split(":");
            owners.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
        }
        return owners;
    }

    private static String encodeHands(Map<Integer, List<ResourceType>> hands) {
        List<String> parts = new ArrayList<>();
        hands.forEach((player, cards) -> {
            List<String> names = new ArrayList<>();
            cards.forEach(card -> names.add(card.name()));
            parts.add(player + ":" + String.join(".", names));
        });
        return String.join(";", parts);
    }

    private static Map<Integer, List<ResourceType>> decodeHands(String text) {
        Map<Integer, List<ResourceType>> hands = new HashMap<>();
        if (text.isEmpty()) return hands;
        for (String part : text.split(";")) {
            String[] kv = part.split(":", -1);
            List<ResourceType> cards = new ArrayList<>();
            if (!kv[1].isEmpty()) for (String name : kv[1].split("\\.")) cards.add(ResourceType.valueOf(name));
            hands.put(Integer.parseInt(kv[0]), cards);
        }
        return hands;
    }
}