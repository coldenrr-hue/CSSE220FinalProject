package finalproject.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.junit.jupiter.api.Test;

/** The framing every lobby message uses. Fields may contain tabs, newlines and any Unicode. */
class WireTest {

    private static String[] roundTrip(String... fields) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Wire.send(new DataOutputStream(bytes), fields);
        return Wire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void roundTripsAwkwardText() throws IOException {
        String[] fields = {"CHAT", "Host", "tab\there, newline\nthere — 🌲 ünïcödé"};
        assertArrayEquals(fields, roundTrip(fields));
    }

    @Test
    void keepsEmptyFields() throws IOException {
        assertArrayEquals(new String[]{"A", "", "", "B", ""}, roundTrip("A", "", "", "B", ""));
    }

    @Test
    void readsSeveralMessagesInOrder() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        Wire.send(out, "ONE");
        Wire.send(out, "TWO", "2");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertArrayEquals(new String[]{"ONE"}, Wire.read(in));
        assertArrayEquals(new String[]{"TWO", "2"}, Wire.read(in));
        assertThrows(EOFException.class, () -> Wire.read(in));
    }

    @Test
    void rejectsGarbage() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeUTF("not base64!!\t***");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertThrows(IOException.class, () -> Wire.read(in));
    }
}
