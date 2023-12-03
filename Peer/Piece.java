package Peer;

import java.nio.ByteBuffer;

public class Piece {

	private final int whichPiece;
	private final byte[] pieceBytes;

	public Piece(int which, byte[] pieceBytes) {

		this.whichPiece = which;
		this.pieceBytes = pieceBytes;
	}

	public byte[] getPieceBytes() {
		return pieceBytes;
	}

	public int getWhichPiece() {
		return whichPiece;
	}

	public static Piece decodePieceMessagePayload(byte[] msgPayload) {
		byte[] whichPiece = new byte[4];
		for (int i = 0; i < 4; i++)
			whichPiece[i] = msgPayload[i];

		byte[] piece = new byte[msgPayload.length - 4];
		for (int i = 4; i < msgPayload.length; i++)
			piece[i - 4] = msgPayload[i];

		return new Piece(ByteBuffer.wrap(whichPiece).getInt(), piece);
	}
}
