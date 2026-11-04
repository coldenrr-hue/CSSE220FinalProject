package finalproject.game.view;

import finalproject.game.*;
import javafx.application.*;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import java.util.*;

/** UI smoke check and review screenshot with an incoming trade and all nine docks. */
public final class TradingViewTest extends Application {
    @Override public void start(Stage stage) {
        try {
            GameScreen screen = new GameScreen();
            List<String[]> sent = new ArrayList<>();
            screen.onCommand(sent::add);
            Board board = BoardGenerator.generate(new Random(42));
            PlayerAssets pieces = PlayerAssets.EMPTY.withCards(0, List.of(ResourceType.FOREST, ResourceType.FOREST))
                    .withCards(1, List.of(ResourceType.HILLS));
            var docks = HarborRules.forBoard(board);
            pieces = pieces.withSettlement(docks.get(0).a(), 0).withSettlement(docks.get(3).b(), 1);
            GameState state = new GameState(board, List.of(0,1,2,3), 0, GamePhase.PLAYING, 1, 20, pieces,
                    new DiceResult(3,4), new TradeOffer(0,1,List.of(ResourceType.FOREST),List.of(ResourceType.HILLS)));
            screen.render(state, 1, Map.of(0,"Alex",1,"Blair",2,"Casey",3,"Drew"));
            stage.setScene(new Scene(screen, 1440, 900)); stage.show();
            PauseTransition wait = new PauseTransition(Duration.seconds(6));
            wait.setOnFinished(e -> {
                try {
                    for (var dock : docks) check(screen.lookup("#harbor-" + dock.a()) != null, "Missing dock");
                    Button accept = screen.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Accept"))
                            .map(n -> (Button)n).findFirst().orElseThrow();
                    check(!accept.isDisabled(), "Recipient cannot accept");
                    accept.fire();
                    check(sent.size() == 1 && Arrays.equals(sent.getFirst(), new String[]{"TRADE_ACCEPT", "20"}), "Wrong acceptance command");
                    var image = screen.snapshot(null, null);
                    var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                        output.setRGB(x, y, image.getPixelReader().getArgb(x,y));
                    java.io.File file = new java.io.File("target/trading-board.png"); file.getParentFile().mkdirs();
                    javax.imageio.ImageIO.write(output, "png", file);
                    System.out.println("PASS: nine docks, recipient acceptance and screenshot");
                    stage.close(); Platform.exit();
                } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
            });
            wait.play();
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) { launch(args); }
}
