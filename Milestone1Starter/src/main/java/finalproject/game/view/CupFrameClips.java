package finalproject.game.view;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.shape.Polygon;
import java.util.ArrayList;
import java.util.List;

/** Vector silhouettes hide each frame's matte while preserving the dark cup itself. */
final class CupFrameClips {
    private final List<double[]> outlines = new ArrayList<>();

    CupFrameClips(Image atlas, int columns, int frameWidth, int frameHeight,
                  int frameCount, double displayWidth, double displayHeight) {
        PixelReader pixels = atlas.getPixelReader();
        for (int frame = 0; frame < frameCount; frame++) {
            int originX = frame % columns * frameWidth, originY = frame / columns * frameHeight;
            int matte = pixels.getArgb(originX, originY);
            List<double[]> rows = new ArrayList<>();
            for (int y = 0; y < frameHeight; y++) {
                int left = frameWidth, right = -1, count = 0;
                for (int x = 0; x < frameWidth; x++) {
                    int color = pixels.getArgb(originX + x, originY + y);
                    int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
                    int dr = red - ((matte >> 16) & 255), dg = green - ((matte >> 8) & 255), db = blue - (matte & 255);
                    if (Math.max(red, Math.max(green, blue)) > 32 && dr * dr + dg * dg + db * db > 18 * 18) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        count++;
                    }
                }
                if (count >= 6) rows.add(new double[]{left, right, y});
            }
            double[] points = new double[rows.size() * 4];
            int position = 0;
            for (double[] row : rows) {
                points[position++] = row[0] * displayWidth / frameWidth;
                points[position++] = row[2] * displayHeight / frameHeight;
            }
            for (int i = rows.size() - 1; i >= 0; i--) {
                double[] row = rows.get(i);
                points[position++] = (row[1] + 1) * displayWidth / frameWidth;
                points[position++] = (row[2] + 1) * displayHeight / frameHeight;
            }
            outlines.add(points);
        }
    }

    Polygon frame(int index) { return new Polygon(outlines.get(index)); }
}
