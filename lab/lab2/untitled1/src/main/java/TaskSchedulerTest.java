import java.util.List;

public class TaskSchedulerTest {
    public static void main(String[] args) {
        System.out.println("=== Task Scheduling System Test ===\n");
        
        // Create a new task scheduler
        TaskScheduler scheduler = new TaskScheduler();
        
        // Add tasks with varying priorities
        System.out.println("Adding tasks to scheduler...");
        scheduler.addTask("Complete project documentation", 5);
        scheduler.addTask("Fix critical bug", 10);
        scheduler.addTask("Code review", 7);
        scheduler.addTask("Update dependencies", 3);
        scheduler.addTask("Write unit tests", 8);
        scheduler.addTask("Deploy to production", 10);
        scheduler.addTask("Update README", 3);
        scheduler.addTask("Refactor legacy code", 6);
        scheduler.addTask("Backup database", 9);
        scheduler.addTask("Team meeting", 4);
        
        System.out.println("Total tasks added: " + scheduler.size() + "\n");
        
        // Retrieve and display the top 5 tasks
        System.out.println("=== Top 5 Tasks (Highest Priority) ===");
        List<Task> topTasks = scheduler.getTopKTasks(5);
        for (int i = 0; i < topTasks.size(); i++) {
            Task task = topTasks.get(i);
            System.out.println((i + 1) + ". " + task);
        }
        System.out.println();
        
        // Finish (remove) the top 3 tasks
        System.out.println("=== Finishing Top 3 Tasks ===");
        for (int i = 0; i < 3; i++) {
            Task nextTask = scheduler.peekNextTask();
            if (nextTask != null) {
                System.out.println("Finishing: " + nextTask);
                scheduler.finishNextTask();
            }
        }
        System.out.println("Remaining tasks: " + scheduler.size() + "\n");
        
        // Display the next 6 tasks
        System.out.println("=== Next 6 Tasks (After Finishing Top 3) ===");
        List<Task> remainingTasks = scheduler.getTopKTasks(6);
        for (int i = 0; i < remainingTasks.size(); i++) {
            Task task = remainingTasks.get(i);
            System.out.println((i + 1) + ". " + task);
        }
        
        // Additional test: verify priority ordering with same priority tasks
        System.out.println("\n=== Testing Lexicographical Ordering (Same Priority) ===");
        TaskScheduler testScheduler = new TaskScheduler();
        testScheduler.addTask("Zebra task", 5);
        testScheduler.addTask("Alpha task", 5);
        testScheduler.addTask("Beta task", 5);
        
        List<Task> samePriorityTasks = testScheduler.getTopKTasks(3);
        for (int i = 0; i < samePriorityTasks.size(); i++) {
            Task task = samePriorityTasks.get(i);
            System.out.println((i + 1) + ". " + task);
        }
        
        System.out.println("\n=== Test Complete ===");
    }
}