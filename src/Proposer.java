import java.net.SocketTimeoutException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Proposer extends KeyValueStore implements Runnable {
    private static final Logger logger = Logger.getLogger(Proposer.class.getName());
    private static final AtomicInteger proposalIdGenerator = new AtomicInteger(0);
    
    private final ConcurrentHashMap<Integer, ProposalState> proposalStates;
    private final int maxRetries;
    private volatile boolean running;
    private Thread proposerThread;
    
    // Performance monitoring
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final ConcurrentHashMap<Long, Long> operationStartTimes = new ConcurrentHashMap<>();
    
    // Concurrent operation handling
    private final ExecutorService operationExecutor;
    private static final int MAX_CONCURRENT_OPERATIONS = 1000;

    public Proposer() {
        super();
        this.proposalStates = new ConcurrentHashMap<>();
        this.maxRetries = 3;
        this.running = false;
        this.operationExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_OPERATIONS);
    }

    public void start() {
        if (!running) {
            running = true;
            proposerThread = new Thread(this);
            proposerThread.start();
            logger.info("Proposer thread started with support for " + MAX_CONCURRENT_OPERATIONS + " concurrent operations");
        }
    }

    public void stop() {
        running = false;
        if (proposerThread != null) {
            proposerThread.interrupt();
        }
        if (operationExecutor != null) {
            operationExecutor.shutdown();
            try {
                if (!operationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    operationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                operationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Proposer thread stopped");
    }

    public synchronized String propose(int key, int action) {
        long startTime = System.currentTimeMillis();
        int proposalId = proposalIdGenerator.incrementAndGet();
        ProposalState state = new ProposalState(proposalId, key, action);
        proposalStates.put(proposalId, state);
        
        // Track operation start time for latency measurement
        operationStartTimes.put(Thread.currentThread().getId(), startTime);
        
        logger.fine("Starting proposal " + proposalId + " for key=" + key + ", action=" + action);
        
        try {
            // Phase 1: Prepare
            if (!preparePhase(state)) {
                recordOperationResult(false, startTime);
                return "Failed to reach consensus in prepare phase for proposal " + proposalId;
            }
            
            // Phase 2: Accept
            if (!acceptPhase(state)) {
                recordOperationResult(false, startTime);
                return "Failed to reach consensus in accept phase for proposal " + proposalId;
            }
            
            // Phase 3: Commit
            String result = commitPhase(state);
            recordOperationResult(true, startTime);
            return result;
            
        } catch (Exception e) {
            recordOperationResult(false, startTime);
            logger.log(Level.SEVERE, "Error in proposal " + proposalId, e);
            return "Error processing proposal: " + e.getMessage();
        }
    }

    // High-throughput concurrent operation method
    public CompletableFuture<String> proposeAsync(int key, int action) {
        return CompletableFuture.supplyAsync(() -> propose(key, action), operationExecutor);
    }

    // Batch operation method for high throughput
    public CompletableFuture<String[]> proposeBatch(int[] keys, int[] actions) {
        if (keys.length != actions.length) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Keys and actions arrays must have same length"));
        }
        
        CompletableFuture<String>[] futures = new CompletableFuture[keys.length];
        for (int i = 0; i < keys.length; i++) {
            futures[i] = proposeAsync(keys[i], actions[i]);
        }
        
        return CompletableFuture.allOf(futures)
            .thenApply(v -> {
                String[] results = new String[keys.length];
                for (int i = 0; i < keys.length; i++) {
                    try {
                        results[i] = futures[i].get();
                    } catch (Exception e) {
                        results[i] = "Error: " + e.getMessage();
                    }
                }
                return results;
            });
    }

    private void recordOperationResult(boolean success, long startTime) {
        long endTime = System.currentTimeMillis();
        long latency = endTime - startTime;
        
        totalOperations.incrementAndGet();
        totalLatency.addAndGet(latency);
        
        if (success) {
            successfulOperations.incrementAndGet();
        } else {
            failedOperations.incrementAndGet();
        }
        
        // Remove operation start time
        operationStartTimes.remove(Thread.currentThread().getId());
    }

    // Performance metrics getters
    public long getTotalOperations() {
        return totalOperations.get();
    }

    public long getSuccessfulOperations() {
        return successfulOperations.get();
    }

    public long getFailedOperations() {
        return failedOperations.get();
    }

    public double getSuccessRate() {
        long total = totalOperations.get();
        if (total == 0) return 0.0;
        return (double) successfulOperations.get() / total * 100;
    }

    public double getAverageLatency() {
        long total = totalOperations.get();
        if (total == 0) return 0.0;
        return (double) totalLatency.get() / total;
    }

    public long getCurrentConcurrentOperations() {
        return operationStartTimes.size();
    }

    private boolean preparePhase(ProposalState state) {
        int prepareCount = 0;
        int requiredQuorum = (Constants.NUMBER_OF_SERVERS / 2) + 1;
        
        Map<String, String> serverMap = ServerHelper.getServerMap();
        Registry registry = null;
        
        for (Map.Entry<String, String> entry : serverMap.entrySet()) {
            if (!running) break;
            
            try {
                registry = LocateRegistry.getRegistry(entry.getValue(),
                        ServerHelper.getPortNumber(entry.getKey()));
                KeyStoreInterface stub = (KeyStoreInterface) registry.lookup(entry.getKey());
                
                if (stub.prepare(state.proposalId, state.key, state.action)) {
                    prepareCount++;
                    logger.fine("Server " + entry.getKey() + " accepted prepare for proposal " + state.proposalId);
                }
            } catch (SocketTimeoutException se) {
                logger.warning("Socket timeout from server " + entry.getKey() + " during prepare");
                continue;
            } catch (RemoteException re) {
                logger.warning("Remote exception from server " + entry.getKey() + " during prepare: " + re.getMessage());
                continue;
            } catch (NotBoundException nbe) {
                logger.warning("Server " + entry.getKey() + " not bound during prepare");
                continue;
            }
        }
        
        boolean success = prepareCount >= requiredQuorum;
        if (success) {
            logger.info("Prepare phase successful: " + prepareCount + "/" + Constants.NUMBER_OF_SERVERS + " servers accepted");
        } else {
            logger.warning("Prepare phase failed: only " + prepareCount + "/" + requiredQuorum + " servers accepted");
        }
        
        return success;
    }

    private boolean acceptPhase(ProposalState state) {
        int acceptCount = 0;
        int requiredQuorum = (Constants.NUMBER_OF_SERVERS / 2) + 1;
        
        Map<String, String> serverMap = ServerHelper.getServerMap();
        Registry registry = null;
        
        for (Map.Entry<String, String> entry : serverMap.entrySet()) {
            if (!running) break;
            
            try {
                registry = LocateRegistry.getRegistry(entry.getValue(),
                        ServerHelper.getPortNumber(entry.getKey()));
                KeyStoreInterface stub = (KeyStoreInterface) registry.lookup(entry.getKey());
                
                if (stub.accept(state.proposalId, state.key, state.action)) {
                    acceptCount++;
                    logger.fine("Server " + entry.getKey() + " accepted proposal " + state.proposalId);
                }
            } catch (SocketTimeoutException se) {
                logger.warning("Socket timeout from server " + entry.getKey() + " during accept");
                continue;
            } catch (RemoteException re) {
                logger.warning("Remote exception from server " + entry.getKey() + " during accept: " + re.getMessage());
                continue;
            } catch (NotBoundException nbe) {
                logger.warning("Server " + entry.getKey() + " not bound during accept");
                continue;
            }
        }
        
        boolean success = acceptCount >= requiredQuorum;
        if (success) {
            logger.info("Accept phase successful: " + acceptCount + "/" + Constants.NUMBER_OF_SERVERS + " servers accepted");
        } else {
            logger.warning("Accept phase failed: only " + acceptCount + "/" + requiredQuorum + " servers accepted");
        }
        
        return success;
    }

    private String commitPhase(ProposalState state) {
        Map<String, String> serverMap = ServerHelper.getServerMap();
        Registry registry = null;
        String finalResponse = "";
        
        for (Map.Entry<String, String> entry : serverMap.entrySet()) {
            if (!running) break;
            
            try {
                registry = LocateRegistry.getRegistry(entry.getValue(),
                        ServerHelper.getPortNumber(entry.getKey()));
                KeyStoreInterface stub = (KeyStoreInterface) registry.lookup(entry.getKey());
                
                String response = stub.commit(state.key, state.action);
                if (finalResponse.isEmpty()) {
                    finalResponse = response;
                }
                logger.fine("Server " + entry.getKey() + " committed proposal " + state.proposalId + ": " + response);
            } catch (SocketTimeoutException se) {
                logger.warning("Socket timeout from server " + entry.getKey() + " during commit");
                continue;
            } catch (RemoteException re) {
                logger.warning("Remote exception from server " + entry.getKey() + " during commit: " + re.getMessage());
                continue;
            } catch (NotBoundException nbe) {
                logger.warning("Server " + entry.getKey() + " not bound during commit");
                continue;
            }
        }
        
        // Clean up proposal state
        proposalStates.remove(state.proposalId);
        
        if (!finalResponse.isEmpty()) {
            logger.info("Commit phase successful for proposal " + state.proposalId + ": " + finalResponse);
        } else {
            logger.warning("Commit phase failed for proposal " + state.proposalId);
        }
        
        return finalResponse;
    }

    @Override
    public void run() {
        logger.info("Proposer thread running");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Background maintenance tasks
                cleanupExpiredProposals();
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in proposer thread", e);
            }
        }
        logger.info("Proposer thread stopped");
    }

    private void cleanupExpiredProposals() {
        long currentTime = System.currentTimeMillis();
        proposalStates.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > 30000); // Remove proposals older than 30 seconds
    }

    // Getters and setters
    public int getProposalId() {
        return proposalIdGenerator.get();
    }

    public void setProposalId(int proposalId) {
        proposalIdGenerator.set(proposalId);
    }

    public int getValue() {
        return 0; // This method seems unused in the original code
    }

    public void setValue(int value) {
        // This method seems unused in the original code
    }

    // Inner class to track proposal state
    private static class ProposalState {
        final int proposalId;
        final int key;
        final int action;
        final long timestamp;
        
        ProposalState(int proposalId, int key, int action) {
            this.proposalId = proposalId;
            this.key = key;
            this.action = action;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
