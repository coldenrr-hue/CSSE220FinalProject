package finalproject.network;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Transport only: callbacks run on a background thread, never the JavaFX thread. */
public final class LobbyClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final DataOutputStream out;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lobby-send"); t.setDaemon(true); return t;
    });
    private volatile boolean closed;

    public LobbyClient(String address, int port, String name, String token,
                       Consumer<List<String>> messages, Consumer<String> disconnected) throws IOException {
        try {
            socket.connect(new InetSocketAddress(address, port), 4000);
            socket.setTcpNoDelay(true);
            out = new DataOutputStream(socket.getOutputStream());
            Wire.send(out, "JOIN", "1", name, token);
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException ignored) { }
            sender.shutdownNow();
            throw e;
        }
        Thread reader = new Thread(() -> {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                while (!closed) messages.accept(List.of(Wire.read(in)));
            } catch (IOException e) {
                if (!closed) disconnected.accept("Connection closed. The host may have left the lobby.");
            } finally { close(); }
        }, "lobby-receive");
        reader.setDaemon(true);
        reader.start();
    }

    public void send(String... fields) {
        if (closed) return;
        try {
            sender.execute(() -> {
                try { Wire.send(out, fields); }
                catch (IOException e) { try { socket.close(); } catch (IOException ignored) { } }
            });
        } catch (RejectedExecutionException ignored) { }
    }

    @Override public void close() {
        closed = true;
        sender.shutdownNow();
        try { socket.close(); } catch (IOException ignored) { }
    }
}
