package Messaging;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import Peer.Piece;

public class Message {

	private MessageType type;
	private byte[] payload;

	public Message() {
		type = null;
		payload = new byte[0];
	}

	public Message(MessageType type) {
		this.type = type;
		this.payload = new byte[0];
	}

	public Message(MessageType type, byte[] payload) {
		this.type = type;
		this.payload = payload;
	}

	public int getPayloadLength() {
		return this.payload.length;
	}

	public int getMessageLength() {
		return this.payload.length + 5;
	}

	public void clearPayload() {
		this.payload = new byte[0];
	}

	public synchronized static Message read(ObjectInputStream in) throws IOException {
		Message msg = new Message();

		byte[] l = new byte[4];
		try {
			in.readFully(l);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		int payloadLength = ByteBuffer.wrap(l).getInt() - 5;

		byte t;
		try {
			t = in.readByte();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		msg.type = MessageType.values()[t];

		msg.payload = new byte[payloadLength];
		try {
			in.readFully(msg.payload);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return msg;
	}

	public synchronized void send(ObjectOutputStream out) {
		try {
			out.write(this.encode());
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public byte[] encode() {
		ByteBuffer b = ByteBuffer.allocate(this.getMessageLength());
		b.putInt(this.getMessageLength());
		b.put((byte) this.type.ordinal());
		b.put(this.payload);
		return b.array();
	}

	public MessageType getType() {
		return type;
	}

	public void setType(MessageType type) {
		this.type = type;
	}

	public byte[] getPayLoad() {
		return payload;
	}

	public void setPayLoad(byte[] payLoad) {
		this.payload = payLoad;
	}

	public String toString() {
		String formattedPayload = "";

		if (this.payload.length == 0)
			formattedPayload = "[NO PAYLOAD]";

		else if (type == MessageType.HAVE)
			formattedPayload = "INDEX " + ByteBuffer.wrap(this.payload).getInt();

		else if (type == MessageType.BITFIELD)
			formattedPayload = "[SOME BITFIELD]";

		else if (type == MessageType.REQUEST)
			formattedPayload = "INDEX " + ByteBuffer.wrap(this.payload).getInt();

		else if (type == MessageType.PIECE) {
			Piece p = Piece.decodePieceMessagePayload(this.payload);
			formattedPayload = "INDEX " + p.getWhichPiece() + " [SOME DATA]";
		}

		return String.format("[%s Message]: %s", type.name(), formattedPayload);
	}
}