# Teammate code guide

Start with the part you want to change:

| Task | File under `src/main/java/finalproject/` |
| --- | --- |
| Change setup, building, trade or production rules | `game/GameRules.java` |
| Change a building's cost or piece limit | `GameRules.Build`, inside that same file |
| Store buildings, roads, hands or scores | `game/PlayerAssets.java` |
| Change dock locations or discounts | `game/HarborRules.java` |
| Change resource names or icon paths | `game/ResourceType.java` |
| Change the main game layout or action buttons | `game/view/GameScreen.java` |
| Draw tiles, docks, pieces or placement highlights | `game/view/HexBoardView.java` |
| Change the building or trading window | `game/view/BuildDialog.java` or `TradeDialog.java` |
| Handle messages and validate the sending player | `network/LobbyServer.java` |
| Change how snapshots travel between computers | `network/GameStateCodec.java` |
| Connect the lobby UI to network messages | `lobby/LobbyController.java` |

## How an action reaches the board

1. `GameScreen` or a dialog collects a player's choice.
2. `LobbyController` sends the command through `LobbyClient`.
3. `LobbyServer` checks the sender and snapshot revision, then calls `GameRules`.
4. The rules return updated immutable `PlayerAssets`; rejected actions leave the old data untouched.
5. The server broadcasts `GameState` through `GameStateCodec`.
6. `LobbyView` passes the snapshot to `GameScreen`, which redraws the hand, scores and board.

The UI can use the rules to highlight legal moves, but the host checks every move
again. Do not deduct resources in button handlers.

## Important model conventions

- `GameState.assets()` contains every player's buildings, roads and cards.
- `PlayerAssets.settlements()` maps occupied building vertices to owners. It includes
  cities; `cities()` identifies which vertices were upgraded. This keeps road blocking,
  dock ownership and production consistent.
- `settlementCount(player)` counts only physical settlements; `cityCount(player)`
  counts cities. Upgrading returns a settlement to the player's supply.
- `GameRules.Build.CITY.cost()` is the shared cost list. `remainingPieces(...)` is
  the shared supply calculation for both rules and the menu.
- Setup placement methods contain `Setup` in their names. Those placements are free;
  normal play uses `GameRules.build(...)`.
- Resource enum constants describe terrain (`FOREST`); `cardName()` describes the
  card (`Lumber`). Keep enum identifiers stable because snapshots send their names.
- `HarborRules.forBoard(...)` returns a cached immutable dock layout. Both drawing
  and discount calculations use it.
- Revision numbers prevent a second click or outdated offer from being applied twice.

## What changed in this refactor

| Previous name | Current name |
| --- | --- |
| `SetupRules` and `PlayRules` | `GameRules` |
| `Pieces` | `PlayerAssets` |
| `PlayingScreen` | `GameScreen` |
| `Harbors` | `HarborRules` |
| `GameState.pieces()` / `withPieces(...)` | `assets()` / `withAssets(...)` |
| `PlayRules.owns(...)` | `GameRules.hasResources(...)` |
| `PlayRules.accept(...)` | `GameRules.acceptTrade(...)` |
| `PlayRules.produce(...)` | `GameRules.collectDiceResources(...)` |
| `TradeDialog.name(...)` / `cards(...)` | `ResourceType.cardName()` / `describeCards(...)` |

Removed unused prototypes: `GameBoardView`, `HexTileView`, `BoardPreview`,
`ResourceCard`, and `ConfirmButton`. The active board renderer and server-managed
resource hands already provide those functions. Six fewer main-source classes
remain after combining the two rules classes. The two dialogs stay separate so
each screen remains easy to find.

The Maven entry point is still `finalproject.StarterApp`. The version-4 network
field order and command strings are unchanged. Use a clean build after pulling
the refactor so old compiled class files do not hide missing imports.

## Verification

The project uses standalone test classes with `main` methods, rather than JUnit.
Compile with `mvn clean test-compile`, then run these with assertions enabled and
`target/classes;target/test-classes` on the Windows classpath:

- `finalproject.game.GameModelTest`: board generation and snapshots.
- `finalproject.game.SetupRulesTest`: complete setup games (test name retained).
- `finalproject.game.TradingBuildingTest`: trades, costs, placement, cities and production.
- `finalproject.network.LobbyIntegrationTest`: four real clients, authorization and synchronization.
- `finalproject.game.SevenResolutionTest` covers discard counts, blocked actions, and robber theft.
- `finalproject.game.DevelopmentRulesTest` covers the development deck and card effects.
- `finalproject.game.MatchResultTest` covers scoring and final standings.

`finalproject.game.view.DiceAnimationTest` and `TradingViewTest` also require the
JavaFX controls/media modules and `src/main/resources` on the runtime classpath.
