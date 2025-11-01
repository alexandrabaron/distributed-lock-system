import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class Server {
    private int port;
    private boolean isLeader;
    private Map<String, String> lockMap = new ConcurrentHashMap<>();
    private List<String> followerServers = new ArrayList<>();
    private String serverIp;
    private ServerSocket serverSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    
    // Pending requests: key = lockName+clientId+cmd, value = PendingRequestInfo (socket and writer)
    private static class PendingRequestInfo {
        Socket socket;
        PrintWriter writer;
        
        PendingRequestInfo(Socket socket, PrintWriter writer) {
            this.socket = socket;
            this.writer = writer;
        }
    }
    private Map<String, PendingRequestInfo> pendingRequests = new ConcurrentHashMap<>();
    
    // Configuration for multiple servers (VM setup)
    private static final Map<String, Integer> SERVER_PORTS = new HashMap<>();
    static {
        SERVER_PORTS.put("10.0.2.3", 5000);   // Leader VM
        SERVER_PORTS.put("10.0.2.4", 5000);   // Follower VM 1
        SERVER_PORTS.put("10.0.2.5", 5000);   // Follower VM 2
    }

    public Server(String serverIp, int port, boolean isLeader) {
        this.serverIp = serverIp;
        this.port = port;
        this.isLeader = isLeader;
        
        // Initialize follower servers list
        if (isLeader) {
            followerServers.add("10.0.2.4:5000");
            followerServers.add("10.0.2.5:5000");
        }
    }

    public void start() throws IOException {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("==========================================");
            System.out.println("Server started successfully!");
            System.out.println("Address: " + serverIp + ":" + port);
            System.out.println("Role: " + (isLeader ? "LEADER" : "FOLLOWER"));
            System.out.println("Followers configured: " + followerServers.size());
            System.out.println("==========================================");

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from: " + clientSocket.getRemoteSocketAddress());
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                    // Continue listening for other connections
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start server on " + serverIp + ":" + port);
            System.err.println("Make sure the port is not already in use");
            throw e;
        }
    }

    // Dans Server.java

    private void handleClient(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String msg = in.readLine();
            if (msg == null) {
                socket.close();
                return;
            }

            System.out.println("[" + serverIp + "] Received message: " + msg);

            // Handle different types of messages
            if (msg.startsWith("SYNC,")) {
                // Synchronization message from leader to followers
                handleSyncMessage(msg, out);
                socket.close();
            
            } else if (msg.startsWith("REGISTER,")) {
                // Registration message from follower to leader
                handleRegistrationMessage(msg, out);
                socket.close();
                
            } else {
                // Client request message (LOCK/UNLOCK/OWN)
                boolean keepOpen = handleClientRequest(msg, out, socket);
                if (!keepOpen) {
                    socket.close();
                }
                // If keepOpen is true, socket will be closed when pending request is fulfilled
            }

        } catch (IOException e) {
            System.err.println("Error handling client connection: " + e.getMessage());
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e2) {
                // Ignore
            }
        }
    }

    private void handleSyncMessage(String msg, PrintWriter out) {
        System.out.println("[" + serverIp + "] Processing sync message: " + msg);
        processSync(msg);
        out.println("ACK");
        System.out.println("[" + serverIp + "] Sent ACK for sync message");
    }

    private void handleRegistrationMessage(String msg, PrintWriter out) {
        System.out.println("[" + serverIp + "] Received registration: " + msg);
        if (isLeader) {
            // Extract follower info and add to list
            String followerInfo = msg.substring(9); // Remove "REGISTER,"
            if (!followerServers.contains(followerInfo)) {
                followerServers.add(followerInfo);
                System.out.println("[" + serverIp + "] Added follower: " + followerInfo);
            }
            out.println("REGISTERED");
        } else {
            out.println("NOT_LEADER");
        }
    }

    private boolean handleClientRequest(String msg, PrintWriter out, Socket socket) {
        String[] parts = msg.split(",");
        if (parts.length < 3) {
            System.out.println("[" + serverIp + "] Invalid message format: " + msg);
            out.println("INVALID_FORMAT");
            return false; // Close socket
        }
        
        String cmd = parts[0];
        String lockName = parts[1];
        String clientId = parts[2];

        System.out.println("[" + serverIp + "] Processing client request: " + cmd + " for lock: " + lockName + " by client: " + clientId);
        
        // For OWN requests, respond immediately (no pending mechanism needed)
        if (cmd.equals("OWN")) {
            String response = processRequest(cmd, lockName, clientId);
            out.println(response);
            System.out.println("[" + serverIp + "] Sent response: " + response);
            return false; // Close socket
        }
        
        // For LOCK/UNLOCK on leader, respond immediately
        if (isLeader) {
            String response = processRequest(cmd, lockName, clientId);
            out.println(response);
            System.out.println("[" + serverIp + "] Sent response: " + response);
            return false; // Close socket
        }
        
        // For LOCK/UNLOCK on follower: mark as pending and forward to leader
        // The response will be sent when SYNC is received
        if (cmd.equals("LOCK") || cmd.equals("UNLOCK")) {
            String pendingKey = lockName + ":" + clientId + ":" + cmd;
            pendingRequests.put(pendingKey, new PendingRequestInfo(socket, out));
            System.out.println("[" + serverIp + "] Marked request as pending: " + pendingKey);
            
            // Forward to leader (but don't respond to client yet - will respond when SYNC arrives)
            forwardToLeaderForPending(cmd, lockName, clientId, pendingKey);
            
            // Keep connection open - will be closed when SYNC response is sent
            // Set timeout to prevent hanging connections
            try {
                socket.setSoTimeout(30000); // 30 seconds timeout for pending requests
            } catch (IOException e) {
                System.err.println("[" + serverIp + "] Error setting timeout: " + e.getMessage());
            }
            return true; // Keep socket open
        }
        
        return false; // Close socket
    }

    // AJOUTER CETTE NOUVELLE MÉTHODE DANS Server.java
    // (Elle contient la logique de votre ancienne méthode handleServerMessage)
    private synchronized void processSync(String syncMsg) {
        // syncMsg est "SYNC,CMD,lockName,clientId"
        // On enlève "SYNC,"
        String commandData = syncMsg.substring(5); 
        
        String[] parts = commandData.split(",");
        if (parts.length < 3) return; // Format invalide
        
        String cmd = parts[0];
        String lockName = parts[1];
        String clientId = parts[2];
        
        // Modifier la map locale comme demandé par le leader
        if (cmd.equals("LOCK")) {
            lockMap.put(lockName, clientId);
            System.out.println("FOLLOWER (" + serverIp + "): Synced LOCK " + lockName + " -> " + clientId);
        } else if (cmd.equals("UNLOCK")) {
            lockMap.remove(lockName);
            System.out.println("FOLLOWER (" + serverIp + "): Synced UNLOCK " + lockName);
        }
        
        // Check if this request is pending (according to subject requirements)
        String pendingKey = lockName + ":" + clientId + ":" + cmd;
        PendingRequestInfo pendingInfo = pendingRequests.remove(pendingKey);
        
        if (pendingInfo != null) {
            // Request is pending - send answer to client (as per subject)
            pendingInfo.writer.println("SUCCESS");
            System.out.println("[" + serverIp + "] Request was pending, sent SUCCESS to client for: " + pendingKey);
            // Close socket after sending response
            try {
                pendingInfo.socket.close();
            } catch (IOException e) {
                System.err.println("[" + serverIp + "] Error closing socket after pending response: " + e.getMessage());
            }
        } else {
            System.out.println("[" + serverIp + "] No pending request found for: " + pendingKey + " (may have been from leader or already processed)");
        }
    }

    private synchronized String processRequest(String cmd, String lockName, String clientId) {
        if (isLeader) {
            return handleLeaderRequest(cmd, lockName, clientId);
        } else {
            return handleFollowerRequest(cmd, lockName, clientId);
        }
    }

    private String handleLeaderRequest(String cmd, String lockName, String clientId) {
        String response = "FAIL";
        
        // First check if operation is legal (command correctness, lock existence, ownership)
        if (cmd.equals("LOCK")) {
            // Check if lock doesn't exist (preempt success condition)
            if (!lockMap.containsKey(lockName)) {
                lockMap.put(lockName, clientId);
                // Notify all followers to perform dictionary modification
                notifyFollowers("LOCK," + lockName + "," + clientId);
                response = "SUCCESS";
            }
            // Otherwise preempt fails (lock already exists)
        } else if (cmd.equals("UNLOCK")) {
            // Check if client owns the lock (release success condition)
            if (lockMap.containsKey(lockName) && lockMap.get(lockName).equals(clientId)) {
                lockMap.remove(lockName);
                // Notify all followers to perform dictionary modification
                notifyFollowers("UNLOCK," + lockName + "," + clientId);
                response = "SUCCESS";
            }
            // Otherwise release fails (client doesn't own lock)
        } else if (cmd.equals("OWN")) {
            // Any client can check the owner of a distributed lock
            response = lockMap.getOrDefault(lockName, "NONE");
        }
        
        return response;
    }

    private String handleFollowerRequest(String cmd, String lockName, String clientId) {
        System.out.println("[" + serverIp + "] Follower processing request: " + cmd + " for lock: " + lockName);
        
        // First check the validity of the operation
        if (cmd.equals("OWN")) {
            // To check the owner of a distributed lock, follower accesses its map directly
            String owner = lockMap.getOrDefault(lockName, "NONE");
            System.out.println("[" + serverIp + "] Follower returning owner: " + owner);
            return owner;
            
        } else {
            // LOCK/UNLOCK handled in handleClientRequest with pending mechanism
            System.out.println("[" + serverIp + "] Invalid command or should be handled with pending: " + cmd);
            return "INVALID_COMMAND";
        }
    }

    private void forwardToLeaderForPending(String cmd, String lockName, String clientId, String pendingKey) {
        // This method forwards to leader but doesn't respond to client
        // The response will be sent when SYNC is received
        threadPool.submit(() -> {
            try (Socket leaderSocket = new Socket("10.0.2.3", 5000);
                 PrintWriter out = new PrintWriter(leaderSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(leaderSocket.getInputStream()))) {

                // Set timeout for the socket
                leaderSocket.setSoTimeout(10000); // 10 seconds timeout

                String request = cmd + "," + lockName + "," + clientId;
                System.out.println("[" + serverIp + "] Forwarding to leader (pending): " + request);
                out.println(request);
                
                String response = in.readLine();
                System.out.println("[" + serverIp + "] Received from leader: " + response);
                
                // If leader returns FAIL, respond immediately (no need to wait for SYNC)
                if (response != null && response.equals("FAIL")) {
                    PendingRequestInfo pendingInfo = pendingRequests.remove(pendingKey);
                    if (pendingInfo != null) {
                        pendingInfo.writer.println("FAIL");
                        System.out.println("[" + serverIp + "] Sent FAIL response for pending request: " + pendingKey);
                        try {
                            pendingInfo.socket.close();
                        } catch (IOException e) {
                            System.err.println("[" + serverIp + "] Error closing socket: " + e.getMessage());
                        }
                    }
                }
                // If SUCCESS, wait for SYNC message to respond
                
            } catch (java.net.SocketTimeoutException e) {
                System.err.println("[" + serverIp + "] Timeout while forwarding to leader: " + e.getMessage());
                PendingRequestInfo pendingInfo = pendingRequests.remove(pendingKey);
                if (pendingInfo != null) {
                    pendingInfo.writer.println("TIMEOUT");
                    System.out.println("[" + serverIp + "] Sent TIMEOUT response for pending request: " + pendingKey);
                    try {
                        pendingInfo.socket.close();
                    } catch (IOException e2) {
                        System.err.println("[" + serverIp + "] Error closing socket: " + e2.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[" + serverIp + "] Error forwarding to leader: " + e.getMessage());
                PendingRequestInfo pendingInfo = pendingRequests.remove(pendingKey);
                if (pendingInfo != null) {
                    pendingInfo.writer.println("ERROR");
                    System.out.println("[" + serverIp + "] Sent ERROR response for pending request: " + pendingKey);
                    try {
                        pendingInfo.socket.close();
                    } catch (IOException e2) {
                        System.err.println("[" + serverIp + "] Error closing socket: " + e2.getMessage());
                    }
                }
            }
        });
    }

    private void notifyFollowers(String message) {
        System.out.println("[" + serverIp + "] Notifying " + followerServers.size() + " followers with message: " + message);
        
        if (followerServers.isEmpty()) {
            System.out.println("[" + serverIp + "] No followers to notify");
            return;
        }
        
        // Use CountDownLatch to wait for all ACKs (synchronous replication)
        CountDownLatch latch = new CountDownLatch(followerServers.size());
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());
        
        for (String follower : followerServers) {
            String[] parts = follower.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            
            threadPool.submit(() -> {
                try (Socket socket = new Socket(ip, port);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    
                    // Set timeout for the socket
                    socket.setSoTimeout(5000); // 5 seconds timeout
                    
                    System.out.println("[" + serverIp + "] Sending SYNC to " + follower + ": " + message);
                    out.println("SYNC," + message);
                    
                    // Wait for ACK (synchronous replication)
                    String ack = in.readLine();
                    if ("ACK".equals(ack)) {
                        System.out.println("[" + serverIp + "] Received ACK from " + follower);
                    } else {
                        System.err.println("[" + serverIp + "] Unexpected response from " + follower + ": " + ack);
                        errors.add(new IOException("Unexpected ACK: " + ack));
                    }
                    
                } catch (IOException e) {
                    System.err.println("[" + serverIp + "] Failed to notify follower " + follower + ": " + e.getMessage());
                    errors.add(e);
                } finally {
                    // Decrement latch count (even if there was an error)
                    latch.countDown();
                }
            });
        }
        
        // Wait for all followers to acknowledge (synchronous replication)
        try {
            boolean allAcked = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!allAcked) {
                System.err.println("[" + serverIp + "] Timeout waiting for ACKs from all followers");
            } else if (!errors.isEmpty()) {
                System.err.println("[" + serverIp + "] Some followers failed to acknowledge: " + errors.size() + " errors");
                // Continue anyway - replication attempted but some followers may not have received it
            } else {
                System.out.println("[" + serverIp + "] All followers acknowledged (synchronous replication complete)");
            }
        } catch (InterruptedException e) {
            System.err.println("[" + serverIp + "] Interrupted while waiting for ACKs");
            Thread.currentThread().interrupt();
        }
    }

    public void newThread(String newIp) {
        // This method handles incoming connections from other servers
        threadPool.submit(() -> {
            try {
                String[] parts = newIp.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);
                
                Socket socket = new Socket(ip, port);
                handleServerMessage(socket);
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleServerMessage(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String msg = in.readLine();
            if (msg == null) return;
            
            if (msg.startsWith("SYNC,")) {
                String syncMsg = msg.substring(5);
                String[] parts = syncMsg.split(",");
                if (parts.length >= 3) {
                    String cmd = parts[0];
                    String lockName = parts[1];
                    String clientId = parts[2];
                    
                    // Modify local map as per requirements
                    synchronized (this) {
                        if (cmd.equals("LOCK")) {
                            lockMap.put(lockName, clientId);
                            System.out.println("SYNC: Lock " + lockName + " acquired by " + clientId);
                        } else if (cmd.equals("UNLOCK")) {
                            lockMap.remove(lockName);
                            System.out.println("SYNC: Lock " + lockName + " released by " + clientId);
                        }
                    }
                }
                
                // Send acknowledgment
                out.println("ACK");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectLeader(String info) {
        // Method to notify leader about this server
        if (!isLeader) {
            System.out.println("[" + serverIp + "] Attempting to register with leader...");
            try (Socket leaderSocket = new Socket("10.0.2.3", 5000);
                 PrintWriter out = new PrintWriter(leaderSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(leaderSocket.getInputStream()))) {
                
                leaderSocket.setSoTimeout(5000); // 5 seconds timeout
                
                String registrationMsg = "REGISTER," + serverIp + ":" + port;
                System.out.println("[" + serverIp + "] Sending registration: " + registrationMsg);
                out.println(registrationMsg);
                
                String response = in.readLine();
                if ("REGISTERED".equals(response)) {
                    System.out.println("[" + serverIp + "] Successfully registered with leader");
                } else {
                    System.err.println("[" + serverIp + "] Registration failed. Response: " + response);
                }
                
            } catch (java.net.SocketTimeoutException e) {
                System.err.println("[" + serverIp + "] Timeout while registering with leader: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("[" + serverIp + "] Error registering with leader: " + e.getMessage());
                System.err.println("Make sure the leader server is running on 10.0.2.3:5000");
            }
        } else {
            System.out.println("[" + serverIp + "] This is the leader server, no registration needed");
        }
    }

    public void inform(String tmpIp, String info) {
        // Synchronized method to inform other servers
        threadPool.submit(() -> {
            try {
                String[] parts = tmpIp.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);
                
                Socket socket = new Socket(ip, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(info);
                socket.close();
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void printStatus() {
        System.out.println("\n=== SERVER STATUS ===");
        System.out.println("Server IP: " + serverIp);
        System.out.println("Port: " + port);
        System.out.println("Role: " + (isLeader ? "LEADER" : "FOLLOWER"));
        System.out.println("Active locks: " + lockMap.size());
        System.out.println("Registered followers: " + followerServers.size());
        
        if (!lockMap.isEmpty()) {
            System.out.println("Current locks:");
            lockMap.forEach((lockName, clientId) -> 
                System.out.println("   - " + lockName + " -> " + clientId));
        }
        
        if (!followerServers.isEmpty()) {
            System.out.println("Followers:");
            followerServers.forEach(follower -> 
                System.out.println("   - " + follower));
        }
        System.out.println("========================\n");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java Server <server_ip> <port> [leader]");
            System.out.println("Example: java Server 127.0.0.1 5000 leader");
            System.out.println("Example: java Server 10.0.2.3 5000 leader");
            System.out.println("Example: java Server 10.0.2.4 5000 follower");
            return;
        }
        
        String serverIp = args[0];
        int port = Integer.parseInt(args[1]);
        boolean isLeader = args.length > 2 && args[2].equals("leader");
        
        Server server = new Server(serverIp, port, isLeader);
        
        // Register with leader if this is a follower
        if (!isLeader) {
            server.connectLeader("REGISTER");
        }
        
        // Print initial status
        server.printStatus();
        
        // Add shutdown hook to print final status
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nServer shutting down...");
            server.printStatus();
        }));
        
        server.start();
    }
}