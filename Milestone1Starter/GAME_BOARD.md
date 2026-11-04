# Shared game board

See [DEMO.md](DEMO.md) for the current integrated setup-and-dice demo and tests.

The lobby shows decorative tile artwork. Starting a match opens a full-screen
playing view with the ocean-framed board, a player/score panel on the left,
chat and actions on the right, and your resource cards along the bottom.

## Model and networking

- `game/BoardGenerator` creates one randomized board on the host.
- `game/BoardGraph` defines shared corner and edge IDs for the 19-tile island.
- `game/Pieces` stores settlements, roads, a pending setup settlement, and hands.
- `game/SetupRules` enforces setup placement order, settlement distance, adjoining
  roads, and resources from the second settlement.
- `game/TurnManager` drives the forward/reverse setup order and normal turns.
- `Dice` is George's die generator; only the host rolls it for a shared game.
- `game/DiceResult` and `game/GameState` carry the current turn's dice and board.
- `network/LobbyServer` validates moves against the connection, phase, and revision.
- `network/GameStateCodec` version 4 carries the same complete snapshot to all clients.

## Views and assets

- `game/view/PlayingScreen` is Andrew's game layout, placement prompts and actions.
- `game/view/HexBoardView` draws the island, tokens, pieces, and clickable legal spots.
- `game/view/DiceRollAnimation` plays George's cup shake, progressively uncovers
  the dice as the cup lifts, then updates the Actions result panel. It does not
  require MP4 playback.
- `lobby/LobbyView` switches screens while keeping the same connection and chat.
- `lobby/LobbyArtwork` uses the tile images for the lobby illustration.

Artwork lives in `src/main/resources`: `tiles/`, `board/`, `pieces/`, `cards/`,
`icons/`, `harbors/`, plus George's `DiceCup.mp4` and `RoseDice1.png` through
`RoseDice6.png`. Terrain names are not drawn over the tiles. The number tokens
remain visible. Resource-card hands are currently public in the shared prototype.

## Remaining work

Setup, shared rolls, resource production, normal building and trading work.
Robber movement, discards/stealing on a seven, development cards, Longest Road,
Largest Army and victory conditions remain to be implemented. A seven currently
produces no resources.
Guest disconnection cancels the match and returns players to an unready lobby.


## Trading, docks and building

After rolling, use **Trade** to exchange resources with the bank or propose an
exchange to a named player. Both sides can contain multiple resource types for
player trades, but the same resource cannot appear on both sides. The recipient
must accept before cards move; they can decline, and the proposer can cancel or
replace the offer. Table chat can be used to negotiate another offer. Only the
active player initiates trades, and each accepted trade involves that player.
Ending a turn or successfully buying/building clears any outstanding offer.

Bank trades use 4 identical cards for 1 by default. A settlement or city at either
end of a dock grants 3:1 for a general dock or 2:1 for the pictured resource.
There are four general docks and one for each resource, consistently located
around the coast on every client. The wooden piers and boat illustrations are
native scalable artwork, with the existing resource icons on specialized docks.
The bank has 19 cards of each resource, calculated from cards in players' hands.

**Build** offers roads (lumber + brick), settlements (lumber + brick + wool +
grain), and cities (3 ore + 2 grain). Choose a piece, then a highlighted legal
location. The host checks costs, connectivity, the distance rule and piece
limits. A city upgrades your settlement and produces two cards on matching rolls.
Building and trading can be interleaved after rolling. All players must run this
version because the shared snapshot format now includes cities and trade offers.

Regression checks: `TradingBuildingTest`, `LobbyIntegrationTest` (four real
clients), and `TradingViewTest` (JavaFX UI and `target/trading-board.png`).
