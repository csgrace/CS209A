import java.util.*;
class Task implements Comparable<Task> {
    private String description;
    private int priority;

    public Task(String description, int priority) {
        this.description = description;
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(Task other) {
        // Order by priority (higher priority first)
        if (this.priority != other.priority) {
            return Integer.compare(other.priority, this.priority);
        }
        // If priorities are the same, order lexicographically by description
        return this.description.compareTo(other.description);
    }

    @Override
    public String toString() {
        return "Task{description='" + description + "', priority=" + priority + "}";
    }
}

class TaskScheduler {
    private PriorityQueue<Task> taskQueue;

    public TaskScheduler() {
        // Initialize the priority queue with a custom comparator
        taskQueue = new PriorityQueue<>();
    }

    // Adds a new task to the task scheduler
    public void addTask(String description, int priority) {
        taskQueue.offer(new Task(description, priority));
    }

    // Retrieves the top K tasks with the highest priorities without modifying the queue
    public List<Task> getTopKTasks(int k) {
        List<Task> result = new ArrayList<>();
        Iterator<Task> iterator = taskQueue.iterator();

        // Use a temporary list to sort and fetch the top K elements
        List<Task> tempList = new ArrayList<>(taskQueue);
        Collections.sort(tempList);

        for (int i = 0; i < k && i < tempList.size(); i++) {
            result.add(tempList.get(i));
        }
        return result;
    }

    // Finishes (removes) the task with the highest priority
    public void finishNextTask() {
        if (!taskQueue.isEmpty()) {
            taskQueue.poll();
        }
    }
}

public class TaskSchedulerTest {
    public static void main(String[] args) {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.addTask("Write report", 2);
        scheduler.addTask("Respond to emails", 1);
        scheduler.addTask("Prepare presentation", 3);
        scheduler.addTask("Code review", 2);
        scheduler.addTask("Team meeting", 5);
        scheduler.addTask("Project planning", 4);
        scheduler.addTask("Client follow-up", 3);
        scheduler.addTask("Bug fixing", 2);
        scheduler.addTask("Lunch break", 1);
        scheduler.addTask("Team outing", 1);

        System.out.println("Top 5 priority tasks:");
        List<Task> top5Tasks = scheduler.getTopKTasks(5);
        top5Tasks.forEach(e -> System.out.println(e));
        System.out.println("\nFinishing the next 3 highest priority tasks\n");
        scheduler.finishNextTask();
        scheduler.finishNextTask();
        scheduler.finishNextTask();
        System.out.println("Top 6 priority tasks:");
        List<Task> top6Tasks = scheduler.getTopKTasks(6);
        top6Tasks.forEach(e -> System.out.println(e));
    }

}