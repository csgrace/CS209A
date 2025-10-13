import java.io.*;
import java.util.*;

public class task2{
    private static final int HEADER_LEN = 4;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java FileTypeParser <file> [<file> ...]");
            return;
        }

        for (String path : args) {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) {
                System.err.println("File not found: " + path);
                continue;
            }

            byte[] header = readHeader(f, HEADER_LEN);

            System.out.println("Filename: " + f.getName());
            System.out.println("File Header(Hex): " + toHexList(header));
            System.out.println("File Type: " + detectType(header));
        }
    }

    private static byte[] readHeader(File f, int len) {
        byte[] buf = new byte[len];
        int read = 0;
        try (FileInputStream fis = new FileInputStream(f)) {
            read = fis.read(buf, 0, len);
            if (read < 0) read = 0;
        } catch (IOException e) {
            read = 0;
        }
        return Arrays.copyOf(buf, read);
    }

    private static String toHexList(byte[] h) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < h.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%02x", h[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String detectType(byte[] h) {
        if (h.length < 4) return "Unknown";
        int b0 = h[0] & 0xFF, b1 = h[1] & 0xFF, b2 = h[2] & 0xFF, b3 = h[3] & 0xFF;

        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "png";
        if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) return "zip/jar";
        if (b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE) return "class";

        return "Unknown";
    }
}