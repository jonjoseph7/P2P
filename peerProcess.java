import Peer.Peer;
import Peer.TCPClient;

class peerProcess {

    /**
     * Parses the peer ID from command line arguments.
     *
     * @param args The command line arguments.
     * @return The parsed peer ID, or -1 if it could not be parsed.
     */

    public static int parsePeerId(String[] args) {
        if (args.length == 0) {
            System.err.println("No peer ID specified.");
            return -1;
        }
        try {
            final int pid = Integer.parseInt(args[0]);
            if (pid >= 0) {
                return pid;
            } else if (pid < 0) {
                System.err.println("Invalid peer ID: " + pid);
                return -1;
            }
        } catch (Exception e) {
            System.out.println("Cannot parse pid.");
        }

        return -1;
    }

    /**
     * Starts the peer process.
     *
     * @param args The command line arguments.
     */

    public static void main(String[] args) {
        final int peerId = parsePeerId(args);

        if (peerId == -1) {
            System.out.println("Invalid peerId");
            return;
        }

        final Peer self = new Peer(peerId);
        // self.startUnchokingTimerTask();
        // self.startOptimisticUnchokingTimerTask();

        try {
            final TCPClient client = new TCPClient(self);
            client.run();
        } catch (Exception e) {
            self.logger.DebugLog(e.getStackTrace().toString());
            e.printStackTrace();
        }
    }
}