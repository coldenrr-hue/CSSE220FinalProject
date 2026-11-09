package finalproject.game.view;

import finalproject.game.Board;
import finalproject.game.BoardGraph;
import finalproject.game.GameState;
import finalproject.game.PlayerAssets;
import finalproject.game.ResourceType;
import finalproject.game.Tile;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;

import java.net.URL;
import java.util.EnumMap;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.Map;

/**
 * Draws the island inside a hexagon-shaped ocean frame and scales it to fit
 * whatever space it is given (window, full screen, lobby preview).
 *
 * Artwork is optional, like the rest of the project:
 *   /tiles/forest.png ... /tiles/desert.png   (ResourceType.imagePath())
 *   /board/ocean.png, /board/robber.png
 * Missing files fall back to plain colors.
 */
public class HexBoardView extends StackPane {

    // Drawing size before scaling. Tile PNGs are 346 x 400, so keep that ratio.
    public static final double TILE_W = 84;
    public static final double TILE_H = TILE_W * 400.0 / 346.0;
    private static final double FRAME_R = 2.5 * TILE_W + 70;   // room in the water for harbors
    public static final double VIEW_W = 2 * FRAME_R + 20;
    public static final double VIEW_H = Math.sqrt(3) * FRAME_R + 20;
    private static final double TOKEN_R = 18;

    private static final Map<ResourceType, Color> FALLBACK = new EnumMap<>(Map.of(
            ResourceType.FOREST, Color.web("#3f8f3a"), ResourceType.PASTURE, Color.web("#9ccc65"),
            ResourceType.FIELDS, Color.web("#f2c94c"), ResourceType.HILLS, Color.web("#c0603a"),
            ResourceType.MOUNTAINS, Color.web("#8d9aa3"), ResourceType.DESERT, Color.web("#ecd9a4")));

    /** Seat colors, shared with the lobby and the playing screen. */
    public static final String[] SEAT_COLORS = {"#e37663", "#72b4e2", "#e6bb59", "#a4c395"};

    private final Pane content = new Pane();
    private final Pane tileLayer = new Pane();     // ocean, tiles, numbers, robber
    private final Pane pieceLayer = new Pane();    // roads and settlements
    private final Pane spotLayer = new Pane();     // clickable highlights
    private final Group scaled = new Group(content);
    private final Map<ResourceType, Image> tileArt = new EnumMap<>(ResourceType.class);
    private final Image ocean = loadOptional("/board/ocean.png");
    private final Image robber = loadOptional("/board/robber.png");

    // Piece artwork by seat: /pieces/settlement_red.png, /pieces/road_red.png, ...
    private static final String[] SEAT_NAMES = {"red", "blue", "yellow", "green"};
    private final Image[] settlementArt = new Image[4];
    private final Image[] roadArt = new Image[4];
    private final Image[] cityArt = new Image[4];

    private GameState state;
    private Board drawnBoard;

    public HexBoardView() {
        for (ResourceType type : ResourceType.values()) {
            Image img = loadOptional(type.imagePath());
            if (img != null) tileArt.put(type, img);
        }
        for (int seat = 0; seat < 4; seat++) {
            settlementArt[seat] = loadOptional("/pieces/settlement_" + SEAT_NAMES[seat] + ".png");
            cityArt[seat] = loadOptional("/pieces/city_" + SEAT_NAMES[seat] + ".png");
            roadArt[seat] = loadOptional("/pieces/road_" + SEAT_NAMES[seat] + ".png");
        }
        content.setPrefSize(VIEW_W, VIEW_H);
        content.getChildren().addAll(tileLayer, pieceLayer, spotLayer);
        getChildren().add(scaled);
        setMinSize(0, 0);
        setPrefSize(VIEW_W, VIEW_H);
        widthProperty().addListener((o, a, b) -> fit());
        heightProperty().addListener((o, a, b) -> fit());
        redraw();
    }

    /** The last game snapshot shown, or null before a game starts. */
    public GameState state() { return state; }

    /** Show a snapshot from the host. The tiles are only redrawn when the board actually changes. */
    public void render(GameState newState) {
        state = newState;
        renderTerrain(newState == null ? null : newState.board());
        drawPieces();
    }

    /** Show just the terrain, without game pieces. */
    public void render(Board board) {
        state = null;
        renderTerrain(board);
        drawPieces();
    }

