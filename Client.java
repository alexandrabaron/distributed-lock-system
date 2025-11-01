import java.io.*;
import java.net.*;

public class Client {
    private String serverIp;
    private int serverPort;
    private String clientId;

    public Client(String serverIp, int serverPort, String clientId) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.clientId = clientId;
    }

    private String sendMsg(String msg) {
        try (Socket socket = new Socket(serverIp, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(msg);
            String response = in.readLine();
            return response != null ? response : "ERROR";

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
            return "ERROR";
        }
    }

    private String receiveMsg() {
        // This method is implemented as part of sendMsg for simplicity
        // In a more complex system, this could handle asynchronous responses
        return "Message received";
    }

    public void tryLock(String lockName, String lockKey) {
        String response = sendMsg("LOCK," + lockName + "," + clientId);
        System.out.println("Client " + clientId + " - TryLock(" + lockName + ") Response: " + response);
    }

    public void tryUnLock(String lockName, String lockKey) {
        String response = sendMsg("UNLOCK," + lockName + "," + clientId);
        System.out.println("Client " + clientId + " - TryUnlock(" + lockName + ") Response: " + response);
    }

    public String ownTheLock(String lockName, String lockKey) {
        String response = sendMsg("OWN," + lockName + "," + clientId);
        System.out.println("Client " + clientId + " - Owner of " + lockName + ": " + response);
        return response;
    }

    // Additional utility methods for testing
    public void testLockSequence(String lockName) {
        System.out.println("\n=== Testing Lock Sequence for " + lockName + " ===");
        
        try {
            // Check initial owner
            ownTheLock(lockName, "");
            Thread.sleep(500); // Delay to allow observation
            
            // Try to acquire lock
            tryLock(lockName, "");
            Thread.sleep(500); // Delay to allow observation
            
            // Check owner after acquisition
            ownTheLock(lockName, "");
            Thread.sleep(500); // Delay to allow observation
            
            // Try to acquire same lock again (should fail)
            tryLock(lockName, "");
            Thread.sleep(500); // Delay to allow observation
            
            // Release lock
            tryUnLock(lockName, "");
            Thread.sleep(500); // Delay to allow observation
            
            // Check owner after release
            ownTheLock(lockName, "");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("=== End of Test Sequence ===\n");
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java Client <server_ip> <server_port> <client_id>");
            System.out.println("Example: java Client 127.0.0.1 5000 Client1");
            return;
        }
        
        String serverIp = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String clientId = args[2];
        
        Client client = new Client(serverIp, serverPort, clientId);
        
        // Test with multiple locks
        client.testLockSequence("lock1");
        client.testLockSequence("lock2");
        
        // Test concurrent access simulation
        System.out.println("Testing concurrent access simulation...");
        try {
            client.tryLock("sharedLock", "");
            Thread.sleep(500);
            client.ownTheLock("sharedLock", "");
            Thread.sleep(2000); // Longer delay before finishing to allow other clients to compete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}