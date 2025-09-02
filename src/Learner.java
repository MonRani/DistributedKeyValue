import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Learner is responsible to maintain the state of the server and handles the commits to it
 */
public class Learner extends KeyValueStore implements Runnable {
    private static final Logger logger = Logger.getLogger(Learner.class.getName());
    
    private volatile boolean running;
    private Thread learnerThread;
    private final ConcurrentHashMap<Integer, CommitRecord> commitHistory;
    private final AtomicInteger totalCommits;
    private final AtomicInteger successfulCommits;
    private final AtomicInteger failedCommits;
    
    public Learner() {
        super();
        this.running = false;
        this.commitHistory = new ConcurrentHashMap<>();
        this.totalCommits = new AtomicInteger(0);
        this.successfulCommits = new AtomicInteger(0);
        this.failedCommits = new AtomicInteger(0);
    }

    public void start() {
        if (!running) {
            running = true;
            learnerThread = new Thread(this);
            learnerThread.start();
            logger.info("Learner thread started");
        }
    }

    public void stop() {
        running = false;
        if (learnerThread != null) {
            learnerThread.interrupt();
        }
        logger.info("Learner thread stopped");
    }

    public String commit(int key, int action) {
        totalCommits.incrementAndGet();
        String response = "";
        
        try {
            logger.info("Learner committing key=" + key + ", action=" + action);
            
            // Perform the commit operation
            switch(action) {
                case 1: 
                    response = super.getKey(key);
                    break;
                case 2: 
                    response = super.putKey(key);
                    break;
                case 3: 
                    response = super.deleteKey(key);
                    break;
                default:
                    response = "Invalid action: " + action;
                    logger.warning("Invalid action requested: " + action);
                    failedCommits.incrementAndGet();
                    return response;
            }
            
            // Record successful commit
            CommitRecord record = new CommitRecord(key, action, response, System.currentTimeMillis());
            commitHistory.put(key, record);
            successfulCommits.incrementAndGet();
            
            logger.info("Learner successfully committed: " + response);
            
        } catch (Exception e) {
            failedCommits.incrementAndGet();
            response = "Commit failed: " + e.getMessage();
            logger.log(Level.SEVERE, "Error during commit operation", e);
        }
        
        return response;
    }

    @Override
    public void run() {
        logger.info("Learner thread running");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Background maintenance tasks
                cleanupOldCommitHistory();
                logStatistics();
                Thread.sleep(5000); // Check every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in learner thread", e);
            }
        }
        logger.info("Learner thread stopped");
    }

    private void cleanupOldCommitHistory() {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - 300000; // Remove records older than 5 minutes
        
        commitHistory.entrySet().removeIf(entry -> 
            entry.getValue().timestamp < cutoffTime);
    }

    private void logStatistics() {
        int total = totalCommits.get();
        int successful = successfulCommits.get();
        int failed = failedCommits.get();
        
        if (total > 0) {
            double successRate = (double) successful / total * 100;
            logger.info(String.format("Learner Statistics - Total: %d, Successful: %d, Failed: %d, Success Rate: %.2f%%", 
                total, successful, failed, successRate));
        }
    }

    // Getter methods for monitoring
    public int getTotalCommits() {
        return totalCommits.get();
    }

    public int getSuccessfulCommits() {
        return successfulCommits.get();
    }

    public int getFailedCommits() {
        return failedCommits.get();
    }

    public double getSuccessRate() {
        int total = totalCommits.get();
        if (total == 0) return 0.0;
        return (double) successfulCommits.get() / total * 100;
    }

    public int getCommitHistorySize() {
        return commitHistory.size();
    }

    // Inner class to track commit records
    private static class CommitRecord {
        final int key;
        final int action;
        final String response;
        final long timestamp;
        
        CommitRecord(int key, int action, String response, long timestamp) {
            this.key = key;
            this.action = action;
            this.response = response;
            this.timestamp = timestamp;
        }
    }
}