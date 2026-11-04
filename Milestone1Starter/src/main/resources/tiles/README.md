# Optional tile artwork

Add your own PNG images with exactly these filenames:

- forest.png
- pasture.png
- fields.png
- hills.png
- mountains.png
- desert.png

Restart the app after adding or replacing images. Maven copies this resources
folder onto the runtime classpath during `compile javafx:run`.

`game/ResourceType.java` owns these classpath paths. `game/view/HexTileView.java`
loads and clips each image to its hexagon. Missing or unreadable images fall
back to a neutral blank tile. Resource labels and number tokens stay over the
artwork so the synchronized tile data is visible even before final images exist.

Portrait images around 440 by 510 pixels match the hex bounds well. Keep the
important artwork near the center: the corners are clipped. There is no need
to edit networking or board generation when changing artwork.
