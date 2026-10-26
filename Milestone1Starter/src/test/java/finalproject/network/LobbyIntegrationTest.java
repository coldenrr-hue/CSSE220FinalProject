package finalproject.network;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Standalone socket integration test: run with java -ea ...LobbyIntegrationTest. */
public final class LobbyIntegrationTest {
    public static void main(String[] args) throws Exception {
        List<Probe> opened = new ArrayList<>();
        try (LobbyServer server = new LobbyServer(0)) {
            Probe host = open(opened, server, "Host", server.hostToken());
            host.await(f -> f.get(0).equals("WELCOME") && f.get(1).equals("0"));
            Probe duplicate = open(opened, server, "host", "");
            duplicate.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("already in use"));
            Probe a = open(opened, server, "Guest A", "");
            Probe b = open(opened, server, "Guest B", "");
            Probe c = open(opened, server, "Guest C", "");
            for (Probe guest : List.of(a, b, c)) guest.await(f -> f.get(0).equals("WELCOME"));
            host.await(f -> state(f, 4, false));
            Probe fifth = open(opened, server, "Fifth", "");
            fifth.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("full"));
            host.client.send("START");
            host.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("mark ready"));
            String chat = "Hello \t table — 🌲";
            a.client.send("CHAT", chat);
            for (Probe p : List.of(host, a, b, c)) {
                p.await(f -> f.get(0).equals("CHAT") && f.get(2).equals(chat));
            }
            for (Probe p : List.of(host, a, b, c)) p.client.send("READY", "true");
            host.await(f -> state(f, 4, false) && allReady(f));
            a.client.send("START");
            a.client.send("CHAT", "guest-start-barrier");
            host.await(f -> f.get(0).equals("CHAT") && f.get(2).equals("guest-start-barrier"));
            if (host.events.stream().anyMatch(f -> f.get(0).equals("STARTED"))) {
                throw new AssertionError("A guest started the game");
            }
            host.client.send("START");
            for (Probe p : List.of(host, a, b, c)) p.await(f -> f.get(0).equals("STARTED"));
            Probe late = open(opened, server, "Late guest", "");
            late.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("already started"));
            c.client.close();
            host.await(f -> state(f, 3, false) && noneReady(f));
            Probe replacement = open(opened, server, "Replacement", "");
            replacement.await(f -> f.get(0).equals("WELCOME"));
            host.await(f -> state(f, 4, false));
            host.client.close();
            a.await(f -> f.get(0).equals("DISCONNECTED"));
            b.await(f -> f.get(0).equals("DISCONNECTED"));
            replacement.await(f -> f.get(0).equals("DISCONNECTED"));
            System.out.println("PASS: seats, duplicate/full rejection, Unicode chat, ready gating, host-only start, late join, replacement, host shutdown");
        } finally {
            for (Probe p : opened) p.client.close();
        }
    }

    private static boolean state(List<String> f, int count, boolean started) {
        return f.get(0).equals("STATE") && f.size() == 2 + count * 3
                && f.get(1).equals(Boolean.toString(started));
    }
    private static boolean allReady(List<String> f) {
        for (int i = 4; i < f.size(); i += 3) if (!f.get(i).equals("true")) return false;
        return true;
    }
    private static boolean noneReady(List<String> f) {
        for (int i = 4; i < f.size(); i += 3) if (!f.get(i).equals("false")) return false;
        return true;
    }
    private static Probe open(List<Probe> opened, LobbyServer server, String name, String token) throws Exception {
        Probe p = new Probe();
        p.client = new LobbyClient("127.0.0.1", server.port(), name, token, p.events::add,
                error -> p.events.add(List.of("DISCONNECTED", error)));
        opened.add(p);
        return p;
    }
    private static final class Probe {
        final BlockingQueue<List<String>> events = new LinkedBlockingQueue<>();
        LobbyClient client;
        List<String> await(Predicate<List<String>> condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new AssertionError("Timed out waiting for lobby event");
                List<String> event = events.poll(remaining, TimeUnit.NANOSECONDS);
                if (event == null) throw new AssertionError("Timed out waiting for lobby event");
                if (condition.test(event)) return event;
            }
        }
    }
}
