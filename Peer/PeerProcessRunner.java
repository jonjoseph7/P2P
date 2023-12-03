package Peer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;

import Messaging.Handshake;
import Messaging.Message;
import Messaging.MessageType;
import Peer.Peer.ChokingUpdate;

class PeerProcessRunner implements Runnable {
	private Peer peer;

	private final int neighborIndex;

	private final Socket socket;
	private final ObjectInputStream input;
	private final ObjectOutputStream output;

	private Bitfield announcedPieces = new Bitfield(); // used to track pieces to announce HAVE

	private boolean chokingTimeout;
	private boolean optimisticUnchokeTimeout;

	private RunnerState state = RunnerState.HANDSHAKE;

	public PeerProcessRunner(Peer peer, int neighborIndex, Socket connectionSocket, ObjectInputStream input,
			ObjectOutputStream output) {
		this.peer = peer;
		this.neighborIndex = neighborIndex;
		this.socket = connectionSocket;
		this.input = input;
		this.output = output;

		// start timeouts in opposite state
		this.chokingTimeout = !this.peer.chokingTimeout;
		this.optimisticUnchokeTimeout = !this.peer.optimisticUnchokeTimeout;
	}

	// Runs checks for have, choke, unchoke related to timeout events and exit event
	private synchronized void lifetimeChecks() {
		// check for entire network having file
		peer.ThreadForceExit |= PeerConfig.allPeersHaveFile(peer);

		if (state == RunnerState.HANDSHAKE)
			return;

		// check timeout events
		boolean chokingUpdate = this.chokingTimeout == this.peer.chokingTimeout;
		boolean optimisticUnchokeUpdate = this.optimisticUnchokeTimeout == this.peer.optimisticUnchokeTimeout;

		// flip bits if there's an update to indicate that it's been resolved
		this.chokingTimeout = chokingUpdate ? !chokingTimeout : chokingTimeout;
		this.optimisticUnchokeTimeout = optimisticUnchokeUpdate ? !optimisticUnchokeTimeout : optimisticUnchokeTimeout;

		var neighbor = PeerConfig.getNeighborhoodInfo().get(neighborIndex);

		if (chokingUpdate || optimisticUnchokeUpdate) {
			if (peer.neighborsChokingUpdates.get(neighborIndex) == ChokingUpdate.SHOULD_BE_CHOKED) {
				Message chokeMsg = new Message(MessageType.CHOKE);
				chokeMsg.send(output);
				peer.logger.DebugLog("Sent CHOKE to Peer " + neighbor.ID);
			} else if (peer.neighborsChokingUpdates.get(neighborIndex) == ChokingUpdate.SHOULD_BE_UNCHOKED) {
				Message unchokeMsg = new Message(MessageType.UNCHOKE);
				unchokeMsg.send(output);
				peer.logger.DebugLog("Sent UNCHOKE to Peer " + neighbor.ID);
			}
			peer.neighborsChokingUpdates.set(neighborIndex, ChokingUpdate.NO_ACTION);
		}

		// check for new pieces
		for (int i = 0; i < peer.bitfield.getSize(); i++) {
			if (peer.bitfield.getBit(i) && !announcedPieces.getBit(i)) {
				// unannounced new piece
				peer.logger.DebugLog("Announcing 'HAVE' " + i + " to Peer " + neighbor.ID);
				byte[] index_byte_array = ByteBuffer.allocate(4).putInt(i).array();
				Message haveMsg = new Message(MessageType.HAVE, index_byte_array);
				haveMsg.send(output);
				announcedPieces.turnOnBit(i);
			}
		}
	}

