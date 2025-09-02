import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Server1
 */
public class ServerA extends PaxosServer {
    private static final Logger logger = Logger.getLogger(ServerA.class.getName());

    public ServerA(int serverNumber) throws RemoteException {
        super(serverNumber);
    }

    public static void main(String args[]) throws Exception {
        try {
            ServerA server = new ServerA(1);
            
            // Start the Paxos components
            server.start();
            
            KeyStoreInterface stub = (KeyStoreInterface)
                    UnicastRemoteObject.exportObject(server, 0);
            Registry registry = LocateRegistry.createRegistry(Constants.SERVER1_PORT_NO);
            registry.bind(Constants.SERVER1, stub);

            logger.info("ServerA ready and running on port " + Constants.SERVER1_PORT_NO);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down ServerA...");
                server.stop();
            }));
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ServerA exception: " + e.toString(), e);
            throw e;
        }
    }
}