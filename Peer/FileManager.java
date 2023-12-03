package Peer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileManager {

	private RandomAccessFile file;

	public FileManager(int peerID, boolean hasFile) {
		String directory = "peer_" + peerID + "/";
		File dir = new File(directory);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		var props = PeerConfig.getPeerCommonProps();
		try {
			file = new RandomAccessFile(directory + props.FileName, "rw");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.out.println("RandomAccessFile constructor failed");
		}
	}

	public synchronized Piece readPiece(int which) throws IOException {
		var props = PeerConfig.getPeerCommonProps();

		int length = props.PieceSize;
		if (which == props.getNumberPieces() - 1) {
			length = props.FileSize - props.PieceSize * which;
		}

		int offSet = which * props.PieceSize;
		byte[] bytes = new byte[length];
		file.seek(offSet);

		for (int i = 0; i < length; i++) {
			bytes[i] = file.readByte();
		}

		Piece piece = new Piece(which, bytes);
		return piece;

	}

	public synchronized void writePiece(Piece piece) throws IOException {
		var props = PeerConfig.getPeerCommonProps();

		int offSet = piece.getWhichPiece() * props.PieceSize;
		int length = piece.getPieceBytes().length;
		byte[] tempByte = piece.getPieceBytes();
		file.seek(offSet);
		for (int i = 0; i < length; i++) {
			file.writeByte(tempByte[i]);
		}

	}

}