	private void handleNextMessage() throws IOException {
		if (input.available() == 0)
			return;

		Message msg = Message.read(input);
		var neighbor = PeerConfig.getNeighborhoodInfo().get(neighborIndex);
		switch (msg.getType()) {
			case CHOKE:
				peer.logger.ChokingLog(neighbor.ID);
				break;
			case UNCHOKE:
				peer.logger.UnchokingLog(neighbor.ID);

				int missingPiece = getRandomMissingPieceIndex();
				if (missingPiece == -1) // no interesting piece, skip request
					break;

				Message requestMsg = new Message(MessageType.REQUEST,
						ByteBuffer.allocate(4).putInt(missingPiece).array());
				requestMsg.send(output);
				peer.requestedPieces.set(missingPiece, true);
				break;
			case INTERESTED:
				peer.logger.InterestedLog(neighbor.ID);
				peer.interestedNeighbors.set(neighborIndex, true);
				break;
			case NOTINTERESTED:
				peer.logger.NotInterestedLog(neighbor.ID);
				peer.interestedNeighbors.set(neighborIndex, false);
				break;
			case HAVE:
				int pieceIndex = ByteBuffer.wrap(msg.getPayLoad()).getInt();
				neighbor.bitfield.turnOnBit(pieceIndex);

				peer.logger.HaveMessageLog(neighbor.ID, pieceIndex);
				if (peer.bitfield.getInterestingIndex(neighbor.bitfield) != -1) {
					Message interestMsg = new Message(MessageType.INTERESTED);
					interestMsg.send(output);
				} else {
					Message noInterestMsg = new Message(MessageType.NOTINTERESTED);
					noInterestMsg.send(output);
				}
				break;
			case BITFIELD:
				neighbor.bitfield.setBitField(msg.getPayLoad());
				peer.logger.DebugLog("Peer " + neighbor.ID + " sent BITFIELD " + neighbor.bitfield.getText());

				boolean hasSomeMissingPiece = false;
				for (int i = 0; i < peer.bitfield.getSize(); i++) {
					if (!peer.hasPiece(i) && neighbor.hasPiece(i))
						hasSomeMissingPiece = true;
				}

				Message interestMsg = new Message(
						hasSomeMissingPiece ? MessageType.INTERESTED : MessageType.NOTINTERESTED);
				interestMsg.send(output);

				if (hasSomeMissingPiece)
					peer.logger.DebugLog("Sent INTERESTED to Peer " + neighbor.ID);
				else
					peer.logger.DebugLog("Sent NOT_INTERESTED to Peer " + neighbor.ID);
				break;
			case REQUEST:
				if (neighbor.isChoked) {
					peer.logger.DebugLog("Received request from " + neighbor.ID + " but it is choked");
					break;
				}

				int requestedPieceIndex = ByteBuffer.wrap(msg.getPayLoad()).getInt();
				if (!peer.hasPiece(requestedPieceIndex)) {
					peer.logger.DebugLog("Received request for a piece that I do not have from " + neighbor.ID);
					break;
				}

				Piece pieceNeeded = peer.fileHandler.readPiece(requestedPieceIndex);
				byte[] piece_needed_byte_array = ByteBuffer.allocate(4 + pieceNeeded.getPieceBytes().length)
						.putInt(pieceNeeded.getWhichPiece())
						.put(pieceNeeded.getPieceBytes())
						.array();
				Message pieceMsg = new Message(MessageType.PIECE, piece_needed_byte_array);
				pieceMsg.send(output);
				break;
			case PIECE:
				peer.logger.DebugLog(msg.toString());
				Piece pieceReceived = Piece.decodePieceMessagePayload(msg.getPayLoad());
				peer.fileHandler.writePiece(pieceReceived);

				boolean wasFinished = peer.bitfield.isFinished();
				peer.bitfield.turnOnBit(pieceReceived.getWhichPiece());
				peer.logger.DownloadLog(neighbor.ID, pieceReceived.getWhichPiece(), peer.bitfield.getNumPiecesDowned());

				if (!wasFinished && peer.bitfield.isFinished())
					peer.logger.CompleteDownloadLog();

				neighbor.bytesTransferedToPeer += pieceReceived.getPieceBytes().length;

				int missingPieceIndex = getRandomMissingPieceIndex();
				if (missingPieceIndex == -1)
					break;

				byte[] missing_piece_byte_array = ByteBuffer.allocate(4).putInt(missingPieceIndex).array();
				Message missingPieceRequest = new Message(MessageType.REQUEST, missing_piece_byte_array);
				missingPieceRequest.send(output);
				break;
		}
	}

	public synchronized int getRandomMissingPieceIndex() {
		if (this.peer.bitfield.isFinished())
			return -1;

		var neighbor = PeerConfig.getNeighborhoodInfo().get(neighborIndex);

		ArrayList<Integer> missingPieceIndices = new ArrayList<>();
		for (int i = 0; i < peer.bitfield.getSize(); i++) {
			if (!peer.hasPiece(i) && neighbor.hasPiece(i) && !peer.requestedPieces.get(i))
				missingPieceIndices.add(i);
		}

		if (missingPieceIndices.size() == 0)
			return -1;

		int randomIndex = new Random().nextInt(missingPieceIndices.size());
		return missingPieceIndices.get(randomIndex);
	}

	public void run() {
		try {
			var neighbor = PeerConfig.getNeighborhoodInfo().get(neighborIndex);
			boolean attemptResult = Handshake.attemptHandshake(input, output, peer.peerId, neighbor.ID,
					() -> lifetimeChecks());

			if (!attemptResult) {
				peer.logger.DebugLog("Failed handshake");
				return;
			}

			peer.logger.DebugLog("Handshake succeeded with Peer " + neighbor.ID + ". Started exchanging messages");

			Message bitfieldMsg = new Message(MessageType.BITFIELD, peer.bitfield.encode());
			bitfieldMsg.send(output);

			if (peer.bitfield.isFinished()) // no need to announce have's if done
				announcedPieces.turnOnAll();

			state = RunnerState.RECEIVE_MESSAGE;
			handleNextMessage(); // receive bitfield

			while (!peer.shouldForceExit()) {
				lifetimeChecks();
				handleNextMessage();
			}

			output.flush();
			lifetimeChecks();
			Thread.sleep(5000); // sleep for 5 secs in case any messages need to be retransmitted
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				input.close();
				output.close();
				socket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}