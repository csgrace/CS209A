import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class task2 {
    public static <T> List<T> filter(List<T> list, MyPredicate<T> p) {
        return list.stream().filter(p::test).collect(Collectors.toList());
    }

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.println("Please input the function no:");
            System.out.println("1 - Get even numbers");
            System.out.println("2 - Get odd numbers");
            System.out.println("3 - Get prime numbers");
            System.out.println("0 - Quit");

            int choice = input.nextInt();
            if (choice == 0) {
                System.out.println("Exiting program...");
                break;
            }

            System.out.println("Input the integer list (space separated):");
            input.nextLine();
            List<Integer> numbers = Arrays.stream(input.nextLine().split(" "))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            MyPredicate<Integer> predicate;
            switch (choice) {
                case 1:
                    predicate = x -> x % 2 == 0;
                    break;
                case 2:
                    predicate = x -> x % 2 != 0;
                    break;
                case 3:
                    predicate = x -> {
                        if (x <= 1) return false;
                        for (int i = 2; i <= Math.sqrt(x); i++) {
                            if (x % i == 0) return false;
                        }
                        return true;
                    };
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
                    continue;
            }

            List<Integer> results = filter(numbers, predicate);
            System.out.println("Filter results:");
            System.out.println(results);
        }
        input.close();
    }
}
