package tutorial.lab7;


public class DepositThread implements Runnable {
    private final Account account;
    private final double money;

    public DepositThread(Account account, double money) {
        this.account = account;
        this.money = money;
    }

    @Override
    public void run() {
        synchronized (account) {
            account.deposit(money);
        }
    }
}