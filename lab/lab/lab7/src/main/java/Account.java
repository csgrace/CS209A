import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private double balance;

    /**
     *
     * @param money
     */
    private final ReentrantLock lock = new ReentrantLock();

    public void deposit(double money) {
        lock.lock();
        try {
            double newBalance = balance + money;
            try {
                Thread.sleep(10);   // Simulating this service takes some processing time
            }
            catch(InterruptedException ex) {
                ex.printStackTrace();
            }
            balance = newBalance;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

    }


    public double getBalance() {
        return balance;
    }
}