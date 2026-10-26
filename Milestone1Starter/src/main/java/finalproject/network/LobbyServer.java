package finalproject.network;

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
                started = true;
                snapshot();
                broadcast("STARTED");
            } else {
                Wire.send(peer.out, "ERROR", "All four players must join and mark ready first.");
            }
        }
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
