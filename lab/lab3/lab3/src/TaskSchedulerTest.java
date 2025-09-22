import java.util.*;
class Task implements Comparable<Task> {
    private String description;
    private int priority;
    public Task(String description, int priority) {
        this.description = description;
        this.priority = priority;
    }
    @Override
    public int compareTo(Task other) {
        if (this.priority != other.priority) {
            return Integer.compare(other.priority, this.priority);
        }
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
        taskQueue = new PriorityQueue<>();
    }
    public void addTask(String description, int priority) {
        taskQueue.offer(new Task(description, priority));
    }
    public List<Task> getTopKTasks(int k) {
        List<Task> result = new ArrayList<>();
        Iterator<Task> iterator = taskQueue.iterator();

        List<Task> tempList = new ArrayList<>(taskQueue);
        Collections.sort(tempList);

        for (int i = 0; i < k && i < tempList.size(); i++) {
            result.add(tempList.get(i));
        }
        return result;
    }
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