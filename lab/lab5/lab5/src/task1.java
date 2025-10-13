import java.io.*;
import java.nio.charset.*;

public class task1 {
    public static void main(String[] args) {
        String inputPath = "input.txt";
        String outputPath = "output.txt";
        if (args.length >= 1) inputPath = args[0];
        if (args.length >= 2) outputPath = args[1];

        final int WATER = 0x007E;    // '~'
        final int FOREST = 0x1F332;  // '🌲'
        final int ICE = 0x2744;      // '❄'
        final int BARREN = 0x2B1C;   // '⬜'

        File inFile = new File(inputPath);
        File outFile = new File(outputPath);

        try (
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(inFile), StandardCharsets.UTF_8));
                BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_16))
        ) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (!firstLine) {
                    bw.newLine();
                }
                firstLine = false;

                for (int i = 0; i < line.length(); ) {
                    int emoji = line.codePointAt(i);
                    i += Character.charCount(emoji);

                    if (emoji == WATER) {
                        bw.write(Character.toChars(ICE));
                    } else if (emoji == FOREST) {
                        bw.write(Character.toChars(BARREN));
                    } else {
                        bw.write(Character.toChars(emoji));
                    }
                }
            }
            bw.flush();
            System.out.println("Map processed -> " + outFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
