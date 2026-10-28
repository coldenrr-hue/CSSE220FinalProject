package finalproject.network;

import finalproject.game.*;
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
            host.id = 0;
            for (Probe guest : List.of(a, b, c)) {
                guest.id = Integer.parseInt(guest.await(f -> f.get(0).equals("WELCOME")).get(1));
            }
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
            List<Probe> players = List.of(host, a, b, c);
            GameState game = sameGame(players);
            Board originalBoard = game.board();
            List<Integer> sequence = new ArrayList<>(game.turnOrder());
            List<Integer> reversed = new ArrayList<>(game.turnOrder());
            Collections.reverse(reversed);
            sequence.addAll(reversed);
            sequence.addAll(game.turnOrder());
            for (int step = 0; step < 12; step++) {
                if (game.currentPlayerId() != sequence.get(step)) throw new AssertionError("Wrong active player");
                int currentId = game.currentPlayerId();
                Probe active = players.stream().filter(p -> p.id == currentId).findFirst().orElseThrow();
                Probe inactive = players.stream().filter(p -> p.id != currentId).findFirst().orElseThrow();
                inactive.client.send("END_TURN", Long.toString(game.revision()));
                inactive.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("not your turn"));
                active.client.send("END_TURN", "invalid");
                active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("stale"));
                long oldRevision = game.revision();
                active.client.send("END_TURN", Long.toString(oldRevision));
                // At the snake reversal, the same player acts twice; stale duplicates must be rejected.
                if (step == 3) active.client.send("END_TURN", Long.toString(oldRevision));
                game = sameGame(players);
                if (game.revision() != oldRevision + 1 || !game.board().equals(originalBoard)) {
                    throw new AssertionError("State changed unexpectedly during a turn");
                }
                if (step == 3) active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("stale"));
                if (step == 7 && (game.phase() != GamePhase.PLAYING || game.turnNumber() != 1)) {
                    throw new AssertionError("Setup did not enter normal play");
                }
            }
            Probe late = open(opened, server, "Late guest", "");
            late.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("already started"));
            c.client.close();
            host.await(f -> state(f, 3, false) && noneReady(f));
            Probe replacement = open(opened, server, "Replacement", "");
            replacement.id = Integer.parseInt(replacement.await(f -> f.get(0).equals("WELCOME")).get(1));
            host.await(f -> state(f, 4, false));
            List<Probe> restartedPlayers = List.of(host, a, b, replacement);
            for (Probe p : restartedPlayers) p.client.send("READY", "true");
            host.await(f -> state(f, 4, false) && allReady(f));
            host.client.send("START");
            for (Probe p : restartedPlayers) p.await(f -> f.get(0).equals("STARTED"));
            GameState restartedGame = sameGame(restartedPlayers);
            if (restartedGame.revision() <= game.revision() || restartedGame.phase() != GamePhase.SETUP_FORWARD) {
                throw new AssertionError("Restart did not create a new setup state");
            }
            host.client.close();
            a.await(f -> f.get(0).equals("DISCONNECTED"));
            b.await(f -> f.get(0).equals("DISCONNECTED"));
            replacement.await(f -> f.get(0).equals("DISCONNECTED"));
            System.out.println("PASS: lobby, chat, identical four-client boards, turn authorization, stale commands, setup, normal play, disconnect/restart, shutdown");
        } finally {
            for (Probe p : opened) p.client.close();
        }
    }

    private static GameState sameGame(List<Probe> players) throws Exception {
        GameState expected = null;
        for (Probe p : players) {
            GameState state = GameStateCodec.decode(p.await(f -> f.get(0).equals("GAME_STATE")));
            if (expected == null) expected = state;
            else if (!expected.equals(state)) throw new AssertionError("Clients received different games");
        }
        return expected;
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
        int id;
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