    private void renderTerrain(Board board) {
        if (board != null && board.equals(drawnBoard)) return;
        drawnBoard = board;
        redraw();
    }

    /** Back to the blank island (lobby / between games). */
    public void clear() {
        state = null;
        drawnBoard = null;
        redraw();
        pieceLayer.getChildren().clear();
        clearSpots();
    }

    // ---------- positions (for teammates drawing settlements, roads, harbors) ----------

    public double tileCenterX(int q, int r) { return VIEW_W / 2 + TILE_W * (q + r / 2.0); }
    public double tileCenterY(int r)        { return VIEW_H / 2 + TILE_H * 0.75 * r; }

    public double vertexX(BoardGraph.Vertex v) { return VIEW_W / 2 + v.x() * TILE_H / 2; }
    public double vertexY(BoardGraph.Vertex v) { return VIEW_H / 2 + v.y() * TILE_H / 2; }

    // ---------- clickable spots for placing pieces ----------

    /** Highlights these corners; clicking one calls onClick with its vertex ID. */
    public void showSettlementSpots(List<Integer> vertexIds, IntConsumer onClick) {
        clearSpots();
        if (drawnBoard == null) return;
        BoardGraph graph = BoardGraph.of(drawnBoard);
        for (int id : vertexIds) {
            BoardGraph.Vertex v = graph.vertex(id);
            Circle spot = new Circle(vertexX(v), vertexY(v), 9, Color.web("#fff3c4", 0.55));
            spot.setId("settlement-spot-" + id);
            spot.setStroke(Color.web("#f3cc80"));
            spot.setStrokeWidth(2.5);
            spot.setCursor(Cursor.HAND);
            spot.setOnMouseEntered(e -> { spot.setRadius(12); spot.setFill(Color.web("#fff3c4", 0.95)); });
            spot.setOnMouseExited(e -> { spot.setRadius(9); spot.setFill(Color.web("#fff3c4", 0.55)); });
            spot.setOnMouseClicked(e -> onClick.accept(id));
            spotLayer.getChildren().add(spot);
        }
    }

    /** Highlights these edges; clicking one calls onClick with its edge ID. */
    public void showRoadSpots(List<Integer> edgeIds, IntConsumer onClick) {
        clearSpots();
        if (drawnBoard == null) return;
        BoardGraph graph = BoardGraph.of(drawnBoard);
        for (int id : edgeIds) {
            BoardGraph.Edge e = graph.edge(id);
            Line spot = edgeLine(graph, e, 0.18);
            spot.setId("road-spot-" + id);
            spot.setStroke(Color.web("#fff3c4", 0.6));
            spot.setStrokeWidth(9);
            spot.setCursor(Cursor.HAND);
            spot.setOnMouseEntered(ev -> spot.setStroke(Color.web("#fff3c4", 1.0)));
            spot.setOnMouseExited(ev -> spot.setStroke(Color.web("#fff3c4", 0.6)));
            spot.setOnMouseClicked(ev -> onClick.accept(id));
            spotLayer.getChildren().add(spot);
        }
    }

    /** Large rings leave the existing settlement visible while selecting a city upgrade. */
    public void showCitySpots(List<Integer> vertexIds, IntConsumer onClick) {
        showSettlementSpots(vertexIds, onClick);
        for (Node node : spotLayer.getChildren()) {
            Circle ring = (Circle) node;
            ring.setId(ring.getId().replace("settlement-spot-", "city-spot-"));
            ring.setRadius(23); ring.setFill(Color.web("#f3cc80", 0.12));
            ring.setStrokeWidth(3);
            ring.setOnMouseEntered(e -> { ring.setRadius(25); ring.setFill(Color.web("#f3cc80", 0.3)); });
            ring.setOnMouseExited(e -> { ring.setRadius(23); ring.setFill(Color.web("#f3cc80", 0.12)); });
            javafx.scene.control.Tooltip.install(ring, new javafx.scene.control.Tooltip("Upgrade to city: 3 ore + 2 grain"));
        }
    }

