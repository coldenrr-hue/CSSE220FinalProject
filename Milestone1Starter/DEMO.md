# Integrated progress demo

This checkout combines Colden's LAN/full-screen game, Andrew's setup placement
and redesigned board, and George's dice class, dice artwork, and cup animation.

## Run the demo

Use Java 21. In IntelliJ's Maven tool window, execute:

```text
compile javafx:run -Dapp.mainClass=finalproject.LocalLobbyTestApp
```

This opens four windows on one laptop. Host in **Host laptop**, join in the three
guest windows, mark everyone ready, then click **Start game** on the host.

For a real four-laptop demo, each laptop runs `compile javafx:run`. The host
shares its current Wi-Fi/Ethernet IPv4 address and TCP port 22000. Guests enter
that address (not localhost). Each laptop must run this same merged version.
Allow Java on the host's private network if Windows Firewall prompts you.

## What to show

1. The lobby switches to Andrew's ocean-framed board and full-screen play view.
2. On the active player's window, click a glowing corner to place a settlement.
3. Click a glowing edge beside it to place the connecting road. The turn advances
   automatically. Everyone sees the same pieces and score changes.
4. Complete the forward and reverse setup rounds. Each player finishes with two
   settlements, two roads, two settlement VP, and the starting cards earned by
   their second settlement.
5. During normal play, the active player clicks **Roll dice**. The host uses
   George's Dice class to choose both values. Every laptop receives those same
   values and plays George's cup-shake, lifting reveal, grow, and fade animation. The cup
   uses frames extracted from his original clip for reliable playback. The
   dice gradually appear from underneath the cup as it lifts. The Actions panel
   says **Rolling…** until the dice are fully uncovered, then shows the numbers
   and total. End turn is disabled until that reveal.
6. Click **End turn**. Dice reset for the next player. Other players cannot roll
   or end someone else's turn, and a player cannot roll twice in one turn.
7. Chat still works. Leaving as a guest returns everyone to the lobby; leaving
   as host disconnects the entire table.

The normal launcher automatically enters full screen. The four-window launcher
stays windowed so its sessions can share a display. Use **Full screen** or Esc to
switch without leaving a match.

## Scope of this progress build

Play through setup, then roll, trade, and build. On a roll of seven, players with
at least seven resource cards choose half their cards to discard, rounded down.
After all discards, the roller selects a new tile and steals one random card if
an eligible opponent borders that tile. Knights can also move the robber.
Development cards, Longest Road, Largest Army, and winning at ten points are supported.

## Validation

From this folder, with Maven and Java 21:

```text
mvn test-compile
java -ea -cp "target/classes;target/test-classes" finalproject.game.GameModelTest
java -ea -cp "target/classes;target/test-classes" finalproject.game.SetupRulesTest
java -ea -cp "target/classes;target/test-classes" finalproject.network.LobbyIntegrationTest
```

On macOS/Linux use `:` instead of `;` in the classpaths. These are standalone
checks; `mvn test` alone does not execute them. The socket test plays a complete
four-player setup followed by four dice turns, checks invalid/stale moves and
rolls, and verifies disconnect/restart behavior.

## Integration choices

- Both original branch histories are retained as merges into Colden.
- StarterApp still launches the LAN app; George's separate starter screen was
  adapted into `game/view/DiceRollAnimation` rather than replacing the game.
- Andrew's GameState/PlayerAssets and placement commands remain authoritative on the host.
- `GAME_STATE` protocol version 7 includes the Longest Road holder.
- Maven adds `javafx-media`, retaining the original artifact name and both launchers.
- Andrew's per-user `.idea/workspace.xml` is excluded from the integration.
