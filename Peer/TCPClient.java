package Peer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

public class TCPClient {

    volatile Vector<Socket> sockets = new Vector<>();
    volatile Vector<ServerSocket> serverSockets = new Vector<>();
    volatile Vector<ObjectInputStream> inputStreams = new Vector<>();
    volatile Vector<ObjectOutputStream> outputStreams = new Vector<>();

    volatile Peer peer;

    public TCPClient(Peer peer) {
        this.peer = peer;
    }

    private void close() {
        for (var s : sockets) {
            try {
                s.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (var ss : serverSockets) {
            try {
                ss.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {
        this.startUnchokingTimerTask();
        this.startOptimisticUnchokingTimerTask();

        try {
            final Vector<Thread> runners = new Vector<>();
            // initiate connection with all previous peers
            final var neighbors = PeerConfig.getNeighborhoodInfo();
            final var selfInfo = neighbors.get(peer.peerIndex);

            // Setting up connections to already running peers
            for (int i = 0; i < peer.peerIndex; i++) {
                final var neighbor = neighbors.get(i);
                boolean establishedConnection = false;
                while (!establishedConnection) {
                    try {
                        Socket onlinePeerSocket = new Socket(neighbor.getIpAddress(), selfInfo.port);
                        onlinePeerSocket.setKeepAlive(true);

                        ObjectInputStream in = new ObjectInputStream(onlinePeerSocket.getInputStream());
                        ObjectOutputStream out = new ObjectOutputStream(onlinePeerSocket.getOutputStream());
                        out.flush(); // clear any output when starting

                        sockets.add(onlinePeerSocket);

                        PeerProcessRunner runner = new PeerProcessRunner(peer, i, onlinePeerSocket, in, out);
                        Thread runner_thread = new Thread(runner);
                        runners.add(runner_thread);
                        runner_thread.start();

                        establishedConnection = true;
                        peer.logger.ConnectToLog(neighbor.ID);
                    } catch (ConnectException e) {
                        // ok
                    } catch (Exception e) {
                        System.out.println("Exception while trying to establish connection.");
                        e.printStackTrace();
                    }
                }
            }

            // Setting up connections to not yet running peers
            for (int i = peer.peerIndex + 1; i < neighbors.size(); i++) {
                try {
                    final var neighbor = neighbors.get(i);
                    
                    ServerSocket offlinePeerSocket = new ServerSocket(neighbor.port, Math.max(neighbors.size(), 100),
                            InetAddress.getByName(selfInfo.hostname));
                    Socket interceptingSocket = offlinePeerSocket.accept();
                    interceptingSocket.setKeepAlive(true);

                    // start output before input, otherwise it deadlocks
                    ObjectOutputStream out = new ObjectOutputStream(interceptingSocket.getOutputStream());
                    out.flush(); // clear any output when starting
                    ObjectInputStream in = new ObjectInputStream(interceptingSocket.getInputStream());

                    serverSockets.add(offlinePeerSocket);

                    PeerProcessRunner runner = new PeerProcessRunner(peer, i, interceptingSocket, in, out);
                    Thread runner_thread = new Thread(runner);
                    runners.add(runner_thread);
                    runner_thread.start();

                    peer.logger.ConnectFromLog(neighbor.ID);
                } catch (Exception e) {
                    System.out.println("Exception while trying to create socket to unconnected peers");
                    e.printStackTrace();
                }
            }

            for (var runner_thread : runners)
                runner_thread.join();

            peer.logger.DebugLog("All peers have file. Exiting.");
        } catch (Exception e) {
            // any unexpected failure should close existing connections
            e.printStackTrace();
            close();
        }
    }

    // NOTE: Not sure if this is where the timer functions should live but they are
    // more related to the client/runner than say the peer itself
    public void startUnchokingTimerTask() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                if (peer.shouldForceExit()) {
                    timer.cancel();
                    return;
                }
                peer.onChokingTimeout();
                discoverWhosNotDone();
            }
        }, 0, PeerConfig.getPeerCommonProps().UnchokingInterval * 1000);
    }

    public void startOptimisticUnchokingTimerTask() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                if (peer.shouldForceExit()) {
                    timer.cancel();
                    return;
                }
                peer.onOptimisticUnchokingTimeout();
            }
        }, 0, PeerConfig.getPeerCommonProps().OptimisticUnchokingInterval * 1000);
    }

    private void discoverWhosNotDone() {
        String notFinished = "[NOT FINISHED]: ";
        if (!peer.bitfield.isFinished()) {
            notFinished += peer.peerId + "(" + peer.bitfield.getNumRemainingPieces() + "), ";
            peer.logger.DebugLog("Peer " + peer.peerId + " BITFIELD: " + peer.bitfield.getText());
        }

        for (var neighbor : PeerConfig.getNeighborhoodInfo()) {
            if (neighbor.ID == peer.peerId)
                continue;

            if (!neighbor.hasFile()) {
                notFinished += neighbor.ID + "(" + neighbor.bitfield.getNumRemainingPieces() + ", "
                        + neighbor.bitfield.getNumPiecesDowned() + "), ";
                peer.logger
                        .DebugLog(peer.peerId + "| Peer " + neighbor.ID + " BITFIELD: " + neighbor.bitfield.getText());
            }
        }

        peer.logger.DebugLog(peer.peerId + "|" + notFinished);
    }
}
