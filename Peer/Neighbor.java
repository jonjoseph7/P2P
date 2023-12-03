package Peer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Vector;

public class Neighbor {
    public final Integer ID;
    public final String hostname;
    public final Integer port;
    public final Bitfield bitfield = new Bitfield();
    public Boolean isChoked = true;
    public Long bytesTransferedToPeer = 0L;

    public Neighbor(Integer id, String hostname, Integer port, Boolean hasFile) {
        this.ID = id;
        this.hostname = hostname;
        this.port = port;

        if (hasFile)
            this.bitfield.turnOnAll();
    }

    public static Vector<Neighbor> LoadNeighborData(String filepath) {
        try {
            Vector<Neighbor> neighbors = new Vector<>();

            File configFile = new File(filepath);
            Scanner reader = new Scanner(configFile);
            while (reader.hasNextLine()) {
                String line = reader.nextLine().strip();
                if (line.length() == 0) {
                    continue;
                }

                String[] data = line.split(" ");
                if (data.length != 4) {
                    continue;
                }

                try {
                    Neighbor newPeer = new Neighbor(Integer.parseInt(data[0]), data[1], Integer.parseInt(data[2]),
                            data[3].equals("1"));
                    neighbors.add(newPeer);
                } catch (NumberFormatException e) {
                    System.out.println(String.format("Could not parse line \"%s\" due to NumberFormatException", line));
                    e.printStackTrace();
                }
            }
            reader.close();
            return neighbors;
        } catch (FileNotFoundException e) {
            System.out.println(String.format("Could not find file \"%s\"", filepath));
            e.printStackTrace();
            return new Vector<>();
        }
    }

    public String toString() {
        return String.format("(%d, %s, %d, %s)", ID, hostname, port, hasFile().toString());
    }

    public String getIpAddress() {
        return PeerConfig.getIpAddress(this.hostname);
    }

    public Boolean hasFile() {
        return bitfield.isFinished();
    }

    public void choke() {
        isChoked = true;
    }

    public void unchoke() {
        isChoked = false;
    }

    public boolean hasPiece(int pieceIndex) {
        return this.bitfield.getBit(pieceIndex);
    }
}
