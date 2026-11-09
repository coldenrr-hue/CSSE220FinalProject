package finalproject.network;

import finalproject.game.*;
import finalproject.Dice;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Host-authoritative four-seat lobby. Slot zero belongs to the host's client. */
public final class LobbyServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 22000;
    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "lan-lobby");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, Peer> peers = new TreeMap<>();
    private final String hostToken = UUID.randomUUID().toString();
    private boolean started;
    private Board board;
    private TurnManager turns;
    private PlayerAssets assets = PlayerAssets.EMPTY;
    private final Dice die = new Dice();
    private DiceResult dice = DiceResult.NONE;
    private TradeOffer offer;
    private int robberTile;
    private DevelopmentState development = DevelopmentState.START;
    // Secret: the deck order and every player's development cards stay on the host.
    // Each player is only ever sent their own hand (see broadcastGame).
    private final List<DevelopmentCard> devDeck = new ArrayList<>();
    private final Map<Integer, DevelopmentHand> devHands = new HashMap<>();
    private final Map<Integer, Integer> cardsPlayed = new HashMap<>();
    private MatchResult matchResult;
    private final Random random = new Random();
    private long gameRevision;
    private volatile boolean closed;

    public LobbyServer(int port) throws IOException {
        listener = new ServerSocket(port);
        workers.execute(this::acceptLoop);
    }

    public int port() { return listener.getLocalPort(); }
    public String hostToken() { return hostToken; }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                socket.setSoTimeout(5000);
                workers.execute(() -> handle(socket));
            } catch (IOException e) {
                if (!closed) close();
            }
        }
    }

    private void handle(Socket socket) {
        Peer peer = null;
        try (socket) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            String[] hello = Wire.read(in);
            synchronized (this) {
                if (hello.length != 4 || !hello[0].equals("JOIN") || !hello[1].equals(Wire.LOBBY_VERSION)) {
                    Wire.send(out, "ERROR", "Incompatible lobby protocol.");
                    return;
                }
                String name = hello[2].strip();
                boolean host = hostToken.equals(hello[3]);
                int id = host ? 0 : freeGuestSlot();
                String rejection = started ? "The game has already started."
                        : name.isEmpty() || name.length() > 20 ? "Use a name between 1 and 20 characters."
                        : id < 0 || peers.containsKey(id) ? "This lobby is full."
                        : peers.values().stream().anyMatch(p -> p.name.equalsIgnoreCase(name))
                        ? "That player name is already in use." : null;
                if (rejection != null) {
                    Wire.send(out, "ERROR", rejection);
                    return;
                }
                peer = new Peer(id, name, socket, out);
                peers.put(id, peer);
                Wire.send(out, "WELCOME", Integer.toString(id));
                broadcast("CHAT", "Lobby", name + " joined.");
                snapshot();
            }
            socket.setSoTimeout(0);
            while (!closed) command(peer, Wire.read(in));
        } catch (IOException ignored) {
            // EOF, a failed write, and a reset all remove this connection below.
        } finally {
            if (peer != null) disconnected(peer);
        }
    }

    private int freeGuestSlot() {
        for (int i = 1; i < 4; i++) if (!peers.containsKey(i)) return i;
        return -1;
    }

    private synchronized void command(Peer peer, String[] fields) throws IOException {
        if (fields.length == 0 || peers.get(peer.id) != peer) return;
        if (fields.length == 2 && fields[0].equals("CHAT")) {
            String message = fields[1].strip();
            if (!message.isEmpty() && message.length() <= 300) broadcast("CHAT", peer.name, message);
        } else if (fields[0].equals("NEW_GAME")) {
            if (matchResult == null || fields.length != 2 || !fields[1].equals(Long.toString(matchResult.revision()))) {
                Wire.send(peer.out, "ERROR", "Use the latest match results to start a new game.");
            } else if (peer.id != 0) {
                Wire.send(peer.out, "ERROR", "Only the host can start a new game.");
            } else {
                resetMatch();
                snapshot();
            }
        } else if (matchResult != null) {
            Wire.send(peer.out, "ERROR", "The match is over.");
        } else if (fields[0].startsWith("TRADE_") || fields[0].equals("BANK_TRADE") || fields[0].equals("BUILD")) {
            playCommand(peer, fields);
        } else if (!started && fields.length == 2 && fields[0].equals("READY")) {
            peer.ready = fields[1].equals("true");
            snapshot();
        } else if (!started && fields.length == 1 && fields[0].equals("START") && peer.id == 0) {
            if (peers.size() == 4 && peers.values().stream().allMatch(p -> p.ready)) {
                board = BoardGenerator.generate(random);
                turns = new TurnManager(new ArrayList<>(peers.keySet()), random);
                assets = PlayerAssets.EMPTY;
                dice = DiceResult.NONE;
                robberTile = GameState.desertTile(board);
                development = DevelopmentState.START;
                devDeck.clear();
                devDeck.addAll(DevelopmentRules.shuffledDeck(random));
                devHands.clear();
                gameRevision++;
                started = true;
                snapshot();
                broadcast("STARTED");
                broadcastGame();
            } else {
                Wire.send(peer.out, "ERROR", "All four players must join and mark ready first.");
            }
        } else if (fields[0].equals("END_TURN")) {
            if (!started || turns == null) {
                Wire.send(peer.out, "ERROR", "There is no active game.");
            } else if (peer.id != turns.currentPlayerId()) {
                Wire.send(peer.out, "ERROR", "It is not your turn.");
            } else if (fields.length != 2 || !fields[1].equals(Long.toString(gameRevision))) {
                Wire.send(peer.out, "ERROR", "That turn request is stale. Use the latest game state.");
            } else if (GameRules.isSetup(currentGame())) {
                Wire.send(peer.out, "ERROR", "During setup your turn ends when you place your road.");
            } else if (!dice.rolled()) {
                Wire.send(peer.out, "ERROR", "Roll the dice before ending your turn.");
            } else if (development.busy()) {
                Wire.send(peer.out, "ERROR", development.movingRobber()
                        ? "Move the robber before ending your turn." : "Place your free roads before ending your turn.");
            } else {
                offer = null;
                // Cards bought this turn become playable on this player's next turn.
                devHands.put(peer.id, hand(peer.id).endTurn());
                development = development.nextTurn();
                turns.advance();
                dice = DiceResult.NONE;
                gameRevision++;
                broadcastGame();
            }
        } else if (fields[0].equals("PLACE_SETTLEMENT") || fields[0].equals("PLACE_ROAD")) {
            place(peer, fields);
        } else if (fields[0].equals("ROLL_DICE")) {
            if (!started || turns == null || GameRules.isSetup(currentGame())) {
                Wire.send(peer.out, "ERROR", "Dice can only be rolled during normal play.");
            } else if (peer.id != turns.currentPlayerId()) {
                Wire.send(peer.out, "ERROR", "It is not your turn.");
            } else if (fields.length != 2 || !fields[1].equals(Long.toString(gameRevision))) {
                Wire.send(peer.out, "ERROR", "That roll request is stale. Use the latest game state.");
            } else if (dice.rolled()) {
                Wire.send(peer.out, "ERROR", "You have already rolled this turn.");
            } else if (development.busy()) {
                Wire.send(peer.out, "ERROR", "Finish your development card before rolling.");
            } else {
                dice = new DiceResult(die.roll(), die.roll());
                assets = GameRules.collectDiceResources(currentGame());
                gameRevision++;
                broadcastGame();
            }
        } else if (fields[0].equals("BUY_DEV_CARD") || fields[0].equals("PLAY_DEV_CARD")
                || fields[0].equals("MOVE_ROBBER")) {
            developmentCommand(peer, fields);
        }
    }

    /**
     * BUY_DEV_CARD <revision>
     * PLAY_DEV_CARD <card> <revision> [resource] [resource]   (Year of Plenty: 2, Monopoly: 1)
     * MOVE_ROBBER <tile> <victim or -1> <revision>            (after a Knight)
     */
    private void developmentCommand(Peer peer, String[] fields) throws IOException {
        int revisionField = switch (fields[0]) {
            case "BUY_DEV_CARD" -> 1;
            case "PLAY_DEV_CARD" -> 2;
            default -> 3;
        };
        if (!started || turns == null) {
            Wire.send(peer.out, "ERROR", "There is no active game.");
            return;
        }
        if (peer.id != turns.currentPlayerId()) {
            Wire.send(peer.out, "ERROR", "It is not your turn.");
            return;
        }
        if (fields.length <= revisionField || !fields[revisionField].equals(Long.toString(gameRevision))) {
            Wire.send(peer.out, "ERROR", "That move is stale. Use the latest game state.");
            return;
        }
        try {
            GameState game = currentGame();
            switch (fields[0]) {
                case "BUY_DEV_CARD" -> {
                    if (fields.length != 2) throw new IllegalArgumentException("Invalid development card purchase.");
                    if (devDeck.isEmpty()) throw new IllegalArgumentException("There are no development cards left.");
                    DevelopmentRules.Result result = DevelopmentRules.buy(game, hand(peer.id), peer.id, devDeck.getLast());
                    devDeck.removeLast();
                    apply(peer.id, result);
                }
                case "PLAY_DEV_CARD" -> {
                    if (fields.length > 5) throw new IllegalArgumentException("Too many resource choices.");
                    DevelopmentCard card = DevelopmentCard.valueOf(fields[1]);
                    List<ResourceType> choices = new ArrayList<>();
                    for (int i = 3; i < fields.length; i++) choices.add(ResourceType.valueOf(fields[i]));
                    DevelopmentRules.Result result = DevelopmentRules.play(game, hand(peer.id), peer.id, card, choices);
                    cardsPlayed.merge(peer.id, 1, Integer::sum);
                    apply(peer.id, result);
                }
                default -> {
                    if (fields.length != 4) throw new IllegalArgumentException("Pick a tile for the robber.");
                    GameState moved = DevelopmentRules.moveRobber(game, peer.id,
                            Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), random);
                    apply(peer.id, new DevelopmentRules.Result(moved, hand(peer.id)));
                }
            }
        } catch (IllegalArgumentException e) {   // rule problems, unknown card/resource names, bad numbers
            Wire.send(peer.out, "ERROR", e.getMessage() == null ? "That move isn't allowed." : e.getMessage());
        }
    }

    private DevelopmentHand hand(int playerId) {
        return devHands.getOrDefault(playerId, DevelopmentHand.EMPTY);
    }

    /** Keeps the host's copy of a rules result and sends it out. */
    private void apply(int playerId, DevelopmentRules.Result result) {
        GameState next = result.state();
        assets = next.assets();
        offer = null;
        robberTile = next.robberTile();
        development = next.development();
        devHands.put(playerId, result.hand());
        gameRevision++;
        broadcastGame();
    }

    /** Setup placements: PLACE_SETTLEMENT <vertex> <revision> or PLACE_ROAD <edge> <revision>. */
    private void place(Peer peer, String[] fields) throws IOException {
        if (!started || turns == null) {
            Wire.send(peer.out, "ERROR", "There is no active game.");
            return;
        }
        if (peer.id != turns.currentPlayerId()) {
            Wire.send(peer.out, "ERROR", "It is not your turn.");
            return;
        }
        if (fields.length != 3 || !fields[2].equals(Long.toString(gameRevision))) {
            Wire.send(peer.out, "ERROR", "That move is stale. Use the latest game state.");
            return;
        }
        try {
            int spot = Integer.parseInt(fields[1]);
            GameState game = currentGame();
            boolean settlement = fields[0].equals("PLACE_SETTLEMENT");
            if (!settlement && development.freeRoads() > 0) {
                GameState placed = DevelopmentRules.placeFreeRoad(game, peer.id, spot);
                assets = placed.assets();
                development = placed.development();
            } else if (!GameRules.isSetup(game)) {
                assets = GameRules.build(game, peer.id, settlement ? GameRules.Build.SETTLEMENT : GameRules.Build.ROAD, spot);
                offer = null;
            } else if (settlement) {
                assets = GameRules.placeSetupSettlement(game, peer.id, spot).assets();
            } else {
                assets = GameRules.placeSetupRoad(game, peer.id, spot).assets();
                turns.advance();        // the road finishes this setup turn
            }
            gameRevision++;
            broadcastGame();
        } catch (IllegalArgumentException e) {   // also covers a bad number
            Wire.send(peer.out, "ERROR", e.getMessage());
        }
    }

    private void playCommand(Peer peer, String[] f) throws IOException {
        try {
            if (!started || turns == null) throw new IllegalArgumentException("There is no active game.");
            if (f.length < 2 || !f[f.length - 1].equals(Long.toString(gameRevision)))
                throw new IllegalArgumentException("That action is stale. Use the latest game state.");
            GameState game = currentGame();
            switch (f[0]) {
                case "BANK_TRADE" -> {
                    if (f.length != 5) throw new IllegalArgumentException("Invalid bank trade.");
                    assets = GameRules.bankTrade(game, peer.id, ResourceType.valueOf(f[1]), ResourceType.valueOf(f[2]), Integer.parseInt(f[3]));
                    offer = null;
                }
                case "BUILD" -> {
                    if (f.length != 4) throw new IllegalArgumentException("Invalid build.");
                    assets = GameRules.build(game, peer.id, GameRules.Build.valueOf(f[1]), Integer.parseInt(f[2]));
                    offer = null;
                }
                case "TRADE_OFFER" -> {
                    if (f.length != 5) throw new IllegalArgumentException("Invalid offer.");
                    TradeOffer next = new TradeOffer(peer.id, Integer.parseInt(f[1]), GameStateCodec.decodeCards(f[2]), GameStateCodec.decodeCards(f[3]));
                    GameRules.validateOffer(game, next);
                    offer = next;
                }
                case "TRADE_ACCEPT" -> {
                    if (f.length != 2) throw new IllegalArgumentException("Invalid acceptance.");
                    assets = GameRules.acceptTrade(game, peer.id);
                    offer = null;
                }
                case "TRADE_DECLINE", "TRADE_CANCEL" -> {
                    if (f.length != 2 || offer == null || peer.id != (f[0].equals("TRADE_CANCEL") ? offer.from() : offer.to()))
                        throw new IllegalArgumentException("You cannot close this offer.");
                    offer = null;
                }
                default -> throw new IllegalArgumentException("Unknown trade action.");
            }
            gameRevision++;
            broadcastGame();
        } catch (IllegalArgumentException e) {
            Wire.send(peer.out, "ERROR", e.getMessage());
        }
    }

    private GameState currentGame() {
        return new GameState(board, turns.order(), turns.currentPlayerId(),
                turns.phase(), turns.turnNumber(), gameRevision, assets, dice, offer, robberTile, development);
    }

    /**
     * Everyone gets the same public snapshot. Then each player gets a DEV_CARDS
     * message with ONLY their own development cards, so nobody can see anyone else's.
     */
    private void broadcastGame() {
        GameState game = currentGame();
        matchResult = MatchResult.ifWon(game, devHands, cardsPlayed);
        broadcast(GameStateCodec.encode(game));
        for (Peer peer : peers.values()) {
            sendTo(peer, GameStateCodec.encodeHand(game.revision(), hand(peer.id)));
        }
        if (matchResult != null) broadcast(GameStateCodec.encodeResult(matchResult));
    }

    private void sendTo(Peer peer, String... fields) {
        try { Wire.send(peer.out, fields); }
        catch (IOException e) { try { peer.socket.close(); } catch (IOException ignored) { } }
    }

    private void snapshot() {
        List<String> fields = new ArrayList<>(List.of("STATE", Boolean.toString(started)));
        for (Peer peer : peers.values()) {
            fields.add(Integer.toString(peer.id));
            fields.add(peer.name);
            fields.add(Boolean.toString(peer.ready));
        }
        broadcast(fields.toArray(String[]::new));
    }

    private void broadcast(String... fields) {
        for (Peer peer : peers.values()) sendTo(peer, fields);
    }

    private synchronized void disconnected(Peer peer) {
        if (peers.remove(peer.id) == null || closed) return;
        if (peer.id == 0) { close(); return; }
        if (matchResult != null) return; // Keep the final standings open for the remaining players.
        // A match cannot continue with a missing player; return everyone to the lobby.
        resetMatch();
        broadcast("CHAT", "Lobby", peer.name + " disconnected. Waiting for four ready players.");
        snapshot();
    }

    private void resetMatch() {
        started = false;
        board = null;
        turns = null;
        assets = PlayerAssets.EMPTY;
        offer = null;
        dice = DiceResult.NONE;
        development = DevelopmentState.START;
        devDeck.clear();
        devHands.clear();
        cardsPlayed.clear();
        matchResult = null;
        peers.values().forEach(p -> p.ready = false);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
        for (Peer peer : peers.values()) {
            try { peer.socket.close(); } catch (IOException ignored) { }
        }
        peers.clear();
        workers.shutdownNow();
    }

    private static final class Peer {
        final int id;
        final String name;
        final Socket socket;
        final DataOutputStream out;
        boolean ready;
        Peer(int id, String name, Socket socket, DataOutputStream out) {
            this.id = id; this.name = name; this.socket = socket; this.out = out;
        }
    }
}
