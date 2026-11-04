package finalproject.lobby;

import java.util.ArrayList;
import java.util.List;

/** Typed snapshot received from the host, independent of JavaFX controls. */
public record LobbyState(boolean started, List<Player> players) {
    public LobbyState { players = List.copyOf(players); }

    public record Player(int id, String name, boolean ready) { }

    public static LobbyState fromMessage(List<String> fields) {
        List<Player> players = new ArrayList<>();
        for (int i = 2; i + 2 < fields.size(); i += 3) {
            players.add(new Player(Integer.parseInt(fields.get(i)), fields.get(i + 1),
                    Boolean.parseBoolean(fields.get(i + 2))));
        }
        return new LobbyState(Boolean.parseBoolean(fields.get(1)), players);
    }

    public boolean isReady(int playerId) {
        return players.stream().anyMatch(p -> p.id() == playerId && p.ready());
    }

    public boolean canStart(int playerId) {
        return !started && playerId == 0 && players.size() == 4 && players.stream().allMatch(Player::ready);
    }
}
