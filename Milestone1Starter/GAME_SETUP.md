# Set up Catan

You can play with four people on the same Wi-Fi or Ethernet network, or run four player windows on one computer to try the game by yourself.

## 1. Get the game running

1. Install a **Java 21 JDK**. Download or clone the project and find the **Milestone1Starter** folder, which contains `pom.xml`.
2. Choose how to launch the game:
   - **IntelliJ IDEA.** Open **Milestone1Starter** as a project. Set the Project SDK and Maven runner JRE to Java 21. In the Maven tool window, choose **Execute Maven Goal** and enter `compile javafx:run`.
   - **Terminal.** Install Apache Maven and make sure `mvn -version` reports Java 21. Open a terminal in **Milestone1Starter** and run:

     ```text
     mvn compile javafx:run
     ```

Maven downloads the game's dependencies the first time, so allow extra time and keep an internet connection available. When the game opens, you should see the lobby.

## 2. Host or join a game

1. Have all four players launch the same version of the game and connect to the same local network.
2. The host enters a name, leaves the port at **22000**, and clicks **Host a table**. The host shares their local IPv4 address with the other players. On Windows, run `ipconfig` and find **IPv4 Address** under the connected Wi-Fi or Ethernet adapter.
3. Each guest enters a name, types the host's IPv4 address into the host field, leaves the port at **22000**, and clicks **Join table**.
4. Once all four names appear, everyone clicks **Mark ready**. The host then clicks **Start game**.

### Try it on one computer

Run this command from **Milestone1Starter**. In IntelliJ's **Execute Maven Goal** box, enter the same command without the leading `mvn`.

```text
mvn compile javafx:run -Dapp.mainClass=finalproject.LocalLobbyTestApp
```

Four windows open with names and connection details filled in. Click **Host a table** in the **Host laptop** window and **Join table** in the three guest windows. Mark all four players ready, then start the game from the host window. Switch between open windows to take each player's turn.

## 3. Place your starting pieces

1. When the board says it's your turn, click a glowing corner to place your first settlement. Then click a glowing edge next to it to place its road. The game passes the turn automatically.
2. After all four players place their first settlement and road, placement goes in reverse order. The last player in the first round goes first in the second round.
3. At the end of setup, each player has two settlements and two roads. You receive starting resource cards from the productive tiles touching your second settlement.
4. On your first regular turn, click **Roll dice**. After the reveal, use **Build** or **Trade** as needed, then click **End turn**.

## If you get stuck

- **The launcher reports a Java version error.** Check that Maven is using the Java 21 JDK. In a terminal, run `mvn -version`. In IntelliJ, check the Maven runner JRE.
- **A guest can't join.** Verify that everyone is on the same local network, the host has clicked **Host a table**, and the guest entered the host's local IPv4 address and matching port. The address `127.0.0.1` is for windows on the same computer. If Windows Firewall prompts the host, allow Java on the private network.
- **Start game is disabled.** Check that all four seats are filled and show **Ready**, then click **Start game** from the host window.