    /** Highlights these tiles (moving the robber); clicking one calls onClick with its tile ID. */
    public void showTileSpots(List<Integer> tileIds, IntConsumer onClick) {
        clearSpots();
        if (drawnBoard == null) return;
        for (int id : tileIds) {
            Tile tile = tileById(id);
            Polygon spot = hexShape(tileCenterX(tile.q(), tile.r()), tileCenterY(tile.r()));
            spot.setId("tile-spot-" + id);
            spot.setScaleX(0.9);
            spot.setScaleY(0.9);
            spot.setFill(Color.web("#fff3c4", 0.18));
            spot.setStroke(Color.web("#f3cc80"));
            spot.setStrokeWidth(3);
            spot.setCursor(Cursor.HAND);
            spot.setOnMouseEntered(e -> spot.setFill(Color.web("#fff3c4", 0.45)));
            spot.setOnMouseExited(e -> spot.setFill(Color.web("#fff3c4", 0.18)));
            spot.setOnMouseClicked(e -> onClick.accept(id));
            spotLayer.getChildren().add(spot);
        }
    }

    public void clearSpots() { spotLayer.getChildren().clear(); }

    // ---------- drawing ----------

    private void fit() {
        double s = Math.min(getWidth() / VIEW_W, getHeight() / VIEW_H);
        s = Math.max(0.2, s);
        scaled.setScaleX(s);
        scaled.setScaleY(s);
    }

    private void redraw() {
        tileLayer.getChildren().clear();
        tileLayer.getChildren().add(frame());

        if (drawnBoard == null) {           // blank island for the lobby / waiting screen
            for (int r = -2; r <= 2; r++) {
                for (int q = Math.max(-2, -r - 2); q <= Math.min(2, -r + 2); q++) {
                    Polygon hex = hexShape(tileCenterX(q, r), tileCenterY(r));
                    hex.setFill(Color.web("#e7ddbd"));
                    hex.setStroke(Color.web("#b9a47a"));
                    hex.setStrokeWidth(2);
                    tileLayer.getChildren().add(hex);
                }
            }
            return;
        }

        for (Tile tile : drawnBoard.tiles()) {
            double cx = tileCenterX(tile.q(), tile.r());
            double cy = tileCenterY(tile.r());
            tileLayer.getChildren().add(tileNode(tile.resource(), cx, cy));
            if (tile.number() != 0) tileLayer.getChildren().add(token(tile.number(), cx, cy));
        }
        drawHarbors();
    }

    private Tile tileById(int tileId) {
        return drawnBoard.tiles().stream().filter(t -> t.id() == tileId).findFirst().orElseThrow();
    }

    /** Scalable dock illustrations: wooden piers connect both eligible coastal corners. */
    private void drawHarbors() {
        BoardGraph graph = BoardGraph.of(drawnBoard);
        for (var harbor : finalproject.game.HarborRules.forBoard(drawnBoard)) {
            var a = graph.vertex(harbor.a()); var b = graph.vertex(harbor.b());
            double mx = (vertexX(a) + vertexX(b)) / 2, my = (vertexY(a) + vertexY(b)) / 2;
            double dx = mx - VIEW_W / 2, dy = my - VIEW_H / 2;
            double length = Math.hypot(dx, dy); dx /= length; dy /= length;
            double x = mx + dx * 30, y = my + dy * 30;
            Group dock = new Group(); dock.setId("harbor-" + harbor.a());
            for (var v : List.of(a, b)) {
                double vx = vertexX(v), vy = vertexY(v);
                Line pier = new Line(vx, vy, x, y);
                pier.setStroke(Color.web("#593a24")); pier.setStrokeWidth(9);
                dock.getChildren().add(pier);
                double px = x - vx, py = y - vy, distance = Math.hypot(px, py);
                for (int step = 1; step <= 5; step++) {
                    double cx = vx + px * step / 6, cy = vy + py * step / 6;
                    Line plank = new Line(cx - py / distance * 4, cy + px / distance * 4,
                            cx + py / distance * 4, cy - px / distance * 4);
                    plank.setStroke(Color.web("#d6aa6b")); plank.setStrokeWidth(3);
                    dock.getChildren().add(plank);
                }
            }
            Circle badge = new Circle(x, y, 19, Color.web("#f7ecd0"));
            badge.setStroke(Color.web("#6e4528")); badge.setStrokeWidth(2);
            dock.getChildren().add(badge);
            String iconName = harbor.resource().cardName().toLowerCase(java.util.Locale.ROOT);
            Image icon = loadOptional("/icons/" + iconName + ".png");
            if (icon != null) {
                ImageView view = new ImageView(icon); view.setFitWidth(19); view.setFitHeight(19);
                view.setLayoutX(x - 9.5); view.setLayoutY(y - 17); dock.getChildren().add(view);
            } else {
                Polygon hull = new Polygon(x - 12, y - 5, x + 12, y - 5, x + 7, y, x - 7, y);
                hull.setFill(Color.web("#8b5838"));
                Polygon sail = new Polygon(x, y - 16, x, y - 6, x + 10, y - 6);
                sail.setFill(Color.web("#ba5946")); dock.getChildren().addAll(hull, sail);
            }
            Text ratio = new Text(x - 10, y + 13, harbor.label());
            ratio.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-fill: #35291d;");
            dock.getChildren().add(ratio);
            javafx.scene.control.Tooltip.install(dock, new javafx.scene.control.Tooltip(
                    harbor.resource() == ResourceType.DESERT ? "General dock: trade 3 identical resources for 1. Build on either connected corner."
                    : harbor.resource().cardName() + " dock: trade 2 for 1. Build on either connected corner."));
            tileLayer.getChildren().add(dock);
        }
    }

