// MapProcessor.java
import java.nio.file.*;
import java.nio.charset.*;
import java.io.IOException;
import java.util.List;

public class task1 {
    // Code points used
    private static final int CP_WATER = 0x007E;       // '~'
    private static final int CP_FOREST = 0x1F332;     // '🌲'
    private static final int CP_MOUNTAIN = 0x26F0;    // '⛰'
    private static final int CP_ICE = 0x2744;         // '❄'
    private static final int CP_BARREN = 0x2B1C;      // '⬜'

    public static void main(String[] args) {
        Path input = Paths.get("input.txt");
        Path output = Paths.get("output.txt");

        try {
            List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
            StringBuilder outAll = new StringBuilder();

            for (int li = 0; li < lines.size(); li++) {
                String line = lines.get(li);
                // process by code points
                line.codePoints().forEach(cp -> {
                    if (cp == CP_WATER) {
                        outAll.appendCodePoint(CP_ICE);
                    } else if (cp == CP_FOREST) {
                        outAll.appendCodePoint(CP_BARREN);
                    } else {
                        // Mountains remain unchanged; but we still append any other character unchanged
                        outAll.appendCodePoint(cp);
                    }
                });
                // 保留原文件的换行（除最后一行也保留换行与题示例一致）
                if (li != lines.size() - 1) outAll.append(System.lineSeparator());
            }

            // 写入 UTF-16
            Files.write(output, outAll.toString().getBytes(StandardCharsets.UTF_16));
            System.out.println("Processed map written to " + output.toString() + " (UTF-16).");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
