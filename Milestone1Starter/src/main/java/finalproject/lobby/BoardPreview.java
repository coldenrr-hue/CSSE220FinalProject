package finalproject.lobby;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Draws the fixed board preview; contains no networking or game rules. */
public final class BoardPreview extends StackPane {
    public BoardPreview() {
        Canvas board = new Canvas(640, 530);
        drawBoard(board);
        getChildren().add(board);
        setMinSize(300, 280);
        setStyle("-fx-background-color: #214e63; -fx-background-radius: 18;");
        Runnable scale = () -> {
            double size = Math.min(getWidth() / 640, getHeight() / 530);
            board.setScaleX(size);
            board.setScaleY(size);
        };
        widthProperty().addListener((o, a, b) -> scale.run());
        heightProperty().addListener((o, a, b) -> scale.run());
    }

    private static void drawBoard(Canvas canvas) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        String[] names = {"Forest", "Pasture", "Fields", "Hills", "Mountains", "Pasture", "Forest",
                "Fields", "Hills", "Desert", "Mountains", "Forest", "Fields", "Pasture", "Hills", "Forest",
                "Mountains", "Fields", "Pasture"};
        String[] colors = {"#46755b", "#92b871", "#dfbd65", "#bc795c", "#87959e", "#92b871", "#46755b",
                "#dfbd65", "#bc795c", "#d9c394", "#87959e", "#46755b", "#dfbd65", "#92b871", "#bc795c", "#46755b",
                "#87959e", "#dfbd65", "#92b871"};
        int[] numbers = {5, 2, 6, 3, 8, 10, 9, 12, 11, 0, 4, 8, 10, 9, 4, 5, 6, 3, 11};
        int tile = 0;
        double radius = 53;
        for (int row = -2; row <= 2; row++) {
            for (int col = Math.max(-2, -row - 2); col <= Math.min(2, -row + 2); col++) {
                double x = 320 + Math.sqrt(3) * radius * (col + row / 2.0), y = 265 + 1.5 * radius * row;
                double[] xs = new double[6], ys = new double[6];
                for (int k = 0; k < 6; k++) {
                    double angle = Math.toRadians(60 * k - 30);
                    xs[k] = x + (radius - 2) * Math.cos(angle); ys[k] = y + (radius - 2) * Math.sin(angle);
                }
                g.setFill(Color.web(colors[tile])); g.fillPolygon(xs, ys, 6);
                g.setStroke(Color.web("#f2dab0")); g.setLineWidth(2); g.strokePolygon(xs, ys, 6);
                g.setFill(Color.web("#182d32")); g.setFont(Font.font("Segoe UI", 12));
                g.setTextAlign(javafx.scene.text.TextAlignment.CENTER); g.fillText(names[tile], x, y - 15);
                if (numbers[tile] != 0) {
                    g.setFill(Color.web("#f4e5c4")); g.fillOval(x - 16, y - 5, 32, 32);
                    g.setFill(numbers[tile] == 6 || numbers[tile] == 8 ? Color.web("#b64236") : Color.web("#253744"));
                    g.setFont(Font.font("Segoe UI", 19)); g.fillText(Integer.toString(numbers[tile]), x, y + 18);
                }
                tile++;
            }
        }
    }
}

