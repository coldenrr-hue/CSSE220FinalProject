package finalproject.network;

import finalproject.game.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, versioned fields using the existing Wire framing.
 * Version 4 includes setup pieces, two current-turn dice fields, the robber and
 * the PUBLIC development card info:
 *   settlements "vertex:player,vertex:player"   roads "edge:player,..."
 *   pending settlement "-1" or a vertex ID       hands "player:FOREST.FIELDS;player:..."
 *   robber tile, dev card counts "player:n,...", knights "player:n,...", largest army,
 *   deck size, played this turn, moving robber, free roads
 *
 * A player's actual development cards are secret and travel separately, in a
 * DEV_CARDS message the host sends only to that player (encodeHand / decodeHand).
 */
public final class GameStateCodec {
    private GameStateCodec() { }

    public static String[] encode(GameState state) {
        List<String> fields = new ArrayList<>(List.of("GAME_STATE", "4", Long.toString(state.revision()),
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
        fields.add(Integer.toString(state.dice().first()));
        fields.add(Integer.toString(state.dice().second()));
        fields.add(Integer.toString(state.robberTile()));
        DevelopmentState dev = state.development();
        fields.add(encodeOwners(dev.cardCounts()));
        fields.add(encodeOwners(dev.knights()));
        fields.add(Integer.toString(dev.largestArmy()));
        fields.add(Integer.toString(dev.deckSize()));
        fields.add(Boolean.toString(dev.playedThisTurn()));
        fields.add(Boolean.toString(dev.movingRobber()));
        fields.add(Integer.toString(dev.freeRoads()));
        return fields.toArray(String[]::new);
    }

    public static GameState decode(List<String> fields) {
        if (fields.size() != 120 || !fields.get(0).equals("GAME_STATE") || !fields.get(1).equals("4")
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
        DevelopmentState dev = new DevelopmentState(decodeOwners(fields.get(113)), decodeOwners(fields.get(114)),
                Integer.parseInt(fields.get(115)), Integer.parseInt(fields.get(116)), Boolean.parseBoolean(fields.get(117)),
                Boolean.parseBoolean(fields.get(118)), Integer.parseInt(fields.get(119)));
        return new GameState(new Board(tiles), order, Integer.parseInt(fields.get(4)),
                GamePhase.valueOf(fields.get(3)), Integer.parseInt(fields.get(5)), Long.parseLong(fields.get(2)), pieces,
                new DiceResult(Integer.parseInt(fields.get(110)), Integer.parseInt(fields.get(111))),
                Integer.parseInt(fields.get(112)), dev);
    }

    /** Private message for ONE player: DEV_CARDS revision ready-cards new-cards ("KNIGHT.MONOPOLY"). */
    public static String[] encodeHand(long revision, DevelopmentHand hand) {
        return new String[]{"DEV_CARDS", Long.toString(revision), encodeCards(hand.ready()), encodeCards(hand.fresh())};
    }

    public static DevelopmentHand decodeHand(List<String> fields) {
        if (fields.size() != 4 || !fields.get(0).equals("DEV_CARDS")) throw new IllegalArgumentException("Invalid development cards");
        return new DevelopmentHand(decodeCards(fields.get(2)), decodeCards(fields.get(3)));
    }

    private static String encodeCards(List<DevelopmentCard> cards) {
        List<String> names = new ArrayList<>();
        cards.forEach(card -> names.add(card.name()));
        return String.join(".", names);
    }

    private static List<DevelopmentCard> decodeCards(String text) {
        List<DevelopmentCard> cards = new ArrayList<>();
        if (!text.isEmpty()) for (String name : text.split("\\.")) cards.add(DevelopmentCard.valueOf(name));
        return cards;
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
