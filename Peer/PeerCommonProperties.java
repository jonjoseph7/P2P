package Peer;

public class PeerCommonProperties {
    public Integer NumberOfPreferredNeighbors;
    public Integer UnchokingInterval;
    public Integer OptimisticUnchokingInterval;
    public String FileName;
    public Integer FileSize;
    public Integer PieceSize;

    public Integer getNumberPieces() {
        return (int) Math.ceil(FileSize / (double) PieceSize);
    }

    public String toString() {
        StringBuffer s = new StringBuffer();
        s.append("NumberOfPreferredNeighbors " + NumberOfPreferredNeighbors + "; ");
        s.append("UnchokingInterval " + UnchokingInterval + "; ");
        s.append("OptimisticUnchokingInterval " + OptimisticUnchokingInterval + "; ");
        s.append("FileName " + FileName + "; ");
        s.append("FileSize " + FileSize + "; ");
        s.append("PieceSize " + PieceSize + ".");

        return s.toString();
    }
}
