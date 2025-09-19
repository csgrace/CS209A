import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class practice2_task1 {
        public static void main(String[] args) {
            //Testing LuckyBox with different types
            // Integer box
            LuckyBox<Integer> intBox = new LuckyBox<>();
            intBox.add(1);
            intBox.add(2);
            intBox.add(3);
            intBox.add(4);
            intBox.add(5);
            System.out.println("Lucky draw (Integer): " + intBox.draw());
            System.out.println("Contents of Integer box:");
            for (Integer i : intBox) {
                System.out.println(i);
            }
            // String box
            LuckyBox<String> strBox = new LuckyBox<>();
            strBox.add("Apple");
            strBox.add("Banana");
            strBox.add("Cherry");
            System.out.println("Contents of String box:");
            for (String s : strBox) {
                System.out.println(s);
            }
            // Draw multiple times to show randomness
            // The same item should not appear again once drawn
            System.out.println("Lucky draw (String): " + strBox.draw());
            System.out.println("Lucky draw (String): " + strBox.draw());
            System.out.println("Lucky draw (String): " + strBox.draw());
            System.out.println("Lucky draw (String): " + strBox.draw());
        }

        public static class LuckyBox<T> implements Iterable<T> {
            private List<T> items;
            private Random random;

            public LuckyBox() {
                items = new ArrayList<>();
                random = new Random();
            }

            public void add(T item) {
                items.add(item);
            }

            public T draw() {
                if (items.isEmpty()) {
                    return null; // or throw an exception
                }
                int index = random.nextInt(items.size());
                return items.remove(index);
            }

            @Override
            public Iterator<T> iterator() {
                return items.iterator();
            }
        }
}
