package finalproject.network;

import finalproject.game.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, versioned fields using the existing Wire framing.
 * Version 6 adds the roll revision and pending discard counts for a rolled seven.
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
        List<String> fields = new ArrayList<>(List.of("GAME_STATE", "6", Long.toString(state.revision()),
                state.phase().name(), Integer.toString(state.currentPlayerId()), Integer.toString(state.turnNumber())));
        for (int id : state.turnOrder()) fields.add(Integer.toString(id));
        fields.add(Integer.toString(state.board().tiles().size()));
        for (Tile tile : state.board().tiles()) {
            fields.addAll(List.of(Integer.toString(tile.id()), Integer.toString(tile.q()), Integer.toString(tile.r()),
                    tile.resource().name(), Integer.toString(tile.number())));
        }
        PlayerAssets assets = state.assets();
        fields.add(encodeOwners(assets.settlements()));
        fields.add(encodeOwners(assets.roads()));
        fields.add(Integer.toString(assets.pendingSettlement()));
        fields.add(encodeHands(assets.hands()));
        fields.add(Integer.toString(state.dice().first()));
        fields.add(Integer.toString(state.dice().second()));
        fields.add(assets.cities().stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        TradeOffer offer = state.offer();
        fields.add(offer == null ? "" : offer.from() + ";" + offer.to() + ";" + encodeCards(offer.give()) + ";" + encodeCards(offer.receive()));
        fields.add(Integer.toString(state.robberTile()));
        DevelopmentState dev = state.development();
        fields.add(encodeOwners(dev.cardCounts()));
        fields.add(encodeOwners(dev.knights()));
        fields.add(Integer.toString(dev.largestArmy()));
        fields.add(Integer.toString(dev.deckSize()));
        fields.add(Boolean.toString(dev.playedThisTurn()));
        fields.add(Boolean.toString(dev.movingRobber()));
        fields.add(Integer.toString(dev.freeRoads()));
        fields.add(Long.toString(state.seven().rollRevision()));
        fields.add(encodeOwners(state.seven().pendingDiscards()));
        return fields.toArray(String[]::new);
    }

    public static GameState decode(List<String> fields) {
        if (fields.size() != 124 || !fields.get(0).equals("GAME_STATE") || !fields.get(1).equals("6")
                || !fields.get(10).equals("19")) throw new IllegalArgumentException("Invalid game snapshot");
        List<Integer> order = new ArrayList<>();
        for (int i = 6; i < 10; i++) order.add(Integer.parseInt(fields.get(i)));
        List<Tile> tiles = new ArrayList<>();
        for (int i = 11; i < 106; i += 5) {
            tiles.add(new Tile(Integer.parseInt(fields.get(i)), Integer.parseInt(fields.get(i + 1)),
                    Integer.parseInt(fields.get(i + 2)), ResourceType.valueOf(fields.get(i + 3)), Integer.parseInt(fields.get(i + 4))));
        }
        PlayerAssets assets = new PlayerAssets(decodeOwners(fields.get(106)), decodeOwners(fields.get(107)),
                Integer.parseInt(fields.get(108)), decodeHands(fields.get(109)), fields.get(112).isEmpty() ? java.util.Set.of() :
                        java.util.Arrays.stream(fields.get(112).split(",")).map(Integer::parseInt).collect(java.util.stream.Collectors.toSet()));
        TradeOffer offer = null;
        if (!fields.get(113).isEmpty()) {
            String[] parts = fields.get(113).split(";", -1);
            offer = new TradeOffer(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), decodeCards(parts[2]), decodeCards(parts[3]));
        }
        DevelopmentState dev = new DevelopmentState(decodeOwners(fields.get(115)), decodeOwners(fields.get(116)),
                Integer.parseInt(fields.get(117)), Integer.parseInt(fields.get(118)), Boolean.parseBoolean(fields.get(119)),
                Boolean.parseBoolean(fields.get(120)), Integer.parseInt(fields.get(121)));
        SevenResolution seven = new SevenResolution(Long.parseLong(fields.get(122)), decodeOwners(fields.get(123)));
        return new GameState(new Board(tiles), order, Integer.parseInt(fields.get(4)),
                GamePhase.valueOf(fields.get(3)), Integer.parseInt(fields.get(5)), Long.parseLong(fields.get(2)), assets,
                new DiceResult(Integer.parseInt(fields.get(110)), Integer.parseInt(fields.get(111))), offer, Integer.parseInt(fields.get(114)), dev, seven);
    }

    public static String encodeCards(List<ResourceType> cards) {
        return cards.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }

    public static List<ResourceType> decodeCards(String text) {
        if (text.isEmpty()) return List.of();
        if (text.length() > 1500) throw new IllegalArgumentException("Too many cards.");
        return java.util.Arrays.stream(text.split(",")).map(ResourceType::valueOf).toList();
    }

    public static String[] encodeResult(MatchResult result) {
        List<String> fields = new ArrayList<>(List.of("MATCH_RESULT", Long.toString(result.revision()),
                Integer.toString(result.winnerId()), Integer.toString(result.turns())));
        for (MatchResult.PlayerStats p : result.players()) {
            fields.addAll(List.of(Integer.toString(p.id()), Integer.toString(p.points()),
                    Integer.toString(p.cardsPlayed()), Integer.toString(p.knights()),
                    Integer.toString(p.settlements()), Integer.toString(p.cities()), Integer.toString(p.roads())));
        }
        return fields.toArray(String[]::new);
    }

    public static MatchResult decodeResult(List<String> fields) {
        if (fields.size() != 32 || !fields.getFirst().equals("MATCH_RESULT"))
            throw new IllegalArgumentException("Invalid match result");
        List<MatchResult.PlayerStats> players = new ArrayList<>();
        for (int i = 4; i < fields.size(); i += 7) {
            players.add(new MatchResult.PlayerStats(Integer.parseInt(fields.get(i)),
                    Integer.parseInt(fields.get(i + 1)), Integer.parseInt(fields.get(i + 2)),
                    Integer.parseInt(fields.get(i + 3)), Integer.parseInt(fields.get(i + 4)),
                    Integer.parseInt(fields.get(i + 5)), Integer.parseInt(fields.get(i + 6))));
        }
        return new MatchResult(Long.parseLong(fields.get(1)), Integer.parseInt(fields.get(2)),
                Integer.parseInt(fields.get(3)), players);
    }

    /** Private message for ONE player: DEV_CARDS revision ready-cards new-cards ("KNIGHT.MONOPOLY"). */
    public static String[] encodeHand(long revision, DevelopmentHand hand) {
        return new String[]{"DEV_CARDS", Long.toString(revision), encodeDevelopmentCards(hand.ready()), encodeDevelopmentCards(hand.fresh())};
    }

    public static DevelopmentHand decodeHand(List<String> fields) {
        if (fields.size() != 4 || !fields.get(0).equals("DEV_CARDS")) throw new IllegalArgumentException("Invalid development cards");
        return new DevelopmentHand(decodeDevelopmentCards(fields.get(2)), decodeDevelopmentCards(fields.get(3)));
    }

    private static String encodeDevelopmentCards(List<DevelopmentCard> cards) {
        List<String> names = new ArrayList<>();
        cards.forEach(card -> names.add(card.name()));
        return String.join(".", names);
    }

    private static List<DevelopmentCard> decodeDevelopmentCards(String text) {
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
