# Shared board and turn prototype

Run the normal Maven goal `compile javafx:run` for one window, or open all four
test windows with:

```text
compile javafx:run -Dapp.mainClass=finalproject.LocalLobbyTestApp
```

In the host window click **Host a table**; click **Join table** in the three guest
windows. Mark every player ready and start from the host. The host generates one
random board and shuffles player order, then broadcasts the full snapshot. Every
window shows the same 19 tiles, number tokens, order, and active player.

The lobby displays a decorative tile illustration. Starting a game replaces the
whole lobby with a dedicated playing screen: centered board, active player and
turn order, plus a player/chat sidebar. Connection fields and ready/start controls
are absent from the playing screen. The normal one-window launcher enters full
screen automatically. Press Esc or **Toggle full screen** to leave full screen
without leaving the game. **Leave game** returns to the lobby (and ends the table
for everyone when used by the host). A guest disconnect also returns the other
players to the lobby. Chat history survives screen changes.

The four-window test launcher keeps its windows windowed so they can share one
display. Each still switches to the dedicated playing screen, and you can use
**Toggle full screen** manually in any one of them.

The four player IDs remain attached to their lobby seats, independently of the
random turn order. The first player is selected by shuffling, not by dice rolls.
Setup follows forward order and then reverse order. **Advance setup (test)**
only demonstrates that sequence: it does not place or validate settlements or
roads. After both setup rounds, **End turn** cycles through the player order.
Only the current player can advance. Requests include a state revision so a
double click or a delayed request cannot skip an additional turn.

Resource counts: four forests, pastures and fields each; three hills and mountains
each; one desert. The desert has no number token. Other tokens are shuffled with
the usual 18-token counts, keeping 6/8 tokens off adjacent hexes. This randomized
number layout is a project choice rather than the printed spiral token layout.
Reference: https://www.catan.com/understand-catan/game-rules

## Artwork

Tiles deliberately have neutral, blank backgrounds. Add PNGs under
`src/main/resources/tiles/` named `forest.png`, `pasture.png`, `fields.png`,
`hills.png`, `mountains.png`, and `desert.png`. Labels/tokens remain over the images.
See that folder's README. No artwork is required to run or test.

## Class responsibilities

- `game/Tile`, `ResourceType`, and `Board`: immutable board data and coordinates.
- `game/BoardGenerator`: host-side randomized resource and number layout.
- `game/TurnManager`: setup sequence and normal turn advancement.
- `game/GameState` and `GamePhase`: public board and turn snapshot.
- `network/GameStateCodec`: versioned snapshot fields using the existing Wire protocol.
- `game/view/HexTileView`: placeholder, optional image, labels, and number token.
- `game/view/HexBoardView`: tile positioning and resizing.
- `game/view/GameBoardView`: current player, player order, board, and advance button.
- `game/view/PlayingScreen`: dedicated playing layout, player roster, chat dock,
  full-screen toggle, and leave-game action.
- `lobby/LobbyArtwork`: decorative tile illustration shown only in the lobby.
- `lobby/LobbyView`: switches screens while preserving the chat and connection.

`LobbyServer` owns game data and validates `END_TURN` against the sender's actual
connection and the current revision. `LobbyController` decodes snapshots and
updates `LobbyView` on the JavaFX thread. Chat and the original connection remain
active during the game. All laptops must run the updated project.

## Remaining teammate work

Settlement/road positions, legal placement, dice, resources, trades, development
cards, and winning are not implemented. Add those rules on the server. Replace
the temporary setup advancement with validated placement commands before using
this for a complete match. A guest disconnect still cancels the prototype game
and returns everyone to an unready lobby; restarting creates a fresh board.

## Automated checks

From the project folder run `mvn test-compile`, then on Windows:

```text
java -ea -cp "target/classes;target/test-classes" finalproject.game.GameModelTest
java -ea -cp "target/classes;target/test-classes" finalproject.network.LobbyIntegrationTest
```

Use `:` instead of `;` on macOS/Linux. These are standalone executable tests;
`mvn test` alone does not run them.
