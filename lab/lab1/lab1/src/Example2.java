import java.util.ArrayList;
import java.util.List;

public class Example2 {
    public static void main(String[] args) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            int[] data = new int[1600];
            list.add(data);
        }
        System.out.println("Done");
    }
}