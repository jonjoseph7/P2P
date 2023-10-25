package Logging;

import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeerLogger {

	private FileHandler logFileHandler;
	private LogFormatter formatter;
	private Logger logger;
	private int id;

	public PeerLogger(int peer_id) {
		// Declare instance variables
		FileHandler logFileHandler;
		LogFormatter formatter;
		Logger logger;
		//int id;

    
		this.id = peer_id; // Assign the peer_id to the instance variable id

		// Create a logger with the name "Peer" + id
		this.logger = Logger.getLogger("Peer" + id);

		try {
			// Set the logger level to INFO
			this.logger.setLevel(Level.INFO);

			// Disable the use of parent handlers
			this.logger.setUseParentHandlers(false);

			// Create a new FileHandler for the log file
			this.logFileHandler = new FileHandler("log_peer_" + peer_id + ".log");

			// Create a new SimpleFormatter
			this.formatter = new LogFormatter();

			// Set the formatter for the FileHandler
			//this.logFileHandler.setFormatter(formatter);

			// Add the FileHandler to the logger
			//this.logger.addHandler(logFileHandler);
		} catch (Exception e) {
			e.printStackTrace(); // Print the stack trace for debugging purposes

			// Log an error message indicating that the logger creation failed
			System.err.println("Failed to create logger for peer " + id);
		}
		
	}

	public synchronized void ConnectToLog(int peer_id) {
		logger.info(String.format("Peer %d makes a connection to Peer %d.", id, peer_id));
	}

	public synchronized void ConnectFromLog(int peer_id) {
		logger.info(String.format("Peer %d is connected from Peer %d.", id, peer_id));
	}

	public synchronized void ChangePrefLog(Vector<Integer> preferedNeighbors) {
		// Use StringBuilder for better performance
		StringBuilder s = new StringBuilder();
		s.append("Peer ").append(id).append(" has the preferred neighbors ");

		// String.join for cleaner code
		//s.append(String.join(", ", preferredNeighbors.stream().map(String::valueOf).collect(Collectors.toList())));

		// Add period at the end
		s.append(".");

		logger.info(s.toString());
	}

	public synchronized void ChangeOptLog(int peer_id) {
		logger.info(String.format("Peer %d has the optimistically unchoked neighbor %d.", id, peer_id));
	}

	public synchronized void UnchokingLog(int peer_id) {
		logger.info(String.format("Peer %d is unchoked by %d.", id, peer_id));
	}

	public synchronized void ChokingLog(int peer_id) {
		logger.info(String.format("Peer %d is choked by %d.", id, peer_id));
	}

	public synchronized void HaveMessageLog(int peer_id, int pieceIndex) {
		logger.info(String.format(
				"Peer %d received a 'have' message from %d for the piece %d.", id, peer_id, pieceIndex));
	}

	public synchronized void InterestedLog(int peer_id) {
		logger.info(String.format("Peer %d received the 'interested' message from %d.", id, peer_id));
	}

	public synchronized void NotInterestedLog(int peer_id) {
		logger.info(String.format("Peer %d received the 'not interested' message from %d.", id, peer_id));
	}

	public synchronized void DownloadLog(int peer_id, int pieceIndex, int numTotalPieces) {
		logger.info(
				String.format("Peer %d has downloaded the piece %d from %d. Now the number of pieces it has is %d.",
						id, pieceIndex, peer_id, numTotalPieces));
	}

	public synchronized void CompleteDownloadLog() {
		logger.info(String.format("Peer %d has downloaded the complete file.", id));
	}

	public synchronized void DebugLog(String log) {
		logger.info(String.format("DEBUG: %s", log));
	}
}
