package finalproject.game;

import java.util.List;

/** A binding offer from the active player to one other seat. */
public record TradeOffer(int from, int to, List<ResourceType> give, List<ResourceType> receive) {
    public TradeOffer {
        give = List.copyOf(give);
        receive = List.copyOf(receive);
        if (from < 0 || from > 3 || to < 0 || to > 3 || from == to
                || give.isEmpty() || receive.isEmpty() || give.size() > 95 || receive.size() > 95
                || give.contains(ResourceType.DESERT) || receive.contains(ResourceType.DESERT)
                || give.stream().anyMatch(receive::contains))
            throw new IllegalArgumentException("Exchange different resources; both players must give cards.");
    }
}
