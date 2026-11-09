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
            // Each player gets their own (empty) development cards in a separate private message.
            for (Probe p : players) {
                List<String> hand = p.await(f -> f.get(0).equals("DEV_CARDS"));
                if (!GameStateCodec.decodeHand(hand).equals(DevelopmentHand.EMPTY)
                        || !hand.get(1).equals(Long.toString(game.revision()))) throw new AssertionError("Bad private hand");
            }
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
                if (GameRules.isSetup(game)) {
                    active.client.send("END_TURN", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("road"));
                    active.client.send("ROLL_DICE", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("normal play"));
                    int vertex = GameRules.legalSetupSettlements(game, currentId).getFirst();
                    inactive.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), Long.toString(game.revision()));
                    inactive.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("not your turn"));
                    active.client.send("PLACE_ROAD", "0", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("touch"));
                    long before = game.revision();
                    active.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), Long.toString(before));
                    game = sameGame(players);
                    if (game.currentPlayerId() != currentId || game.assets().pendingSettlement() != vertex
                            || game.revision() != before + 1 || game.assets().settlements().size() != step + 1) {
                        throw new AssertionError("Settlement did not synchronize");
                    }
                    active.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), Long.toString(before));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("stale"));
                    active.client.send("PLACE_SETTLEMENT", Integer.toString(vertex), Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR"));
                    int road = GameRules.legalSetupRoads(game, currentId).getFirst();
                    before = game.revision();
                    active.client.send("PLACE_ROAD", Integer.toString(road), Long.toString(before));
                    game = sameGame(players);
                    if (game.revision() != before + 1 || game.assets().roads().size() != step + 1
                            || game.assets().pendingSettlement() != -1) throw new AssertionError("Road did not finish setup turn");
                    if (step >= 4 && game.assets().hand(currentId).isEmpty()) {
                        throw new AssertionError("No resources granted for the second settlement");
                    }
                } else {
                    active.client.send("END_TURN", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("Roll the dice"));
                    inactive.client.send("ROLL_DICE", Long.toString(game.revision()));
                    inactive.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("not your turn"));
                    active.client.send("ROLL_DICE", "invalid");
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("stale"));
                    inactive.client.send("BUY_DEV_CARD", Long.toString(game.revision()));
                    inactive.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("not your turn"));
                    active.client.send("BUY_DEV_CARD", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("Roll the dice before buying"));
                    active.client.send("PLAY_DEV_CARD", "KNIGHT", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("don't have"));
                    active.client.send("PLAY_DEV_CARD", "NOT_A_CARD", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR"));
                    active.client.send("MOVE_ROBBER", "0", "-1", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("robber"));
                    long before = game.revision();
                    active.client.send("ROLL_DICE", Long.toString(before));
                    game = sameGame(players);
                    if (!game.dice().rolled() || game.dice().total() < 2 || game.dice().total() > 12
                            || game.revision() != before + 1) throw new AssertionError("Dice did not synchronize");
                    active.client.send("ROLL_DICE", Long.toString(before));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("stale"));
                    active.client.send("ROLL_DICE", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("already rolled"));
                    active.client.send("PLACE_SETTLEMENT", "0", Long.toString(game.revision()));
                    active.await(f -> f.get(0).equals("ERROR"));
                    if (!game.assets().hand(currentId).isEmpty()) {
                        ResourceType give = game.assets().hand(currentId).getFirst();
                        ResourceType receive = Arrays.stream(ResourceType.values()).filter(r -> r != give && r != ResourceType.DESERT).findFirst().orElseThrow();
                        Probe recipient = inactive;
                        active.client.send("TRADE_OFFER", "" + recipient.id, give.name(), receive.name(), "" + game.revision());
                        game = sameGame(players);
                        if (game.offer() == null || game.offer().to() != recipient.id) throw new AssertionError("Offer not synchronized");
                        active.client.send("TRADE_ACCEPT", "" + game.revision());
                        active.await(f -> f.get(0).equals("ERROR"));
                        recipient.client.send("TRADE_ACCEPT", "0");
                        recipient.await(f -> f.get(0).equals("ERROR") && f.get(1).contains("stale"));
                        if (game.assets().hand(recipient.id).contains(receive)) {
                            PlayerAssets expected = GameRules.acceptTrade(game, recipient.id);
                            recipient.client.send("TRADE_ACCEPT", "" + game.revision());
                            game = sameGame(players);
                            if (!game.assets().equals(expected) || game.offer() != null) throw new AssertionError("Trade did not synchronize");
                            recipient.client.send("TRADE_ACCEPT", "" + game.revision());
                            recipient.await(f -> f.get(0).equals("ERROR"));
                        } else {
                            recipient.client.send("TRADE_ACCEPT", "" + game.revision());
                            recipient.await(f -> f.get(0).equals("ERROR"));
                            recipient.client.send("TRADE_DECLINE", "" + game.revision());
                            game = sameGame(players);
                            if (game.offer() != null) throw new AssertionError("Declined offer remained");
                        }
                    }
                    before = game.revision();
                    active.client.send("END_TURN", Long.toString(before));
                    game = sameGame(players);
                    if (game.revision() != before + 1 || game.dice().rolled()) throw new AssertionError("Turn did not reset dice");
                }
                if (!game.board().equals(originalBoard)) throw new AssertionError("Board changed during play");
                if (step == 7 && (game.phase() != GamePhase.PLAYING || game.turnNumber() != 1
                        || game.assets().settlements().size() != 8 || game.assets().roads().size() != 8)) {
                    throw new AssertionError("Setup did not enter normal play with eight settlements and roads");
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
            System.out.println("PASS: lobby, chat, identical four-client boards, turn authorization, stale commands, eight settlements/roads, shared dice, normal play, disconnect/restart, shutdown");
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
