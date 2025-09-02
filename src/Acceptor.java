import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Acceptor extends KeyValueStore implements Runnable {
    private static final Logger logger = Logger.getLogger(Acceptor.class.getName());
    private static final AtomicInteger myproposalId = new AtomicInteger(0);
    
    private volatile boolean active;
    private int serverNumber;
    private Thread acceptorThread;
    private final ConcurrentHashMap<Integer, AcceptedProposal> acceptedProposals;
    private final ConcurrentHashMap<Integer, PreparedProposal> preparedProposals;
    
    public Acceptor() {
        super();
        this.active = false;
        this.acceptedProposals = new ConcurrentHashMap<>();
        this.preparedProposals = new ConcurrentHashMap<>();
    }

    public int getMyproposalId() {
        return myproposalId.get();
    }

    public void setMyproposalId(int proposalId) {
        myproposalId.set(proposalId);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean isAlive) {
        this.active = isAlive;
    }

    public void start() {
        if (!active) {
            active = true;
            acceptorThread = new Thread(this);
            acceptorThread.start();
            logger.info("Acceptor " + serverNumber + " thread started");
        }
    }

    public void kill() {
        active = false;
        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }
        logger.info("Acceptor " + serverNumber + " thread killed");
    }

    public boolean accept(int proposalId, int key, int action) throws RemoteException, SocketTimeoutException {
        if (!active) {
            logger.warning("Acceptor " + serverNumber + " is not active, rejecting proposal " + proposalId);
            return false;
        }
        
        return check(proposalId, key, action, true);
    }

    public boolean prepare(int proposalId, int key, int action) throws RemoteException, SocketTimeoutException {
        if (!active) {
            logger.warning("Acceptor " + serverNumber + " is not active, rejecting prepare for proposal " + proposalId);
            return false;
        }
        
        return check(proposalId, key, action, false);
    }

    private boolean check(int proposalId, int key, int action, boolean isAccept) throws RemoteException, SocketTimeoutException {
        // Simulate random server failures for testing
        if (shouldSimulateFailure()) {
            logger.info("Acceptor " + serverNumber + " simulating failure for proposal " + proposalId);
            // The sleep is now handled in shouldSimulateFailure method
        }
        
        // Check if we've already accepted a higher proposal number
        if (proposalId < myproposalId.get()) {
            logger.fine("Acceptor " + serverNumber + " rejecting proposal " + proposalId + " (already accepted higher: " + myproposalId.get() + ")");
            return false;
        }
        
        // Validate the action
        if (!super.checkAction(key, action)) {
            logger.warning("Acceptor " + serverNumber + " rejecting proposal " + proposalId + " (invalid action)");
            return false;
        }
        
        if (isAccept) {
            // Accept phase
            PreparedProposal prepared = preparedProposals.get(proposalId);
            if (prepared == null) {
                logger.warning("Acceptor " + serverNumber + " rejecting accept for proposal " + proposalId + " (not prepared)");
                return false;
            }
            
            // Accept the proposal
            AcceptedProposal accepted = new AcceptedProposal(proposalId, key, action);
            acceptedProposals.put(proposalId, accepted);
            myproposalId.set(proposalId);
            
            logger.info("Acceptor " + serverNumber + " accepted proposal " + proposalId + " for key=" + key + ", action=" + action);
            return true;
        } else {
            // Prepare phase
            PreparedProposal prepared = new PreparedProposal(proposalId, key, action);
            preparedProposals.put(proposalId, prepared);
            myproposalId.set(proposalId);
            
            logger.info("Acceptor " + serverNumber + " prepared proposal " + proposalId + " for key=" + key + ", action=" + action);
            return true;
        }
    }

    private boolean shouldSimulateFailure() {
        // Simulate failures for testing purposes with 20% failure rate
        try {
            // Use a more deterministic approach to achieve 20% failure rate
            double failureThreshold = 0.20; // 20% failure rate
            double randomValue = Math.random();
            
            if (randomValue < failureThreshold) {
                logger.info("Acceptor " + serverNumber + " simulating failure for proposal");
                try {
                    // Simulate different types of failures
                    int failureType = (int)(Math.random() * 3);
                    switch (failureType) {
                        case 0: // Network delay
                            Thread.sleep(100 + (int)(Math.random() * 200)); // 100-300ms delay
                            break;
                        case 1: // Temporary unavailability
                            Thread.sleep(500 + (int)(Math.random() * 1000)); // 500-1500ms delay
                            break;
                        case 2: // Complete failure simulation
                            Thread.sleep(2000 + (int)(Math.random() * 3000)); // 2-5 second delay
                            break;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in failure simulation", e);
        }
        return false;
    }

    public int getServerNumber() {
        return serverNumber;
    }

    public void setServerNumber(int serverNumber) {
        this.serverNumber = serverNumber;
    }

    @Override
    public void run() {
        logger.info("Acceptor " + serverNumber + " thread running");
        while (active && !Thread.currentThread().isInterrupted()) {
            try {
                // Background maintenance tasks
                cleanupExpiredProposals();
                Thread.sleep(2000); // Check every 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in acceptor thread " + serverNumber, e);
            }
        }
        logger.info("Acceptor " + serverNumber + " thread stopped");
    }

    private void cleanupExpiredProposals() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up prepared proposals older than 60 seconds
        preparedProposals.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > 60000);
        
        // Clean up accepted proposals older than 120 seconds
        acceptedProposals.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > 120000);
    }

    // Inner classes to track proposal state
    private static class PreparedProposal {
        final int proposalId;
        final int key;
        final int action;
        final long timestamp;
        
        PreparedProposal(int proposalId, int key, int action) {
            this.proposalId = proposalId;
            this.key = key;
            this.action = action;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static class AcceptedProposal {
        final int proposalId;
        final int key;
        final int action;
        final long timestamp;
        
        AcceptedProposal(int proposalId, int key, int action) {
            this.proposalId = proposalId;
            this.key = key;
            this.action = action;
            this.timestamp = System.currentTimeMillis();
        }
    }
}