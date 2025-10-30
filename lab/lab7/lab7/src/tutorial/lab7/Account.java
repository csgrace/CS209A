package tutorial.lab7;

public class Account {
    private double balance;

    /**
     *
     * @param money
     */

    public synchronized void deposit(double money) {
        double newBalance = balance + money;
        try {
            Thread.sleep(10);   // Simulating this service takes some processing time
        }
        catch(InterruptedException ex) {
            ex.printStackTrace();
        }
        balance = newBalance;
    }
    public synchronized double getBalance() {
        return balance;
    }
}