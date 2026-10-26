# Local multiplayer lobby

The Maven project root is `Milestone1Starter` (the folder containing `pom.xml`).
The entry class remains `finalproject.StarterApp`; its superclass `LanLobbyApp`
creates the window and connects the view and controller. Java 21 and JavaFX 21
are required.

## Test on one laptop (four windows)

In IntelliJ, open the Maven tool window, choose **Execute Maven Goal**, and run:

```text
compile javafx:run -Dapp.mainClass=finalproject.LocalLobbyTestApp
```

Or, with Maven on your PATH, run this from the project folder:

```text
mvn compile javafx:run -Dapp.mainClass=finalproject.LocalLobbyTestApp
```

This opens four independent lobby windows with unique names and `127.0.0.1`
prefilled. Use the window titles or your taskbar to switch between them.

1. In **Host laptop**, click **Host a table**.
2. In each **Guest laptop** window, click **Join table**.
3. Confirm all windows list the same four players. Send a chat message and
   confirm it appears in every window.
4. Click **Mark ready** in each window. Only the host's **Start game** button
   should become enabled, and only after all four players are ready.
5. Click **Start game** on the host. Every window should report the game handoff.
   Catan gameplay itself is your teammates' part.
6. Try **Leave table** on a guest. The other windows should show an open seat
   and reset readiness. Rejoin to fill it. Leaving as host disconnects everyone.

Close all four windows to stop the launcher. If port 22000 is already occupied,
choose another port in all four windows before connecting. Use the normal
`clean javafx:run` goal for a single player window. This test uses real TCP
connections on loopback; you still need a four-laptop check for Wi-Fi/firewall issues.

## Run on four laptops

1. Connect all four laptops to the same Wi-Fi or Ethernet network. Each laptop
   needs the project and its JavaFX dependencies downloaded before offline use.
2. Open this folder in IntelliJ and run the Maven goal `clean javafx:run`.
   With Maven on your PATH, `mvn clean javafx:run` also works from this folder.
3. On the host laptop, enter a player name and click **Host a table**. The app
   lists local IPv4 addresses and the port (default TCP 22000). Share the address
   for the actual Wi-Fi/Ethernet interface; VPN and virtual adapters may also appear.
4. On each guest laptop, enter a unique name, the host's address, and the same
   port, then click **Join table**. Do not use `localhost` on another laptop.
5. All four players click **Mark ready**, including the host. Only the host can
   click **Start game**. All four screens receive the same start notification.
6. Chat works throughout the lobby. **Leave table** releases a guest seat.
   Leaving as host closes the table for everyone.

Allow Java through the host's firewall on your private network if prompted. No
router port forwarding is needed. Networks with client isolation (often guest or
school Wi-Fi) can prevent laptops from reaching each other; try an allowed local
network or hotspot. A failed join reports an error without freezing the window.

For testing on one laptop, run four app instances, host in the first, and have
the others join `127.0.0.1`. Use different player names. The host must keep running.

## Teammate integration

- `LanLobbyApp.java` only sets up the window and provides `onGameStarted()`.
- `lobby/LobbyController.java` handles connection lifecycle, outgoing commands,
  and incoming events, including marshaling callbacks onto the JavaFX thread.
- `lobby/LobbyView.java` arranges the screen. `ConnectionPanel`, `PlayerPanel`,
  and `ChatPanel` each own their controls; `BoardPreview` draws the sample board.
- `lobby/LobbyState.java` holds the typed player snapshot and readiness checks.
- `network/NetworkAddresses.java` finds host addresses to share with guests.
- `network/LobbyServer.java` owns the four seats, host identity, player names,
  readiness, and start permission. The host also joins through `LobbyClient`,
  so all four screens consume the same authoritative lobby updates.
- `network/LobbyClient.java` owns a persistent TCP connection. Its callbacks run
  on a background thread. `LobbyController` marshals updates with `Platform.runLater`.
- `network/Wire.java` frames UTF-8 fields using `DataInputStream.readUTF` /
  `DataOutputStream.writeUTF`, with Base64 field encoding. It never deserializes
  Java objects. The first message is `JOIN, 1, name, hostToken`; guests use an
  empty token. Server events are `WELCOME`, `STATE`, `CHAT`, `ERROR`, `STARTED`.
  Client commands are `READY`, `CHAT`, and host-only `START`.
- `LanLobbyApp.onGameStarted()` is the hook for opening your team's game
  controller on each laptop. The board is a fixed visual preview, not shared
  gameplay state. Extend the server protocol with validated game commands and
  authoritative game-state snapshots when the rules controller is ready.

The current implementation handles the lobby and start handoff only. It does
not implement Catan rules, turn synchronization, saved matches, automatic host
discovery, or resuming an interrupted match. If a guest disconnects, everyone
returns to an unready lobby and a replacement can join. Loss of the host ends
the session. A physical network outage may take the operating system's TCP
timeout to detect. This protocol is intended for a trusted LAN and is unencrypted.

## Socket integration test

From the project folder, with Java 21 and Maven configured:

```text
mvn test-compile
java -ea -cp "target/classes;target/test-classes" finalproject.network.LobbyIntegrationTest
```

On macOS/Linux, replace the classpath semicolon with a colon. The standalone
test opens real loopback sockets and checks four seats, duplicate names, a fifth
player, Unicode chat, readiness, guest start rejection, host start delivery,
late joins, guest replacement, and host shutdown. It throws on any failure and
prints `PASS` on success. This is a standalone test rather than a JUnit test;
`mvn test` alone does not execute it.
