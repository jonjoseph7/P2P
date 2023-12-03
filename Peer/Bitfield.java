package Peer;

import java.util.Vector;

public class Bitfield {

	private int numPiecesDowned = 0;
	private Vector<Boolean> bitfield;

	public Bitfield() {
		int numPieces = PeerConfig.getPeerCommonProps().getNumberPieces();
		bitfield = new Vector<>(numPieces);
		for (int i = 0; i < numPieces; i++)
			bitfield.add(false);
	}

	public int getSize() {
		return bitfield.size();
	}

	public synchronized void turnOnBit(int which) {
		if (bitfield.get(which))
			return;

		bitfield.set(which, true);
		numPiecesDowned++;
	}

	public synchronized void turnOnAll() {
		for (int i = 0; i < bitfield.size(); i++) {
			bitfield.set(i, true);
		}
		numPiecesDowned = bitfield.size();
	}

	public synchronized boolean isFinished() {
		for (int i = 0; i < bitfield.size(); i++)
			if (bitfield.get(i).equals(false))
				return false;
		return true;
		// return numPiecesDowned == bitfield.size();
	}

	public synchronized byte[] encode() {
		int numBytes = (int) Math.ceil(bitfield.size() / 8.0);

		byte[] bytes = new byte[numBytes];
		for (int i = 0; i < numBytes; i++) {
			bytes[i] = (byte) 0;
		}
		for (int i = 0; i < bitfield.size(); i++) {
			int whichByte = i / 8;
			int whichBit = i % 8;
			if (bitfield.get(i)) {
				bytes[whichByte] = (byte) (bytes[whichByte] | (1 << whichBit));
			} else {
				bytes[whichByte] = (byte) (bytes[whichByte] & ~(1 << whichBit));
			}
		}

		return bytes;
	}

	public synchronized void setBitField(byte[] bytes) {
		numPiecesDowned = 0;
		for (int i = 0; i < bitfield.size(); i++) {
			int whichByte = i / 8;
			int whichBit = i % 8;
			if ((bytes[whichByte] & (1 << whichBit)) == 0) {
				bitfield.set(i, false);
			} else {
				bitfield.set(i, true);
				numPiecesDowned++;
			}
		}
	}

	public synchronized int getInterestingIndex(Bitfield b) {
		for (int i = 0; i < bitfield.size(); i++) {
			if (!bitfield.get(i) && b.getBit(i))
				return i;
		}
		return -1;
	}

	public synchronized String getText() {
		StringBuffer text = new StringBuffer();
		for (int i = 0; i < bitfield.size(); i++) {
			text.append(bitfield.get(i) ? "1" : "0");
		}
		return text.toString();
	}

	public synchronized boolean getBit(int i) {
		return bitfield.get(i);
	}

	public synchronized void clear() {
		for (int i = 0; i < bitfield.size(); i++)
			bitfield.set(i, false);
	}

	public synchronized int getNumPiecesDowned() {
		return numPiecesDowned;
	}

	public synchronized int getNumRemainingPieces() {
		return bitfield.size() - numPiecesDowned;
	}
}
