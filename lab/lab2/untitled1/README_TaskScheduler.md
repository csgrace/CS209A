# Task Scheduling System

A priority-based task scheduling system implemented in Java using PriorityQueue.

## Overview

This system manages tasks with priorities and provides efficient ordering based on:
1. **Higher priority first** (descending order)
2. **Lexicographical order by description** for tasks with equal priority

## Classes

### Task Class
- **Attributes**: `description` (String) and `priority` (int)
- **Implements**: `Comparable<Task>` for natural ordering
- **Methods**: Standard getters, `compareTo()`, `toString()`, `equals()`, `hashCode()`

### TaskScheduler Class
- **Internal Storage**: `PriorityQueue<Task>`
- **Key Methods**:
  - `addTask(String description, int priority)`: Add a new task
  - `getTopKTasks(int k)`: Get top K tasks without removing them
  - `finishNextTask()`: Remove the highest priority task
  - `size()`, `isEmpty()`, `peekNextTask()`: Utility methods

## Usage

### Compilation and Running
```bash
# Compile the project
mvn compile

# Run the demonstration
java -cp target/classes TaskSchedulerTest

# Run unit tests
mvn test
```

### Example Code
```java
TaskScheduler scheduler = new TaskScheduler();

// Add tasks with different priorities
scheduler.addTask("Fix critical bug", 10);
scheduler.addTask("Write documentation", 5);
scheduler.addTask("Deploy to production", 10);

// Get top 3 tasks (doesn't modify the queue)
List<Task> topTasks = scheduler.getTopKTasks(3);

// Process highest priority task
scheduler.finishNextTask();
```

## Sample Output
```
=== Top 5 Tasks (Highest Priority) ===
1. Task{description='Deploy to production', priority=10}
2. Task{description='Fix critical bug', priority=10}
3. Task{description='Backup database', priority=9}
4. Task{description='Write unit tests', priority=8}
5. Task{description='Code review', priority=7}
```

## Key Features

✅ **Priority-based ordering**: Higher priority tasks come first  
✅ **Lexicographical ordering**: Equal priority tasks sorted alphabetically  
✅ **Non-destructive retrieval**: `getTopKTasks()` doesn't modify the queue  
✅ **Efficient operations**: O(log n) insertion and removal  
✅ **Comprehensive testing**: 7 unit tests covering all edge cases

## Testing

The implementation includes comprehensive unit tests that verify:
- Priority ordering correctness
- Lexicographical ordering for equal priorities
- Edge cases (empty queue, k=0, k>size)
- Task finishing functionality
- Non-destructive retrieval operations

Run `mvn test` to execute all unit tests.