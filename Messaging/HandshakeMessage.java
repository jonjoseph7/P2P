package Messaging;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class HandshakeMessage {

    final static byte[] HEADER = "P2PFILESHARINGPROJ".getBytes(StandardCharsets.US_ASCII);

    public static byte[] encode(int peerID) {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.put(HEADER);
        b.put(new byte[10]); // 10 zeroes
        b.putInt(peerID);
        return b.array();
    }

    public static synchronized int read(ObjectInputStream in) throws IllegalHeaderException, IOException {
        ByteBuffer b = ByteBuffer.allocate(32);

        // Read bytes from the input stream
        for (int i = 0; i < 32; i++) {
            try {
                b.put(in.readByte());
            } catch (IOException e) {
                throw new RuntimeException("Error reading byte from input stream", e);
            }
        }

        b.flip();

        byte[] header = new byte[18];
        b.get(header, 0, 18);

        // Check if the header is valid
        if (!Arrays.equals(header, HEADER)) {
            throw new IllegalHeaderException("Invalid Header");
        }

        // Ensure that the position is set correctly before getting the int
        if (b.position() != 28) {
            b.position(28);
        }

        return b.getInt();
    }

    final class IllegalHeaderException extends IllegalArgumentException {
        public IllegalHeaderException(String message) {
            super(message);
        }
    }
}

