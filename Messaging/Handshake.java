package Messaging;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class Handshake {

	public interface waitingFunction {
		void waitFunc();
	}

	public static synchronized int read(ObjectInputStream in) throws IllegalHeaderException, IOException {
		int id = HandshakeMessage.read(in);
		return id;
	}

	public static synchronized void send(ObjectOutputStream out, int id) throws IOException {
		out.write(HandshakeMessage.encode(id));
		out.flush();
	}

	/**
	 * This method attempts to perform a handshake between two peers in a network.
	 *
	 * @param in        The ObjectInputStream to read data from the peer.
	 * @param out       The ObjectOutputStream to write data to the peer.
	 * @param senderId  The ID of the sender.
	 * @param receiverId The ID of the receiver.
	 * @param waitFunc  A function that is called when waiting for new input.
	 * @return          True if the handshake was successful, false otherwise.
	 */

	public static synchronized boolean attemptHandshake(ObjectInputStream in, ObjectOutputStream out, int senderId,
			int receiverId,
			waitingFunction waitFunc) {
		try {
			// Send a handshake message to the peer.
			Handshake.send(out, senderId);
			System.out.println("Handshake message sent from sender with ID: " + senderId);

			// Wait for a response from the peer.
			System.out.println("Waiting for a response from the peer...");
			while (in.available() == 0) {
				waitFunc.waitFunc();
			}

			// Read the response from the peer.
			int peerId = Handshake.read(in);
			System.out.println("Received response from peer with ID: " + peerId);

			// Check if the IDs match.
			if (receiverId != peerId) {
				System.out.println("The IDs do not match. Handshake failed.");
				return false;
			}
		} catch (IllegalHeaderException e) {
			// Handle the case where an illegal header was received.
			System.out.println("Illegal Header when attempting to complete handshake");
			return false;
		} catch (IOException e) {
			// Handle IO exceptions.
			e.printStackTrace();
			return false;
		}

		// If we reached this point, it means that the handshake was successful.
		System.out.println("Handshake successful!");
		return true;
}
