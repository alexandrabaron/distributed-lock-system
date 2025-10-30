import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class DistributedLockTest {
    
    public static void main(String[] args) {
        System.out.println("=== Distributed Lock System Test ===");
        
        // Test concurrent clients
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // Test 1: Multiple clients trying to acquire the same lock
        System.out.println("\nTest 1: Concurrent lock acquisition");
        executor.submit(() -> testClient("Client1", "10.0.2.3", 5000, "sharedLock"));
        executor.submit(() -> testClient("Client2", "10.0.2.4", 5000, "sharedLock"));
        executor.submit(() -> testClient("Client3", "10.0.2.5", 5000, "sharedLock"));
        
        try {
            Thread.sleep(5000); // Wait for tests to complete
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Test 2: Different locks
        System.out.println("\nTest 2: Different locks");
        executor.submit(() -> testClient("Client1", "10.0.2.3", 5000, "lock1"));
        executor.submit(() -> testClient("Client2", "10.0.2.4", 5000, "lock2"));
        executor.submit(() -> testClient("Client3", "10.0.2.5", 5000, "lock3"));
        
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        executor.shutdown();
        System.out.println("\n=== Test Complete ===");
    }
    
    private static void testClient(String clientId, String serverIp, int port, String lockName) {
        try {
            Client client = new Client(serverIp, port, clientId);
            
            // Try to acquire lock
            client.tryLock(lockName, "");
            Thread.sleep(1000);
            
            // Check ownership
            client.ownTheLock(lockName, "");
            Thread.sleep(1000);
            
            // Release lock
            client.tryUnLock(lockName, "");
            Thread.sleep(1000);
            
            // Check ownership after release
            client.ownTheLock(lockName, "");
            
        } catch (Exception e) {
            System.err.println("Error in client " + clientId + ": " + e.getMessage());
        }
    }
}
