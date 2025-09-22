public class Task implements Comparable<Task> {
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
        // Higher priority first (descending order)
        int priorityComparison = Integer.compare(other.priority, this.priority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        // If priorities are equal, sort by description lexicographically (ascending order)
        return this.description.compareTo(other.description);
    }
    
    @Override
    public String toString() {
        return "Task{description='" + description + "', priority=" + priority + "}";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Task task = (Task) obj;
        return priority == task.priority && 
               description != null ? description.equals(task.description) : task.description == null;
    }
    
    @Override
    public int hashCode() {
        return (description != null ? description.hashCode() : 0) * 31 + priority;
    }
}