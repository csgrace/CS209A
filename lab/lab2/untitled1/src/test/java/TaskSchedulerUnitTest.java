import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class TaskSchedulerUnitTest {
    private TaskScheduler scheduler;
    
    @BeforeEach
    void setUp() {
        scheduler = new TaskScheduler();
    }
    
    @Test
    void testTaskComparison() {
        Task task1 = new Task("High priority task", 10);
        Task task2 = new Task("Low priority task", 5);
        Task task3 = new Task("Alpha task", 5);
        Task task4 = new Task("Beta task", 5);
        
        // Higher priority should come first (negative result)
        assertTrue(task1.compareTo(task2) < 0);
        
        // Same priority, lexicographical order
        assertTrue(task3.compareTo(task4) < 0);
        assertTrue(task4.compareTo(task3) > 0);
    }
    
    @Test
    void testAddTaskAndSize() {
        assertEquals(0, scheduler.size());
        assertTrue(scheduler.isEmpty());
        
        scheduler.addTask("Test task", 5);
        assertEquals(1, scheduler.size());
        assertFalse(scheduler.isEmpty());
        
        scheduler.addTask("Another task", 3);
        assertEquals(2, scheduler.size());
    }
    
    @Test
    void testGetTopKTasks() {
        scheduler.addTask("Low priority", 1);
        scheduler.addTask("High priority", 10);
        scheduler.addTask("Medium priority", 5);
        
        List<Task> top2 = scheduler.getTopKTasks(2);
        assertEquals(2, top2.size());
        assertEquals("High priority", top2.get(0).getDescription());
        assertEquals(10, top2.get(0).getPriority());
        assertEquals("Medium priority", top2.get(1).getDescription());
        assertEquals(5, top2.get(1).getPriority());
        
        // Should not modify the original queue
        assertEquals(3, scheduler.size());
    }
    
    @Test
    void testGetTopKTasksWithEqualPriorities() {
        scheduler.addTask("Zebra", 5);
        scheduler.addTask("Alpha", 5);
        scheduler.addTask("Beta", 5);
        
        List<Task> allTasks = scheduler.getTopKTasks(3);
        assertEquals(3, allTasks.size());
        assertEquals("Alpha", allTasks.get(0).getDescription());
        assertEquals("Beta", allTasks.get(1).getDescription());
        assertEquals("Zebra", allTasks.get(2).getDescription());
    }
    
    @Test
    void testFinishNextTask() {
        scheduler.addTask("Low priority", 1);
        scheduler.addTask("High priority", 10);
        scheduler.addTask("Medium priority", 5);
        
        assertEquals(3, scheduler.size());
        Task nextTask = scheduler.peekNextTask();
        assertEquals("High priority", nextTask.getDescription());
        
        scheduler.finishNextTask();
        assertEquals(2, scheduler.size());
        
        Task newNextTask = scheduler.peekNextTask();
        assertEquals("Medium priority", newNextTask.getDescription());
    }
    
    @Test
    void testGetTopKTasksEdgeCases() {
        // Empty scheduler
        List<Task> emptyResult = scheduler.getTopKTasks(5);
        assertTrue(emptyResult.isEmpty());
        
        // k = 0
        scheduler.addTask("Test", 5);
        List<Task> zeroResult = scheduler.getTopKTasks(0);
        assertTrue(zeroResult.isEmpty());
        
        // k > size
        List<Task> moreResult = scheduler.getTopKTasks(10);
        assertEquals(1, moreResult.size());
    }
    
    @Test
    void testPeekNextTaskOnEmptyScheduler() {
        assertNull(scheduler.peekNextTask());
    }
}