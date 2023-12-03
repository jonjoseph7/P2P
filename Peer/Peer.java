package Peer;

import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

import Logging.PeerLogger;

public class Peer {

    int peerId;
    Bitfield bitfield;
    int peerIndex = -1; // own index in peer config

    volatile Vector<ChokingUpdate> neighborsChokingUpdates = new Vector<>();
    public volatile Vector<Boolean> requestedPieces = new Vector<>();
    public volatile Vector<Boolean> interestedNeighbors = new Vector<>();

    Vector<Integer> preferredNeighborIds;
    int optimisticallyUnchokedNeighborId = -1;

    // these work kind of unintutively: flipping the value, indicates a change in
    // the state. This way process runners can independently keep track of the last
    // value they saw to know whether they have to run choking procedures
    public boolean chokingTimeout = false;
    public boolean optimisticUnchokeTimeout = false;

    public PeerLogger logger;
    FileManager fileHandler;

    public static volatile boolean ThreadForceExit = false;

    public enum ChokingUpdate {
        NO_ACTION,
        SHOULD_BE_CHOKED,
        SHOULD_BE_UNCHOKED
    }

    public Peer(int peerId) {
        this.peerId = peerId;

        final var props = PeerConfig.getPeerCommonProps(); // loads Peer properties if not already loaded
        bitfield = new Bitfield();

        final var neighbors = PeerConfig.getNeighborhoodInfo();
        for (int i = 0; i < neighbors.size(); i++) {
            neighborsChokingUpdates.add(ChokingUpdate.NO_ACTION);
            interestedNeighbors.add(false);

            if (neighbors.get(i).ID.equals(this.peerId))
                peerIndex = i;
        }

        for (int i = 0; i < bitfield.getSize(); i++) {
            requestedPieces.add(false);
            if (neighbors.get(peerIndex).hasFile())
                bitfield.turnOnBit(i);
        }

        logger = new PeerLogger(this.peerId);
        fileHandler = new FileManager(this.peerId, neighbors.get(peerIndex).hasFile());

        preferredNeighborIds = new Vector<>(props.NumberOfPreferredNeighbors);

        this.logger.DebugLog(String.format("Created Peer with config: %s", props.toString()));
        this.logger.DebugLog(String.format("Using PeerInfo: %s", PeerConfig.neighborsToString()));

        if (neighbors.get(peerIndex).hasFile()) {
            this.logger.DebugLog("This Peer (" + peerId + ") has the file.");
        } else {
            this.logger.DebugLog("This Peer (" + peerId + ") does not have the file.");
        }
    }

    public synchronized boolean shouldForceExit() {
        return ThreadForceExit;
    }

    public synchronized boolean wantsToUnchoke(int neighborId) {
        if (neighborId == optimisticallyUnchokedNeighborId)
            return true;

        for (var id : preferredNeighborIds) {
            if (id.equals(neighborId))
                return true;
        }
        return false;
    }

    private synchronized void recomputePreferredNeighbors() {
        ArrayList<Neighbor> interestedNeigh = new ArrayList<>();
        for (int i = 0; i < this.interestedNeighbors.size(); i++)
            if (this.interestedNeighbors.get(i))
                interestedNeigh.add(PeerConfig.getNeighborhoodInfo().get(i));

        boolean peerHasFile = this.bitfield.isFinished();
        interestedNeigh.sort((var n1, var n2) -> {
            if (peerHasFile || n1.bytesTransferedToPeer == n2.bytesTransferedToPeer) {
                // choose at random
                int[] r = new int[] { -1, 1 };
                return r[new Random().nextInt(2)];
            }
            return (int) (n1.bytesTransferedToPeer - n2.bytesTransferedToPeer);
        });

        preferredNeighborIds.clear();
        int numPrefNeigh = Math.min(PeerConfig.getPeerCommonProps().NumberOfPreferredNeighbors,
                interestedNeigh.size());
        for (int i = 0; i < numPrefNeigh; i++)
            preferredNeighborIds.add(interestedNeigh.get(i).ID);

        // reset download rates of all neighbors
        for (var neighbor : PeerConfig.getNeighborhoodInfo())
            neighbor.bytesTransferedToPeer = 0L;

        // reset requested pieces to prevent deadlock
        for (int i = 0; i < requestedPieces.size(); i++)
            requestedPieces.set(i, false);
    }

    private synchronized Integer pickOptimisticallyUnchokedNeighbor() {
        // for (int i = 0; i < PeerConfig.getNeighborhoodInfo().size(); i++) {
        // var neighbor = PeerConfig.getNeighborhoodInfo().get(i);
        // this.logger.DebugLog(String.format("[IS SELF]: %b, [isInterested]: %b,
        // [isChoked]: %b, [chokingUpdate]: %s",
        // neighbor.ID == peerId, interestedNeighbors.get(i), neighbor.isChoked,
        // neighborsChokingUpdates.get(i)));
        // }

        ArrayList<Neighbor> interestedChokedNeighbors = new ArrayList<>();
        for (int i = 0; i < PeerConfig.getNeighborhoodInfo().size(); i++) {
            var neighbor = PeerConfig.getNeighborhoodInfo().get(i);
            if (neighbor.ID == peerId || !interestedNeighbors.get(i) || !neighbor.isChoked)
                // || neighborsChokingUpdates.get(i) == ChokingUpdate.SHOULD_BE_UNCHOKED)
                continue;
            interestedChokedNeighbors.add(neighbor);
        }

        // reset requested pieces to prevent deadlock
        for (int i = 0; i < requestedPieces.size(); i++)
            requestedPieces.set(i, false);

        if (interestedChokedNeighbors.size() == 0)
            return -1;

        int random_index = new Random().nextInt(interestedChokedNeighbors.size());

        return interestedChokedNeighbors.get(random_index).ID;
    }

    public synchronized void onChokingTimeout() {
        try {
            recomputePreferredNeighbors();
            logger.ChangePrefLog(preferredNeighborIds);

            for (int i = 0; i < PeerConfig.getNeighborhoodInfo().size(); i++) {
                var neighbor = PeerConfig.getNeighborhoodInfo().get(i);

                if (neighbor.ID == this.peerId) // ignore self
                    continue;

                if (neighbor.isChoked && this.wantsToUnchoke(neighbor.ID)) {
                    neighborsChokingUpdates.set(i, ChokingUpdate.SHOULD_BE_UNCHOKED);
                    neighbor.unchoke();
                } else if (!neighbor.isChoked && !this.wantsToUnchoke(neighbor.ID)) {
                    neighborsChokingUpdates.set(i, ChokingUpdate.SHOULD_BE_CHOKED);
                    neighbor.choke();
                }
            }
            chokingTimeout = !chokingTimeout; // flipping bit indicates change of state
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void onOptimisticUnchokingTimeout() {
        try {
            int newOptimisticNeighborId = pickOptimisticallyUnchokedNeighbor();
            logger.ChangeOptLog(newOptimisticNeighborId);

            for (int i = 0; i < PeerConfig.getNeighborhoodInfo().size(); i++) {
                var neighbor = PeerConfig.getNeighborhoodInfo().get(i);
                if (neighbor.ID != newOptimisticNeighborId)
                    continue;

                neighborsChokingUpdates.set(i, ChokingUpdate.SHOULD_BE_UNCHOKED);
                neighbor.unchoke();
                optimisticUnchokeTimeout = !optimisticUnchokeTimeout; // flipping bit indicates change of state
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean hasPiece(int pieceIndex) {
        return this.bitfield.getBit(pieceIndex);
    }

}
