package finalproject.game;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final standings. Private victory point cards contribute only their points. */
public record MatchResult(long revision, int winnerId, int turns, List<PlayerStats> players) {
    public static final int WINNING_POINTS = 10;

    public record PlayerStats(int id, int points, int cardsPlayed, int knights,
                              int settlements, int cities, int roads) { }

    public MatchResult {
        players = List.copyOf(players);
        var seats = new HashSet<>(players.stream().map(PlayerStats::id).toList());
        if (revision < 0 || turns < 1 || players.size() != 4 || !seats.equals(Set.of(0, 1, 2, 3))
                || players.stream().noneMatch(p -> p.id() == winnerId && p.points() >= WINNING_POINTS)) {
            throw new IllegalArgumentException("Invalid match result");
        }
    }

    public PlayerStats winner() {
        return players.stream().filter(p -> p.id() == winnerId).findFirst().orElseThrow();
    }

    /** Evaluated by the host after each accepted move, using the actual private hands. */
    public static MatchResult ifWon(GameState game, Map<Integer, DevelopmentHand> hands,
                                    Map<Integer, Integer> cardsPlayed) {
        int current = game.currentPlayerId();
        if (game.phase() != GamePhase.PLAYING || points(game, hands, current) < WINNING_POINTS) return null;

        PlayerAssets assets = game.assets();
        List<PlayerStats> standings = game.turnOrder().stream()
                .map(id -> new PlayerStats(id, points(game, hands, id), cardsPlayed.getOrDefault(id, 0),
                        game.development().knights(id), assets.settlementCount(id), assets.cityCount(id),
                        assets.roadCount(id)))
                .sorted(Comparator.comparing((PlayerStats p) -> p.id() != current)
                        .thenComparing(Comparator.comparingInt(PlayerStats::points).reversed())
                        .thenComparingInt(PlayerStats::id))
                .toList();
        return new MatchResult(game.revision(), current, game.turnNumber(), standings);
    }

    private static int points(GameState game, Map<Integer, DevelopmentHand> hands, int id) {
        return game.victoryPoints(id) + hands.getOrDefault(id, DevelopmentHand.EMPTY).victoryPoints();
    }
}
