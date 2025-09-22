import java.util.*;

public class TaskScheduler {
    private PriorityQueue<Task> taskQueue;
    
    public TaskScheduler() {
        this.taskQueue = new PriorityQueue<>();
    }
    
    /**
     * Adds a new task to the scheduler
     * @param description the task description
     * @param priority the task priority (higher numbers = higher priority)
     */
    public void addTask(String description, int priority) {
        taskQueue.offer(new Task(description, priority));
    }
    
    /**
     * Retrieves the top K tasks (highest priority) without modifying the queue
     * @param k the number of tasks to retrieve
     * @return a list of the top K tasks in priority order
     */
    public List<Task> getTopKTasks(int k) {
        List<Task> result = new ArrayList<>();
        if (k <= 0 || taskQueue.isEmpty()) {
            return result;
        }
        
        // Create a temporary list to extract elements without modifying the original queue
        List<Task> tempList = new ArrayList<>(taskQueue);
        // Sort the temporary list using the same comparator as PriorityQueue
        tempList.sort(null); // Uses natural ordering (Comparable)
        
        // Return the first k elements (or all if k > size)
        int elementsToReturn = Math.min(k, tempList.size());
        for (int i = 0; i < elementsToReturn; i++) {
            result.add(tempList.get(i));
        }
        
        return result;
    }
    
    /**
     * Removes the highest priority task from the scheduler
     */
    public void finishNextTask() {
        taskQueue.poll();
    }
    
    /**
     * Returns the number of tasks currently in the scheduler
     * @return the number of tasks
     */
    public int size() {
        return taskQueue.size();
    }
    
    /**
     * Checks if the scheduler is empty
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return taskQueue.isEmpty();
    }
    
    /**
     * Returns the next task without removing it
     * @return the highest priority task, or null if empty
     */
    public Task peekNextTask() {
        return taskQueue.peek();
    }
}