# Cup animation frames

`cup-shake.png` is an 8-column by 7-row atlas of 56 frames, each 300 by 366 pixels.
It contains the cup-covering, shaking and lifting segment of George's original
`DiceCup.mp4`, sampled at 20 frames per second beginning at 1.2 seconds. The
opening frames containing the video's fixed dice values are deliberately omitted.

`DiceRollAnimation` advances the frames on the JavaFX animation clock. During the
lift, it progressively uncovers the host-generated dice below the cup's lower
rim. Once the dice are fully uncovered, the result appears in the right-hand panel.
Shake frames play at 20 fps; the lift slows to 10 fps so the uncover is gradual.
The lower-rim positions for frames 36–51 are stored in `CUP_BOTTOM`; update those
positions if replacing the atlas with a different cup animation.
This avoids platform-specific MP4 startup/decoding delays and a separate reveal
timer. The original MP4 and RoseDice images are preserved.

`CupFrameClips` builds a vector silhouette for each cup frame. JavaFX clips away
the surrounding dark matte without removing the dark pixels inside the cup.
The revealed dice also have no background panel, so the board remains visible
around and between them. The source atlas is preserved unchanged.
