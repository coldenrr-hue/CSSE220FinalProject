package finalproject.network;

import finalproject.game.*;
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
    private Pieces pieces = Pieces.EMPTY;
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
                if (hello.length != 4 || !hello[0].equals("JOIN") || !hello[1].equals("1")) {
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
        if (fields.length == 2 && fields[0].equals("CHAT")) {
            String message = fields[1].strip();
            if (!message.isEmpty() && message.length() <= 300) broadcast("CHAT", peer.name, message);
        } else if (!started && fields.length == 2 && fields[0].equals("READY")) {
            peer.ready = fields[1].equals("true");
            snapshot();
        } else if (!started && fields.length == 1 && fields[0].equals("START") && peer.id == 0) {
            if (peers.size() == 4 && peers.values().stream().allMatch(p -> p.ready)) {
                Random random = new Random();
                board = BoardGenerator.generate(random);
                turns = new TurnManager(new ArrayList<>(peers.keySet()), random);
                pieces = Pieces.EMPTY;
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
            } else if (SetupRules.isSetup(currentGame())) {
                Wire.send(peer.out, "ERROR", "During setup your turn ends when you place your road.");
            } else {
                turns.advance();
                gameRevision++;
                broadcastGame();
            }
        } else if (fields[0].equals("PLACE_SETTLEMENT") || fields[0].equals("PLACE_ROAD")) {
            place(peer, fields);
        }
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
            if (fields[0].equals("PLACE_SETTLEMENT")) {
                pieces = SetupRules.placeSettlement(game, peer.id, spot).pieces();
            } else {
                pieces = SetupRules.placeRoad(game, peer.id, spot).pieces();
                turns.advance();        // the road finishes this setup turn
            }
            gameRevision++;
            broadcastGame();
        } catch (IllegalArgumentException e) {   // also covers a bad number
            Wire.send(peer.out, "ERROR", e.getMessage());
        }
    }

    private GameState currentGame() {
        return new GameState(board, turns.order(), turns.currentPlayerId(),
                turns.phase(), turns.turnNumber(), gameRevision, pieces);
    }

    private void broadcastGame() {
        broadcast(GameStateCodec.encode(currentGame()));
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
        for (Peer peer : peers.values()) {
            try { Wire.send(peer.out, fields); }
            catch (IOException e) { try { peer.socket.close(); } catch (IOException ignored) { } }
        }
    }

    private synchronized void disconnected(Peer peer) {
        if (peers.remove(peer.id) == null || closed) return;
        if (peer.id == 0) { close(); return; }
        // A match cannot continue with a missing player; return everyone to the lobby.
        started = false;
        board = null;
        turns = null;
        pieces = Pieces.EMPTY;
        peers.values().forEach(p -> p.ready = false);
        broadcast("CHAT", "Lobby", peer.name + " disconnected. Waiting for four ready players.");
        snapshot();
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