    /** Roads and settlements from the current snapshot. */
    private void drawPieces() {
        pieceLayer.getChildren().clear();
        if (drawnBoard == null) return;
        if (state == null) {
            drawRobber(GameState.desertTile(drawnBoard));
            return;
        }
        BoardGraph graph = BoardGraph.of(drawnBoard);
        PlayerAssets pieces = state.assets();

        pieces.roads().forEach((edgeId, player) ->
                pieceLayer.getChildren().add(roadNode(graph, graph.edge(edgeId), player)));
        pieces.settlements().forEach((vertexId, player) -> {
            BoardGraph.Vertex v = graph.vertex(vertexId);
            if (pieces.cities().contains(vertexId)) {
                if (cityArt[player] != null) {
                    ImageView city = new ImageView(cityArt[player]);
                    city.setFitWidth(40); city.setFitHeight(40);
                    city.setLayoutX(vertexX(v) - 20); city.setLayoutY(vertexY(v) - 25);
                    pieceLayer.getChildren().add(city);
                } else {
                    Group city = new Group(settlementShape(vertexX(v) - 5, vertexY(v), SEAT_COLORS[player]),
                            settlementShape(vertexX(v) + 7, vertexY(v) - 5, SEAT_COLORS[player]));
                    pieceLayer.getChildren().add(city);
                }
            } else pieceLayer.getChildren().add(settlementNode(vertexX(v), vertexY(v), player));
        });
        drawRobber(state.robberTile());
    }

    private void drawRobber(int tileId) {
        Tile tile = tileById(tileId);
        pieceLayer.getChildren().add(robberNode(tileCenterX(tile.q(), tile.r()), tileCenterY(tile.r())));
    }

    /** Road image stretched along the edge and rotated to match it (plain line if no image). */
    private Node roadNode(BoardGraph graph, BoardGraph.Edge e, int player) {
        if (roadArt[player] == null) {
            Line road = edgeLine(graph, e, 0.14);
            road.setStroke(Color.web(SEAT_COLORS[player]));
            road.setStrokeWidth(7);
            return road;
        }
        BoardGraph.Vertex a = graph.vertex(e.a()), b = graph.vertex(e.b());
        double ax = vertexX(a), ay = vertexY(a), bx = vertexX(b), by = vertexY(b);
        double length = Math.hypot(bx - ax, by - ay) * 0.72;   // leave room for settlements at the ends
        double thickness = 11;
        ImageView road = new ImageView(roadArt[player]);
        road.setFitWidth(length);
        road.setFitHeight(thickness);
        road.setLayoutX((ax + bx) / 2 - length / 2);
        road.setLayoutY((ay + by) / 2 - thickness / 2);
        road.setRotate(Math.toDegrees(Math.atan2(by - ay, bx - ax)));
        return road;
    }

    /** Settlement image standing on the corner (house shape if no image). */
    private Node settlementNode(double x, double y, int player) {
        if (settlementArt[player] == null) return settlementShape(x, y, SEAT_COLORS[player]);
        double size = 34;
        ImageView house = new ImageView(settlementArt[player]);
        house.setFitWidth(size);
        house.setFitHeight(size);
        house.setLayoutX(x - size / 2);
        house.setLayoutY(y - size * 0.62);   // the house's base sits on the corner
        return house;
    }

