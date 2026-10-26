package finalproject.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Small, length-bounded text protocol. No Java object deserialization. */
final class Wire {
    private Wire() { }

    static void send(DataOutputStream out, String... fields) throws IOException {
        String[] encoded = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            encoded[i] = Base64.getEncoder().encodeToString(fields[i].getBytes(StandardCharsets.UTF_8));
        }
        synchronized (out) {
            out.writeUTF(String.join("\t", encoded));
            out.flush();
        }
    }

    static String[] read(DataInputStream in) throws IOException {
        String[] fields = in.readUTF().split("\t", -1);
        try {
            for (int i = 0; i < fields.length; i++) {
                fields[i] = new String(Base64.getDecoder().decode(fields[i]), StandardCharsets.UTF_8);
            }
            return fields;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid message encoding", e);
        }
    }
}
