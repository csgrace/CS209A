import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MyBlockingQueue<E> {
    private Queue<E> queue;
    private int capacity;

    private ReentrantLock lock = new ReentrantLock();
    private Condition notEmpty = lock.newCondition();
    private Condition notFull = lock.newCondition();

    public MyBlockingQueue(int capacity){
        //TODO: Constructor with input capacity
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.queue = new LinkedList<>();
    }

    public void put(E e){
        //TODO: When the queue is full,  wait until the consumer takes data and the queue has some empty buffer
        lock.lock();
        try {
            while (queue.size() == capacity) {
                try {
                    notFull.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            queue.offer(e);
            System.out.println("Thread:" + Thread.currentThread().getName() +
                    ", Produced:" + e + ", Queue:" + queue);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public E take() {
        //TODO: When queue empty,   wait until the producer puts new data into the queue
        lock.lock();
        try {
            while (queue.isEmpty()) {
                try {
                    notEmpty.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            E e = queue.poll();
            System.out.println("Thread:" + Thread.currentThread().getName() +
                    ", Consumed:" + e + ", Queue:" + queue);
            notFull.signal();
            return e;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {

        int CAPACITY = 200;
        int PRODUCER_WORK = 20;
        int PRODUCER_CNT = 100; // 10
        int PRODUCER_OFF = 1000; // 10
        int CONSUMER_WORK = 21; //20
        int CONSUMER_CNT = 100; // 10
        int CONSUMER_OFF = 10;

        MyBlockingQueue<Integer> queue = new MyBlockingQueue<>(CAPACITY);

        Runnable producer = () -> {
            for(int i=0; i<PRODUCER_WORK; i++){
                queue.put(i);
                try {
                    Thread.sleep(PRODUCER_OFF);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        Runnable consumer = () -> {
            for(int i=0; i<CONSUMER_WORK; i++){
                queue.take();
                try {
                    Thread.sleep(CONSUMER_OFF);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        for (int i = 0; i < PRODUCER_CNT; i++) {
            new Thread(producer).start();
        }
        for (int i = 0; i < CONSUMER_CNT; i++) {
            new Thread(consumer).start();
        }

    }

}
