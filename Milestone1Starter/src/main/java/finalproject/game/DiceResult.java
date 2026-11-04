package finalproject.game;

/** Host-generated dice for the current turn. Zero/zero means not rolled yet. */
public record DiceResult(int first, int second) {
    public static final DiceResult NONE = new DiceResult(0, 0);
    public DiceResult {
        if ((first != 0 || second != 0) && (first < 1 || first > 6 || second < 1 || second > 6)) {
            throw new IllegalArgumentException("Dice must both be 1–6, or both zero");
        }
    }
    public boolean rolled() { return first != 0; }
    public int total() { return first + second; }
}
