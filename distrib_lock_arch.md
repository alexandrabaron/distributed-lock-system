```mermaid
graph TD
    %% Define Node Shapes and Roles
    subgraph System Components
        A[Client]
        B[Server: Leader<br>10.0.2.3:5000]
        C[Server: Follower 1<br>10.0.2.4:5000]
        D[Server: Follower 2<br>10.0.2.5:5000]
    end

    subgraph Core Data Structures
        E((Leader lockMap<br>ConcurrentHashMap))
        F((Follower 1 lockMap))
        G((Follower 2 lockMap))
        H((Leader followerServers<br>List of IPs))
    end
    
    %% Initial Setup (Registration)
    subgraph Initial Setup
        C -- REGISTER (IP:Port) --> B
        D -- REGISTER (IP:Port) --> B
        B -- Stores Follower Info --> H
    end

    %% Write Operation (LOCK/UNLOCK) - Requires Leader Consensus
    subgraph Write Operation (LOCK/UNLOCK)
        direction LR
        
        A -- 1. LOCK Request (Client-to-Server) --> C
        
        C -- 2. Forward LOCK Request (to Leader) --> B
        
        B -- 3. Update Lock Logic --> E
        
        %% Leader Synchronization
        B -- 4. SYNC Lock State (Leader-to-Follower) --> C
        B -- 4. SYNC Lock State (Leader-to-Follower) --> D
        
        C -- 5. Update Local Map --> F
        D -- 5. Update Local Map --> G
        
        C -- 6. ACK Sync --> B
        D -- 6. ACK Sync --> B
        
        B -- 7. Final Response (Success/Fail) --> C
        C -- 8. Relays Response (to Client) --> A
    end

    %% Read Operation (OWN) - Local Read Optimization
    subgraph Read Operation (OWN)
        direction LR
        
        A -- OWN Request --> B
        B -- Check Local Lock State --> E
        B -- Response (Owner/NONE) --> A
        
        A -- OWN Request --> C
        C -- Check Local Lock State --> F
        C -- Response (Owner/NONE) --> A
    end

    %% Connect Data to Servers
    B -. owns .-> E
    B -. manages .-> H
    C -. replicates .-> F
    D -. replicates .-> G
    
    %% Styles
    classDef leader fill:#f9f,stroke:#333,stroke-width:2px;
    class B leader
    classDef follower fill:#fcc,stroke:#333;
    class C,D follower
    classDef client fill:#ccf,stroke:#333;
    class A client
    classDef data fill:#afa,stroke:#333;
    class E,F,G,H data
```