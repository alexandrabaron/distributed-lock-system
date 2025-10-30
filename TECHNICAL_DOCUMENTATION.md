# 📚 Technical Documentation - Distributed Lock System

This document provides a comprehensive technical analysis of the distributed lock system codebase, explaining every class, method, and concept in detail.

## 📋 Table of Contents

- [Project Structure](#project-structure)
- [Server.java - Complete Analysis](#serverjava---complete-analysis)
- [Client.java - Complete Analysis](#clientjava---complete-analysis)
- [DistributedLockTest.java - Complete Analysis](#distributedlocktestjava---complete-analysis)
- [Communication Flow](#communication-flow)
- [Data Structures](#data-structures)
- [Threading Model](#threading-model)
- [Error Handling](#error-handling)
- [Protocol Details](#protocol-details)

## 🏗️ Project Structure

```
distributed-lock-project/
├── Server.java              # Main server implementation
├── Client.java              # Client implementation
├── DistributedLockTest.java # Automated testing
└── README.md                # Project documentation
```

## 🖥️ Server.java - Complete Analysis

### Class Overview

The `Server` class implements the core distributed lock system with leader-follower architecture.

```java
public class Server {
    // Core server properties
    private int port;                    // Server listening port
    private boolean isLeader;            // Leader or follower role
    private Map<String, String> lockMap; // Distributed lock storage
    private List<String> followerServers; // List of follower servers
    private String serverIp;             // Server IP address
    private ServerSocket serverSocket;   // Main server socket
    private ExecutorService threadPool;  // Thread pool for connections
}
```

### Static Configuration

```java
private static final Map<String, Integer> SERVER_PORTS = new HashMap<>();
static {
    SERVER_PORTS.put("10.0.2.3", 5000);   // Leader VM
    SERVER_PORTS.put("10.0.2.4", 5000);   // Follower VM 1
    SERVER_PORTS.put("10.0.2.5", 5000);   // Follower VM 2
}
```

**Purpose**: Defines the network topology of the distributed system.

### Constructor Analysis

```java
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
```

**Parameters**:
- `serverIp`: IP address of this server instance
- `port`: Port number for listening
- `isLeader`: Boolean flag determining server role

**Initialization Logic**:
- Sets up server identity
- If leader, pre-configures follower list
- Followers start with empty follower list

### Main Server Loop

```java
public void start() throws IOException {
    try {
        serverSocket = new ServerSocket(port);
        System.out.println("==========================================");
        System.out.println("🚀 Server started successfully!");
        System.out.println("📍 Address: " + serverIp + ":" + port);
        System.out.println("👑 Role: " + (isLeader ? "LEADER" : "FOLLOWER"));
        System.out.println("📊 Followers configured: " + followerServers.size());
        System.out.println("==========================================");

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔗 New connection from: " + clientSocket.getRemoteSocketAddress());
                threadPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                System.err.println("❌ Error accepting connection: " + e.getMessage());
                // Continue listening for other connections
            }
        }
    } catch (IOException e) {
        System.err.println("❌ Failed to start server on " + serverIp + ":" + port);
        System.err.println("💡 Make sure the port is not already in use");
        throw e;
    }
}
```

**Functionality**:
1. **Socket Creation**: Creates server socket on specified port
2. **Status Display**: Shows server startup information
3. **Accept Loop**: Continuously accepts incoming connections
4. **Thread Delegation**: Each connection handled in separate thread
5. **Error Recovery**: Continues operation despite individual connection errors

### Client Connection Handler

```java
private void handleClient(Socket socket) {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

        String msg = in.readLine();
        if (msg == null) return;

        System.out.println("[" + serverIp + "] Received message: " + msg);

        // Handle different types of messages
        if (msg.startsWith("SYNC,")) {
            // Synchronization message from leader to followers
            handleSyncMessage(msg, out);
        
        } else if (msg.startsWith("REGISTER,")) {
            // Registration message from follower to leader
            handleRegistrationMessage(msg, out);
            
        } else {
            // Client request message (LOCK/UNLOCK/OWN)
            handleClientRequest(msg, out);
        }

    } catch (IOException e) {
        System.err.println("Error handling client connection: " + e.getMessage());
        e.printStackTrace();
    }
}
```

**Message Routing Logic**:
- **SYNC messages**: Inter-server synchronization
- **REGISTER messages**: Follower registration with leader
- **Client messages**: LOCK/UNLOCK/OWN operations

### Synchronization Message Handler

```java
private void handleSyncMessage(String msg, PrintWriter out) {
    System.out.println("[" + serverIp + "] Processing sync message: " + msg);
    processSync(msg);
    out.println("ACK");
    System.out.println("[" + serverIp + "] Sent ACK for sync message");
}
```

**Purpose**: Handles synchronization messages from leader to followers.

**Process**:
1. Logs the sync message
2. Processes the synchronization
3. Sends ACK confirmation to leader

### Registration Message Handler

```java
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
```

**Functionality**:
- **Leader**: Adds follower to internal list, confirms registration
- **Follower**: Rejects registration attempts

### Client Request Handler

```java
private void handleClientRequest(String msg, PrintWriter out) {
    String[] parts = msg.split(",");
    if (parts.length < 3) {
        System.out.println("[" + serverIp + "] Invalid message format: " + msg);
        out.println("INVALID_FORMAT");
        return;
    }
    
    String cmd = parts[0];
    String lockName = parts[1];
    String clientId = parts[2];

    System.out.println("[" + serverIp + "] Processing client request: " + cmd + " for lock: " + lockName + " by client: " + clientId);
    
    String response = processRequest(cmd, lockName, clientId);
    out.println(response);
    
    System.out.println("[" + serverIp + "] Sent response: " + response);
}
```

**Message Format**: `COMMAND,LOCK_NAME,CLIENT_ID`

**Validation**: Ensures message has at least 3 parts

### Core Request Processing

```java
private synchronized String processRequest(String cmd, String lockName, String clientId) {
    if (isLeader) {
        return handleLeaderRequest(cmd, lockName, clientId);
    } else {
        return handleFollowerRequest(cmd, lockName, clientId);
    }
}
```

**Synchronization**: Uses `synchronized` to ensure thread-safe access to lock map.

**Routing**: Delegates to appropriate handler based on server role.

### Leader Request Handler

```java
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
```

**LOCK Logic**:
- **Success**: Lock doesn't exist → Add to map → Notify followers
- **Failure**: Lock already exists

**UNLOCK Logic**:
- **Success**: Client owns lock → Remove from map → Notify followers
- **Failure**: Client doesn't own lock

**OWN Logic**:
- **Always succeeds**: Returns owner or "NONE"

### Follower Request Handler

```java
private String handleFollowerRequest(String cmd, String lockName, String clientId) {
    System.out.println("[" + serverIp + "] Follower processing request: " + cmd + " for lock: " + lockName);
    
    // First check the validity of the operation
    if (cmd.equals("OWN")) {
        // To check the owner of a distributed lock, follower accesses its map directly
        String owner = lockMap.getOrDefault(lockName, "NONE");
        System.out.println("[" + serverIp + "] Follower returning owner: " + owner);
        return owner;
        
    } else if (cmd.equals("LOCK") || cmd.equals("UNLOCK")) {
        // For legitimate operations, slave server forwards the request to primary server
        System.out.println("[" + serverIp + "] Forwarding " + cmd + " request to leader");
        String leaderResponse = forwardToLeader(cmd, lockName, clientId);
        
        // If the leader approved the operation, update local map
        if ("SUCCESS".equals(leaderResponse)) {
            synchronized (this) {
                if (cmd.equals("LOCK")) {
                    lockMap.put(lockName, clientId);
                    System.out.println("[" + serverIp + "] Follower updated local map: LOCK " + lockName + " -> " + clientId);
                } else if (cmd.equals("UNLOCK")) {
                    lockMap.remove(lockName);
                    System.out.println("[" + serverIp + "] Follower updated local map: UNLOCK " + lockName);
                }
            }
        }
        
        System.out.println("[" + serverIp + "] Follower returning leader response: " + leaderResponse);
        return leaderResponse;
        
    } else {
        System.out.println("[" + serverIp + "] Invalid command: " + cmd);
        return "INVALID_COMMAND";
    }
}
```

**OWN Operation**: 
- **Local processing**: Reads from local map (may be slightly stale)
- **No forwarding**: Reduces latency for read operations

**LOCK/UNLOCK Operations**:
- **Forwarding**: Sends request to leader
- **Local update**: Updates local map if leader approves
- **Consistency**: Maintains local consistency with leader

### Leader Communication

```java
private String forwardToLeader(String cmd, String lockName, String clientId) {
    try (Socket leaderSocket = new Socket("10.0.2.3", 5000);
         PrintWriter out = new PrintWriter(leaderSocket.getOutputStream(), true);
         BufferedReader in = new BufferedReader(new InputStreamReader(leaderSocket.getInputStream()))) {

        // Set timeout for the socket
        leaderSocket.setSoTimeout(10000); // 10 seconds timeout

        String request = cmd + "," + lockName + "," + clientId;
        System.out.println("[" + serverIp + "] Forwarding to leader: " + request);
        out.println(request);
        
        String response = in.readLine();
        System.out.println("[" + serverIp + "] Received from leader: " + response);
        return response != null ? response : "ERROR";

    } catch (java.net.SocketTimeoutException e) {
        System.err.println("[" + serverIp + "] Timeout while forwarding to leader: " + e.getMessage());
        return "TIMEOUT";
    } catch (IOException e) {
        System.err.println("[" + serverIp + "] Error forwarding to leader: " + e.getMessage());
        return "ERROR";
    }
}
```

**Features**:
- **Timeout handling**: 10-second timeout prevents hanging
- **Error recovery**: Returns appropriate error codes
- **Logging**: Detailed logging for debugging

### Follower Notification

```java
private void notifyFollowers(String message) {
    System.out.println("[" + serverIp + "] Notifying " + followerServers.size() + " followers with message: " + message);
    
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
                
                // Wait for ACK
                String ack = in.readLine();
                if ("ACK".equals(ack)) {
                    System.out.println("[" + serverIp + "] Received ACK from " + follower);
                } else {
                    System.err.println("[" + serverIp + "] Unexpected response from " + follower + ": " + ack);
                }
                
            } catch (IOException e) {
                System.err.println("[" + serverIp + "] Failed to notify follower " + follower + ": " + e.getMessage());
            }
        });
    }
}
```

**Process**:
1. **Parallel notification**: Each follower notified in separate thread
2. **Timeout protection**: 5-second timeout per follower
3. **ACK verification**: Confirms message delivery
4. **Error handling**: Continues if individual followers fail

### Synchronization Processing

```java
private synchronized void processSync(String syncMsg) {
    // syncMsg is "SYNC,CMD,lockName,clientId"
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
}
```

**Synchronization Logic**:
- **LOCK**: Adds lock to local map
- **UNLOCK**: Removes lock from local map
- **Thread safety**: Uses `synchronized` for safe map updates

### Server Registration

```java
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
                System.out.println("✅ [" + serverIp + "] Successfully registered with leader");
            } else {
                System.err.println("❌ [" + serverIp + "] Registration failed. Response: " + response);
            }
            
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("⏰ [" + serverIp + "] Timeout while registering with leader: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("❌ [" + serverIp + "] Error registering with leader: " + e.getMessage());
            System.err.println("💡 Make sure the leader server is running on 10.0.2.3:5000");
        }
    } else {
        System.out.println("[" + serverIp + "] This is the leader server, no registration needed");
    }
}
```

**Registration Process**:
1. **Leader check**: Only followers register
2. **Timeout protection**: 5-second timeout
3. **Response validation**: Confirms successful registration
4. **Error handling**: Provides helpful error messages

### Status Monitoring

```java
public void printStatus() {
    System.out.println("\n📊 === SERVER STATUS ===");
    System.out.println("📍 Server IP: " + serverIp);
    System.out.println("🔌 Port: " + port);
    System.out.println("👑 Role: " + (isLeader ? "LEADER" : "FOLLOWER"));
    System.out.println("🔒 Active locks: " + lockMap.size());
    System.out.println("👥 Registered followers: " + followerServers.size());
    
    if (!lockMap.isEmpty()) {
        System.out.println("🔐 Current locks:");
        lockMap.forEach((lockName, clientId) -> 
            System.out.println("   - " + lockName + " -> " + clientId));
    }
    
    if (!followerServers.isEmpty()) {
        System.out.println("👥 Followers:");
        followerServers.forEach(follower -> 
            System.out.println("   - " + follower));
    }
    System.out.println("========================\n");
}
```

**Status Information**:
- **Server identity**: IP, port, role
- **Lock state**: Number and details of active locks
- **Follower state**: Number and list of registered followers

### Main Method

```java
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
        System.out.println("\n🛑 Server shutting down...");
        server.printStatus();
    }));
    
    server.start();
}
```

**Startup Process**:
1. **Argument parsing**: Validates command-line arguments
2. **Server creation**: Creates server instance
3. **Registration**: Followers register with leader
4. **Status display**: Shows initial server status
5. **Shutdown hook**: Displays final status on exit
6. **Server start**: Begins main server loop

## 👤 Client.java - Complete Analysis

### Class Overview

The `Client` class provides a simple interface for interacting with the distributed lock system.

```java
public class Client {
    private String serverIp;    // Target server IP
    private int serverPort;      // Target server port
    private String clientId;     // Unique client identifier
}
```

### Constructor

```java
public Client(String serverIp, int serverPort, String clientId) {
    this.serverIp = serverIp;
    this.serverPort = serverPort;
    this.clientId = clientId;
}
```

**Parameters**:
- `serverIp`: IP address of server to connect to
- `serverPort`: Port number of server
- `clientId`: Unique identifier for this client

### Message Sending

```java
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
```

**Features**:
- **Resource management**: Uses try-with-resources for automatic cleanup
- **Error handling**: Returns "ERROR" on connection failure
- **Simple protocol**: Sends message, waits for response

### Lock Operations

#### Try Lock

```java
public void tryLock(String lockName, String lockKey) {
    String response = sendMsg("LOCK," + lockName + "," + clientId);
    System.out.println("Client " + clientId + " - TryLock(" + lockName + ") Response: " + response);
}
```

**Message Format**: `LOCK,<lockName>,<clientId>`

**Behavior**: Attempts to acquire a distributed lock

#### Try Unlock

```java
public void tryUnLock(String lockName, String lockKey) {
    String response = sendMsg("UNLOCK," + lockName + "," + clientId);
    System.out.println("Client " + clientId + " - TryUnlock(" + lockName + ") Response: " + response);
}
```

**Message Format**: `UNLOCK,<lockName>,<clientId>`

**Behavior**: Attempts to release a distributed lock

#### Check Owner

```java
public String ownTheLock(String lockName, String lockKey) {
    String response = sendMsg("OWN," + lockName + "," + clientId);
    System.out.println("Client " + clientId + " - Owner of " + lockName + ": " + response);
    return response;
}
```

**Message Format**: `OWN,<lockName>,<clientId>`

**Behavior**: Queries the current owner of a lock

### Test Sequence

```java
public void testLockSequence(String lockName) {
    System.out.println("\n=== Testing Lock Sequence for " + lockName + " ===");
    
    // Check initial owner
    ownTheLock(lockName, "");
    
    // Try to acquire lock
    tryLock(lockName, "");
    
    // Check owner after acquisition
    ownTheLock(lockName, "");
    
    // Try to acquire same lock again (should fail)
    tryLock(lockName, "");
    
    // Release lock
    tryUnLock(lockName, "");
    
    // Check owner after release
    ownTheLock(lockName, "");
    
    System.out.println("=== End of Test Sequence ===\n");
}
```

**Test Sequence**:
1. **Initial check**: Verify no owner
2. **Acquisition**: Attempt to acquire lock
3. **Verification**: Confirm ownership
4. **Duplicate attempt**: Try to acquire again (should fail)
5. **Release**: Release the lock
6. **Final check**: Verify no owner

### Main Method

```java
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
    client.tryLock("sharedLock", "");
    client.ownTheLock("sharedLock", "");
}
```

**Usage**: `java Client <server_ip> <server_port> <client_id>`

**Test Execution**:
- Tests multiple lock sequences
- Simulates concurrent access

## 🧪 DistributedLockTest.java - Complete Analysis

### Class Overview

The `DistributedLockTest` class provides automated testing for the distributed lock system.

### Main Test Method

```java
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
```

**Test Scenarios**:
1. **Concurrent access**: Multiple clients competing for same lock
2. **Different locks**: Each client acquires different locks
3. **Thread management**: Uses thread pool for concurrent execution

### Individual Client Test

```java
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
```

**Test Steps**:
1. **Acquisition**: Attempt to acquire lock
2. **Verification**: Check ownership
3. **Release**: Release the lock
4. **Final check**: Verify release

## 🔄 Communication Flow

### Client to Follower Flow

```
Client → Follower → Leader → Followers → Client
```

1. **Client request**: LOCK/UNLOCK operation
2. **Follower forwarding**: Forwards to leader
3. **Leader processing**: Validates and updates map
4. **Synchronization**: Notifies all followers
5. **Response**: Returns result to client

### Client to Leader Flow

```
Client → Leader → Followers → Client
```

1. **Direct request**: Client connects directly to leader
2. **Leader processing**: Validates and updates map
3. **Synchronization**: Notifies all followers
4. **Response**: Returns result to client

### OWN Operation Flow

```
Client → Server → Client
```

1. **Direct query**: Client queries any server
2. **Local response**: Server responds from local map
3. **No synchronization**: Read-only operation

## 📊 Data Structures

### Lock Map

```java
private Map<String, String> lockMap = new ConcurrentHashMap<>();
```

**Structure**: `Map<LockName, ClientId>`

**Thread Safety**: Uses `ConcurrentHashMap` for thread-safe access

**Operations**:
- **PUT**: Add lock with owner
- **REMOVE**: Remove lock
- **GET**: Query lock owner

### Follower List

```java
private List<String> followerServers = new ArrayList<>();
```

**Structure**: `List<"IP:PORT">`

**Usage**: Tracks registered follower servers

**Operations**:
- **ADD**: Register new follower
- **ITERATE**: Notify all followers

## 🧵 Threading Model

### Thread Pool

```java
private ExecutorService threadPool = Executors.newCachedThreadPool();
```

**Type**: Cached thread pool

**Usage**: Handles incoming connections

**Benefits**: 
- Automatic thread management
- Efficient resource utilization
- Scalable to connection load

### Synchronization

```java
private synchronized String processRequest(String cmd, String lockName, String clientId)
private synchronized void processSync(String syncMsg)
```

**Critical Sections**:
- Request processing
- Synchronization processing

**Protection**: Ensures thread-safe access to shared data

## ⚠️ Error Handling

### Connection Errors

```java
catch (IOException e) {
    System.err.println("Connection error: " + e.getMessage());
    return "ERROR";
}
```

**Handling**: Returns error codes instead of crashing

### Timeout Errors

```java
catch (java.net.SocketTimeoutException e) {
    System.err.println("Timeout while forwarding to leader: " + e.getMessage());
    return "TIMEOUT";
}
```

**Protection**: Prevents hanging on network issues

### Validation Errors

```java
if (parts.length < 3) {
    System.out.println("Invalid message format: " + msg);
    out.println("INVALID_FORMAT");
    return;
}
```

**Validation**: Checks message format before processing

## 📡 Protocol Details

### Message Formats

| Message Type | Format | Example |
|--------------|--------|---------|
| LOCK | `LOCK,<name>,<client>` | `LOCK,myLock,Client1` |
| UNLOCK | `UNLOCK,<name>,<client>` | `UNLOCK,myLock,Client1` |
| OWN | `OWN,<name>,<client>` | `OWN,myLock,Client1` |
| SYNC | `SYNC,<cmd>,<name>,<client>` | `SYNC,LOCK,myLock,Client1` |
| REGISTER | `REGISTER,<ip>:<port>` | `REGISTER,10.0.2.4:5000` |

### Response Codes

| Code | Meaning | Usage |
|------|---------|-------|
| `SUCCESS` | Operation succeeded | LOCK/UNLOCK success |
| `FAIL` | Operation failed | LOCK/UNLOCK failure |
| `NONE` | No owner | OWN query result |
| `ERROR` | System error | Connection/processing error |
| `TIMEOUT` | Network timeout | Communication timeout |
| `ACK` | Acknowledgment | Synchronization confirmation |

### Timeout Values

| Operation | Timeout | Reason |
|-----------|---------|--------|
| Follower notification | 5 seconds | Quick sync confirmation |
| Leader communication | 10 seconds | Allow for processing time |
| Registration | 5 seconds | Quick startup confirmation |

---

This documentation provides a complete understanding of every aspect of the distributed lock system. Each class, method, and concept is explained in detail to facilitate understanding and maintenance of the codebase.
