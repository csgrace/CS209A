public class Example1 {
    // static int add (int a, int b) {
    //     return a + b;
    // }
    // static int fib(int n) {
    //     if (n <= 1)
    //         return n;
    //     //return fib(n - 1) + fib(n - 2);
    //     return add(fib(n - 1), fib(n - 2));
    // }
        static int fib(int n) {
        if (n <= 1)
            return n;
        //return fib(n - 1) + fib(n - 2);
        return fib(n - 1)+fib(n - 2);
    }

    public static void main(String args[]) {
        System.out.println(fib(10));
    }
}
