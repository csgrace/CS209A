import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class FilterProgram {
    public static <T> List<T> filter(List<T> list, MyPredicate<T> p) {
        List<T> result = new ArrayList<>();
        for (T item : list) {
            if (p.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static boolean isEven(int number) {
        return number % 2 == 0;
    }

    public static boolean isOdd(int number) {
        return number % 2 != 0;
    }

    public static boolean isPrime(int number) {
        if (number <= 1) return false;
        for (int i = 2; i <= Math.sqrt(number); i++) {
            if (number % i == 0) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please input the function no:");
            System.out.println("1 - Get even numbers");
            System.out.println("2 - Get odd numbers");
            System.out.println("3 - Get prime numbers");
            System.out.println("0 - Quit");

            int choice = scanner.nextInt();
            if (choice == 0) {
                System.out.println("Exiting program...");
                break;
            }

            System.out.println("Input the integer list (space separated):");
            scanner.nextLine(); // Consume the leftover newline
            List<Integer> numbers = Arrays.stream(scanner.nextLine().split(" "))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            List<Integer> results = new ArrayList<>();
            switch (choice) {
                case 1:
                    results = filter(numbers, FilterProgram::isEven);
                    break;
                case 2:
                    results = filter(numbers, FilterProgram::isOdd);
                    break;
                case 3:
                    results = filter(numbers, FilterProgram::isPrime);
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
                    continue;
            }

            System.out.println("Filter results:");
            System.out.println(results);
        }
        scanner.close();
    }
}
