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
- `network/GameStateCodec` version 3 carries the same complete snapshot to all clients.

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

Setup and shared rolls work. Normal-play building, production from dice, robber
rules, trades, development cards and victory conditions remain to be implemented.
Guest disconnection cancels the match and returns players to an unready lobby.
