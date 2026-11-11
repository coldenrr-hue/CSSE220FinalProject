package finalproject.network;

import static org.junit.jupiter.api.Assertions.*;

import finalproject.game.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Real sockets on localhost: one LobbyServer and four LobbyClients per test. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class LobbyIntegrationTest {
    private LobbyServer server;
    private final List<Probe> opened = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = new LobbyServer(0);
    }

    @AfterEach
    void stopEverything() {
        for (Probe p : opened) p.client.close();
        server.close();
    }

    // ------------------------------------------------------------------ lobby

    @Test
    void lobbyAdmissionRules() throws Exception {
        Probe host = open("Host", server.hostToken());
        assertEquals("0", host.await(f -> f.get(0).equals("WELCOME")).get(1), "host always gets seat 0");

        open("host", "").await(f -> isError(f, "already in use"));          // names are case-insensitive
        open("   ", "").await(f -> isError(f, "between 1 and 20"));
        open("x".repeat(21), "").await(f -> isError(f, "between 1 and 20"));

        Set<String> seats = new HashSet<>();
        for (String name : List.of("Guest A", "Guest B", "Guest C")) {
            seats.add(open(name, "").await(f -> f.get(0).equals("WELCOME")).get(1));
        }
        assertEquals(Set.of("1", "2", "3"), seats);
        host.await(f -> lobbyState(f, 4));
        open("Fifth", "").await(f -> isError(f, "full"));
    }

    @Test
    void chatReachesEveryoneIntact() throws Exception {
        List<Probe> players = joinFour();
        String chat = "Hello \t table — 🌲";
        players.get(1).client.send("CHAT", chat);
        for (Probe p : players) {
            List<String> f = p.await(e -> e.get(0).equals("CHAT") && e.get(2).equals(chat));
            assertEquals("Guest A", f.get(1), "sender name");
        }
    }

    @Test
    void onlyTheHostCanStartAndOnlyWhenEveryoneIsReady() throws Exception {
        List<Probe> players = joinFour();
        Probe host = players.get(0), guest = players.get(1);
        host.client.send("START");
        host.await(f -> isError(f, "mark ready"));

        for (Probe p : players) p.client.send("READY", "true");
        host.await(f -> lobbyState(f, 4) && allReady(f, true));

        guest.client.send("START");
        guest.client.send("CHAT", "guest-start-barrier");
        // The server handles one connection's messages in order, so if START had worked,
        // STARTED would reach the host before this chat message.
        host.awaitWithout(f -> f.get(0).equals("CHAT") && f.get(2).equals("guest-start-barrier"),
                f -> f.get(0).equals("STARTED"), "a guest started the game");

        host.client.send("START");
        for (Probe p : players) p.await(f -> f.get(0).equals("STARTED"));
        open("Late guest", "").await(f -> isError(f, "already started"));
    }

    // ------------------------------------------------------------------ the game

    @Test
    void everyoneGetsTheSameBoardAndOnlyTheirOwnEmptyHand() throws Exception {
        List<Probe> players = startGame();
        GameState game = sameGame(players);
        assertEquals(GamePhase.SETUP_FORWARD, game.phase());
        assertEquals(Set.of(0, 1, 2, 3), new HashSet<>(game.turnOrder()));
        for (Probe p : players) {
            List<String> hand = p.await(f -> f.get(0).equals("DEV_CARDS"));
            assertEquals(DevelopmentHand.EMPTY, GameStateCodec.decodeHand(hand));
            assertEquals(Long.toString(game.revision()), hand.get(1));
        }
    }

    @Test
    void setupAndFirstRoundOfPlayStaySynchronized() throws Exception {
        List<Probe> players = startGame();
        GameState game = sameGame(players);
        Board originalBoard = game.board();
        List<Integer> sequence = new ArrayList<>(game.turnOrder());
        List<Integer> reversed = new ArrayList<>(game.turnOrder());
        Collections.reverse(reversed);
        sequence.addAll(reversed);
        sequence.addAll(game.turnOrder());

        for (int step = 0; step < 12; step++) {
            String at = "step " + step;
            assertEquals(sequence.get(step), game.currentPlayerId(), "active player, " + at);
            int currentId = game.currentPlayerId();
            Probe active = players.stream().filter(p -> p.id == currentId).findFirst().orElseThrow();
            Probe inactive = players.stream().filter(p -> p.id != currentId).findFirst().orElseThrow();

            inactive.client.send("END_TURN", rev(game));
            inactive.await(f -> isError(f, "not your turn"));
            active.client.send("END_TURN", "invalid");
            active.await(f -> isError(f, "stale"));

            if (SetupRules.isSetup(game)) {
                game = playSetupTurn(players, active, inactive, game, step);
            } else {
                game = playNormalTurn(players, active, inactive, game);
            }
            assertEquals(originalBoard, game.board(), "board changed, " + at);
            if (step == 7) {
                assertEquals(GamePhase.PLAYING, game.phase(), "normal play after setup");
                assertEquals(1, game.turnNumber());
                assertEquals(8, game.pieces().settlements().size());
                assertEquals(8, game.pieces().roads().size());
                for (int p = 0; p < 4; p++) assertEquals(2, game.victoryPoints(p));
            }
        }
    }

    private GameState playSetupTurn(List<Probe> players, Probe active, Probe inactive, GameState game, int step)
            throws Exception {
        int currentId = active.id;
        active.client.send("END_TURN", rev(game));
        active.await(f -> isError(f, "road"));
        active.client.send("ROLL_DICE", rev(game));
        active.await(f -> isError(f, "normal play"));

        int vertex = SetupRules.legalSettlements(game, currentId).getFirst();
        inactive.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), rev(game));
        inactive.await(f -> isError(f, "not your turn"));
        int farEdge = farEdge(game, vertex);
        active.client.send("PLACE_ROAD", Integer.toString(farEdge), rev(game));
        active.await(f -> isError(f, "touch"));
        active.client.send("PLACE_SETTLEMENT", "not-a-number", rev(game));
        active.await(f -> f.get(0).equals("ERROR"));

        long before = game.revision();
        active.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), rev(game));
        game = sameGame(players);
        assertEquals(currentId, game.currentPlayerId(), "still my turn until the road");
        assertEquals(vertex, game.pieces().pendingSettlement());
        assertEquals(before + 1, game.revision());
        assertEquals(step + 1, game.pieces().settlements().size());

        active.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), Long.toString(before));
        active.await(f -> isError(f, "stale"));
        active.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), rev(game));
        active.await(f -> f.get(0).equals("ERROR"));   // second settlement this turn

        int road = SetupRules.legalRoads(game, currentId).getFirst();
        before = game.revision();
        active.client.send("PLACE_ROAD", Integer.toString(road), rev(game));
        game = sameGame(players);
        assertEquals(before + 1, game.revision());
        assertEquals(step + 1, game.pieces().roads().size());
        assertEquals(-1, game.pieces().pendingSettlement());
        if (step >= 4) {
            assertEquals(SetupRules.startingResources(game.board(), vertex), game.pieces().hand(currentId),
                    "resources for the second settlement");
        } else {
            assertTrue(game.pieces().hand(currentId).isEmpty(), "no resources for the first settlement");
        }
        return game;
    }

    private GameState playNormalTurn(List<Probe> players, Probe active, Probe inactive, GameState game) throws Exception {
        active.client.send("END_TURN", rev(game));
        active.await(f -> isError(f, "Roll the dice"));
        inactive.client.send("ROLL_DICE", rev(game));
        inactive.await(f -> isError(f, "not your turn"));
        active.client.send("ROLL_DICE", "invalid");
        active.await(f -> isError(f, "stale"));

        inactive.client.send("BUY_DEV_CARD", rev(game));
        inactive.await(f -> isError(f, "not your turn"));
        active.client.send("BUY_DEV_CARD", rev(game));
        active.await(f -> isError(f, "Roll the dice before buying"));
        active.client.send("PLAY_DEV_CARD", "KNIGHT", rev(game));
        active.await(f -> isError(f, "don't have"));
        active.client.send("PLAY_DEV_CARD", "NOT_A_CARD", rev(game));
        active.await(f -> f.get(0).equals("ERROR"));
        active.client.send("MOVE_ROBBER", "0", "-1", rev(game));
        active.await(f -> isError(f, "robber"));
        active.client.send("PLACE_SETTLEMENT", "0", rev(game));
        active.await(f -> f.get(0).equals("ERROR"));

        long before = game.revision();
        active.client.send("ROLL_DICE", rev(game));
        game = sameGame(players);
        assertTrue(game.dice().rolled());
        assertTrue(game.dice().total() >= 2 && game.dice().total() <= 12);
        assertEquals(before + 1, game.revision());
        assertEquals(active.id, game.currentPlayerId(), "rolling doesn't end the turn");

        active.client.send("ROLL_DICE", Long.toString(before));
        active.await(f -> isError(f, "stale"));
        active.client.send("ROLL_DICE", rev(game));
        active.await(f -> isError(f, "already rolled"));

        before = game.revision();
        active.client.send("END_TURN", rev(game));
        game = sameGame(players);
        assertEquals(before + 1, game.revision());
        assertFalse(game.dice().rolled(), "dice reset for the next player");
        assertNotEquals(active.id, game.currentPlayerId());
        return game;
    }

    // ------------------------------------------------------------------ disconnects

    @Test
    void guestDisconnectReturnsEveryoneToTheLobbyAndANewGameCanStart() throws Exception {
        List<Probe> players = startGame();
        GameState first = sameGame(players);
        Probe host = players.get(0), leaving = players.get(3);

        leaving.client.close();
        host.await(f -> lobbyState(f, 3) && allReady(f, false));
        Probe replacement = open("Replacement", "");
        replacement.id = Integer.parseInt(replacement.await(f -> f.get(0).equals("WELCOME")).get(1));
        assertEquals(leaving.id, replacement.id, "the freed seat is reused");
        host.await(f -> lobbyState(f, 4));

        List<Probe> restarted = List.of(players.get(0), players.get(1), players.get(2), replacement);
        for (Probe p : restarted) p.client.send("READY", "true");
        host.await(f -> lobbyState(f, 4) && allReady(f, true));
        host.client.send("START");
        for (Probe p : restarted) p.await(f -> f.get(0).equals("STARTED"));
        GameState second = sameGame(restarted);
        assertTrue(second.revision() > first.revision(), "revision keeps increasing so old commands stay stale");
        assertEquals(GamePhase.SETUP_FORWARD, second.phase());
        assertTrue(second.pieces().settlements().isEmpty());
    }

    @Test
    void hostDisconnectShutsTheServerDown() throws Exception {
        List<Probe> players = joinFour();
        players.get(0).client.close();
        for (Probe p : players.subList(1, 4)) p.await(f -> f.get(0).equals("DISCONNECTED"));
    }

    // ------------------------------------------------------------------ helpers

    private List<Probe> joinFour() throws Exception {
        Probe host = open("Host", server.hostToken());
        host.id = Integer.parseInt(host.await(f -> f.get(0).equals("WELCOME")).get(1));
        List<Probe> players = new ArrayList<>(List.of(host));
        for (String name : List.of("Guest A", "Guest B", "Guest C")) {
            Probe guest = open(name, "");
            guest.id = Integer.parseInt(guest.await(f -> f.get(0).equals("WELCOME")).get(1));
            players.add(guest);
        }
        host.await(f -> lobbyState(f, 4));
        return players;
    }

    private List<Probe> startGame() throws Exception {
        List<Probe> players = joinFour();
        for (Probe p : players) p.client.send("READY", "true");
        players.get(0).await(f -> lobbyState(f, 4) && allReady(f, true));
        players.get(0).client.send("START");
        for (Probe p : players) p.await(f -> f.get(0).equals("STARTED"));
        return players;
    }

    /** Reads the next GAME_STATE from every client and checks they are identical. */
    private static GameState sameGame(List<Probe> players) throws Exception {
        GameState expected = null;
        for (Probe p : players) {
            GameState state = GameStateCodec.decode(p.await(f -> f.get(0).equals("GAME_STATE")));
            if (expected == null) expected = state;
            else assertEquals(expected, state, "clients received different games");
        }
        return expected;
    }

    private static int farEdge(GameState game, int vertex) {
        return BoardGraph.of(game.board()).edges().stream().filter(e -> !e.touches(vertex)
                && !game.pieces().roads().containsKey(e.id())).findFirst().orElseThrow().id();
    }

    private static String rev(GameState game) { return Long.toString(game.revision()); }

    private static boolean isError(List<String> f, String text) {
        return f.get(0).equals("ERROR") && f.get(1).contains(text);
    }

    private static boolean lobbyState(List<String> f, int count) {
        return f.get(0).equals("STATE") && f.size() == 2 + count * 3;
    }

    private static boolean allReady(List<String> f, boolean ready) {
        for (int i = 4; i < f.size(); i += 3) if (!f.get(i).equals(Boolean.toString(ready))) return false;
        return true;
    }

    private Probe open(String name, String token) throws Exception {
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

        /** Skips events until one matches; fails after 5 seconds. */
        List<String> await(Predicate<List<String>> condition) throws Exception {
            return awaitWithout(condition, f -> false, null);
        }

        /** Like await, but fails if a forbidden event shows up first. */
        List<String> awaitWithout(Predicate<List<String>> condition, Predicate<List<String>> forbidden, String why)
                throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            List<List<String>> skipped = new ArrayList<>();
            while (true) {
                long remaining = deadline - System.nanoTime();
                List<String> event = remaining > 0 ? events.poll(remaining, TimeUnit.NANOSECONDS) : null;
                if (event == null) fail("Timed out waiting for lobby event; skipped " + skipped);
                if (forbidden.test(event)) fail(why + ": " + event);
                if (condition.test(event)) return event;
                skipped.add(event);
            }
        }
    }
}
