package Peer;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PeerConfig {

    public static final String PeerPropertiesFile = "Common.small.cfg";
    public static final String PeerInfoFile = "PeerInfo.small.cfg";

    private static PeerCommonProperties props = null;
    private static Vector<Neighbor> neighbors = null;

    private volatile static HashMap<String, String> dnsShortcut = new HashMap<>() {
        {
            put("lin114-00.cise.ufl.edu", "10.242.94.34");
            put("lin114-01.cise.ufl.edu", "10.242.94.35");
            put("lin114-02.cise.ufl.edu", "10.242.94.36");
            put("lin114-03.cise.ufl.edu", "10.242.94.37");
            put("lin114-04.cise.ufl.edu", "10.242.94.38");
            put("lin114-05.cise.ufl.edu", "10.242.94.39");
            put("lin114-06.cise.ufl.edu", "10.242.94.40");
            put("lin114-07.cise.ufl.edu", "10.242.94.41");
            put("lin114-08.cise.ufl.edu", "10.242.94.42");
            put("lin114-09.cise.ufl.edu", "10.242.94.43");
            put("lin114-10.cise.ufl.edu", "10.242.94.44");
            put("lin114-11.cise.ufl.edu", "10.242.94.45");
            put("localhost", "localhost");
            put("ubuntu", "localhost");
        }
    };

    public static void LoadPeerProperties(String filepath) {
        Map<String, Field> properties = Stream.of(PeerCommonProperties.class.getDeclaredFields())
                .collect(Collectors.toMap(f -> f.getName(), f -> f));

        PeerConfig.props = new PeerCommonProperties();

        try {
            File configFile = new File(filepath);
            Scanner reader = new Scanner(configFile);
            while (reader.hasNextLine()) {
                String line = reader.nextLine().strip();
                if (line.length() == 0) {
                    continue;
                }

                String[] data = line.split(" ");
                if (data.length != 2 || !properties.containsKey(data[0])) {
                    continue;
                }

                Field f = properties.get(data[0]);

                Object newData = f.getType().isAssignableFrom(Integer.class) ? Integer.parseInt(data[1]) : data[1];
                try {
                    // System.out.println(String.format("Setting %s to %s", f.getName(), data[1]));
                    f.set(PeerConfig.props, newData);
                } catch (IllegalAccessException e) {
                    System.out.println(String.format(
                            "Failed to parse data for \"%s\" due to IllegalAccessException", data[0]));
                }
            }
            reader.close();
        } catch (FileNotFoundException e) {
            System.out.println(String.format("Could not find file \"%s\"", filepath));
            e.printStackTrace();
        }
    }

    public static void LoadNeighborsFromFile(String filename) {
        PeerConfig.neighbors = Neighbor.LoadNeighborData(filename);
    }

    public static PeerCommonProperties getPeerCommonProps() {
        if (PeerConfig.props == null) {
            PeerConfig.LoadPeerProperties(PeerPropertiesFile);
        }

        return PeerConfig.props;
    }

    public static Vector<Neighbor> getNeighborhoodInfo() {
        if (PeerConfig.neighbors == null) {
            PeerConfig.LoadNeighborsFromFile(PeerInfoFile);
        }

        return PeerConfig.neighbors;
    }

    public static String getIpAddress(String hostname) {
        if (dnsShortcut.containsKey(hostname)) {
            return dnsShortcut.get(hostname);
        }

        try {
            return InetAddress.getByName(hostname).getHostAddress();
        } catch (UnknownHostException e) {
            System.out.println(String.format("Could not find host address for %s. Returning localhost.", hostname));
        }

        return dnsShortcut.get("localhost");
    }

    public static synchronized boolean allPeersHaveFile(Peer peer) {
        if (!peer.bitfield.isFinished())
            return false;

        for (var neighbor : getNeighborhoodInfo()) {
            if (neighbor.ID == peer.peerId)
                continue;
            if (!neighbor.hasFile())
                return false;
        }
        return true;
    }

    public static String neighborsToString() {
        var neighbors = getNeighborhoodInfo();

        StringBuffer sbuf = new StringBuffer();
        for (var nei : neighbors) {
            sbuf.append(String.format("Peer %d at %s : %d; ", nei.ID, nei.hostname, nei.port));
        }

        return sbuf.toString();
    }
}