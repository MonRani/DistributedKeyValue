

import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.util.logging.Logger;
import java.util.logging.Level;

public class PaxosServer implements KeyStoreInterface {
    private static final Logger logger = Logger.getLogger(PaxosServer.class.getName());
    
    private final Proposer proposer;
    private final Learner learner;
    private final Acceptor acceptor;
    private final int serverNumber;
    private volatile boolean isRunning;

    public PaxosServer(int serverNumber) throws RemoteException {
        this.serverNumber = serverNumber;
        this.proposer = new Proposer();
        this.learner = new Learner();
        this.acceptor = new Acceptor();
        this.isRunning = false;
        
        logger.info("PaxosServer " + serverNumber + " initialized");
    }

    public void start() {
        if (!isRunning) {
            try {
                // Start all components
                proposer.start();
                learner.start();
                acceptor.start();
                
                // Set server number for acceptor
                acceptor.setServerNumber(serverNumber);
                
                isRunning = true;
                logger.info("PaxosServer " + serverNumber + " started successfully");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to start PaxosServer " + serverNumber, e);
                throw new RuntimeException("Failed to start server", e);
            }
        }
    }

    public void stop() {
        if (isRunning) {
            try {
                // Stop all components
                proposer.stop();
                learner.stop();
                acceptor.kill();
                
                isRunning = false;
                logger.info("PaxosServer " + serverNumber + " stopped successfully");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error stopping PaxosServer " + serverNumber, e);
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public String get(int key) {
        if (!isRunning) {
            logger.warning("PaxosServer " + serverNumber + " is not running");
            return "Server not running";
        }
        
        try {
            logger.info("PaxosServer " + serverNumber + " processing GET request for key: " + key);
            String result = proposer.propose(key, 1);
            logger.info("PaxosServer " + serverNumber + " GET result: " + result);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing GET request for key: " + key, e);
            return "Error processing GET request: " + e.getMessage();
        }
    }

    public String put(int key) {
        if (!isRunning) {
            logger.warning("PaxosServer " + serverNumber + " is not running");
            return "Server not running";
        }
        
        try {
            logger.info("PaxosServer " + serverNumber + " processing PUT request for key: " + key);
            String result = proposer.propose(key, 2);
            logger.info("PaxosServer " + serverNumber + " PUT result: " + result);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing PUT request for key: " + key, e);
            return "Error processing PUT request: " + e.getMessage();
        }
    }

    public String delete(int key) {
        if (!isRunning) {
            logger.warning("PaxosServer " + serverNumber + " is not running");
            return "Server not running";
        }
        
        try {
            logger.info("PaxosServer " + serverNumber + " processing DELETE request for key: " + key);
            String result = proposer.propose(key, 3);
            logger.info("PaxosServer " + serverNumber + " DELETE result: " + result);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing DELETE request for key: " + key, e);
            return "Error processing DELETE request: " + e.getMessage();
        }
    }

    @Override
    public boolean prepare(int proposalId, int key, int action)
            throws RemoteException, SocketTimeoutException {
        if (!isRunning) {
            logger.warning("PaxosServer " + serverNumber + " received prepare request while not running");
            return false;
        }
        
        try {
            logger.fine("PaxosServer " + serverNumber + " processing prepare request: proposalId=" + proposalId + ", key=" + key + ", action=" + action);
            boolean result = acceptor.prepare(proposalId, key, action);
            logger.fine("PaxosServer " + serverNumber + " prepare result: " + result);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing prepare request", e);
            throw e;
        }
    }

    @Override
    public boolean accept(int proposalId, int key, int action)
            throws RemoteException, SocketTimeoutException {
        if (!isRunning) {
            logger.warning("PaxosServer " + serverNumber + " received accept request while not running");
            return false;
        }
        
        try {
            logger.fine("PaxosServer " + serverNumber + " processing accept request: proposalId=" + proposalId + ", key=" + key + ", action=" + action);
            boolean result = acceptor.accept(proposalId, key, action);
            logger.fine("PaxosServer " + serverNumber + " accept result: " + result);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing accept request", e);
            throw e;
        }
    }

    @Override
    public String commit(int key, int action)
            throws RemoteException, SocketTimeoutException {
        if (!isRunning) {
            logger.warning("PaxosServer " + serverNumber + " received commit request while not running");
            return "Server not running";
        }
        
        try {
            logger.fine("PaxosServer " + serverNumber + " processing commit request: key=" + key + ", action=" + action);
            String result = learner.commit(key, action);
            logger.fine("PaxosServer " + serverNumber + " commit result: " + result);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing commit request", e);
            throw e;
        }
    }

    // Getter methods for monitoring
    public Proposer getProposer() {
        return proposer;
    }

    public Learner getLearner() {
        return learner;
    }

    public Acceptor getAcceptor() {
        return acceptor;
    }

    public int getServerNumber() {
        return serverNumber;
    }
}