    /** A line along an edge, pulled in from both corners by the given fraction. */
    private Line edgeLine(BoardGraph graph, BoardGraph.Edge e, double inset) {
        BoardGraph.Vertex a = graph.vertex(e.a()), b = graph.vertex(e.b());
        double ax = vertexX(a), ay = vertexY(a), bx = vertexX(b), by = vertexY(b);
        Line line = new Line(ax + (bx - ax) * inset, ay + (by - ay) * inset,
                bx + (ax - bx) * inset, by + (ay - by) * inset);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        return line;
    }

    /** A small house shape centered on a corner. */
    private Polygon settlementShape(double x, double y, String color) {
        Polygon house = new Polygon(
                x - 9, y + 8,  x + 9, y + 8,  x + 9, y - 2,
                x, y - 11,     x - 9, y - 2);
        house.setFill(Color.web(color));
        house.setStroke(Color.web("#1b1b1b"));
        house.setStrokeWidth(2);
        return house;
    }

    private Polygon frame() {
        Polygon frame = new Polygon();
        for (int i = 0; i < 6; i++) {            // flat-topped: corners point left and right
            double a = Math.toRadians(60 * i);
            frame.getPoints().addAll(VIEW_W / 2 + FRAME_R * Math.cos(a), VIEW_H / 2 + FRAME_R * Math.sin(a));
        }
        frame.setFill(ocean != null ? new ImagePattern(ocean) : Color.web("#2f6fa8"));
        frame.setStroke(Color.web("#c9a46a"));
        frame.setStrokeWidth(10);
        return frame;
    }

    private Polygon hexShape(double cx, double cy) {
        Polygon hex = new Polygon();
        double r = TILE_H / 2;
        for (int i = 0; i < 6; i++) {            // pointy-topped tiles
            double a = Math.toRadians(60 * i - 90);
            hex.getPoints().addAll(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        return hex;
    }

    private Node tileNode(ResourceType type, double cx, double cy) {
        Image art = tileArt.get(type);
        if (art == null) {
            Polygon hex = hexShape(cx, cy);
            hex.setFill(FALLBACK.get(type));
            hex.setStroke(Color.web("#3b2a1a"));
            hex.setStrokeWidth(2);
            return hex;
        }
        ImageView view = new ImageView(art);
        view.setFitWidth(TILE_W);
        view.setFitHeight(TILE_H);
        view.setLayoutX(cx - TILE_W / 2);
        view.setLayoutY(cy - TILE_H / 2);
        return view;
    }

    private StackPane token(int number, double cx, double cy) {
        Circle circle = new Circle(TOKEN_R, Color.web("#f7ecd0"));
        circle.setStroke(Color.web("#3b2a1a"));
        circle.setStrokeWidth(2);

        String color = (number == 6 || number == 8) ? "#c62828" : "#1b1b1b";
        Text num = new Text(Integer.toString(number));
        num.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-fill: " + color + ";");
        Text pips = new Text("•".repeat(6 - Math.abs(7 - number)));   // likelihood dots
        pips.setStyle("-fx-font-size: 8px; -fx-fill: " + color + ";");

        VBox label = new VBox(-4, num, pips);
        label.setAlignment(Pos.CENTER);
        StackPane token = new StackPane(circle, label);
        token.setLayoutX(cx - TOKEN_R);
        token.setLayoutY(cy - TOKEN_R);
        return token;
    }

    private Node robberNode(double cx, double cy) {
        if (robber == null) {
            Circle c = new Circle(cx, cy, 14, Color.web("#2b2b2b"));
            c.setStroke(Color.WHITE);
            c.setStrokeWidth(2);
            return c;
        }
        ImageView view = new ImageView(robber);
        view.setFitHeight(54);
        view.setPreserveRatio(true);
        view.setLayoutX(cx - 54 * 180.0 / 256.0 / 2);   // robber.png is 180 x 256
        view.setLayoutY(cy - 36);
        return view;
    }

    private Image loadOptional(String path) {
        URL url = getClass().getResource(path);
        return url == null ? null : new Image(url.toExternalForm());
    }
}
