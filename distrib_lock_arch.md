```mermaid
graph TD
    %% Configuration des classes et des rôles
    subgraph Components
        A[Client.java]
        B{Server: Leader<br>(10.0.2.3:5000)}
        C{Server: Follower 1<br>(10.0.2.4:5000)}
        D{Server: Follower 2<br>(10.0.2.5:5000)}
    end

    %% Représentation des Data Structures Clés
    subgraph Data Structures
        E[Leader: lockMap<br>(ConcurrentHashMap)]
        F[Follower 1: lockMap<br>(ConcurrentHashMap)]
        G[Follower 2: lockMap<br>(ConcurrentHashMap)]
        H[Leader: followerServers<br>(List)]
    end
    
    %% Connexion et Enregistrement Initial
    subgraph Initial Setup
        C -- REGISTER (10.0.2.4:5000) --> B
        D -- REGISTER (10.0.2.5:5000) --> B
        B -- Store Follower Info --> H
    end

    %% Communication Flow: Write Operation (LOCK/UNLOCK)
    subgraph Write Operation (e.g., LOCK)
        direction LR
        
        %% Client se connecte à n'importe quel serveur (ici, Follower)
        A -- 1. LOCK/UNLOCK Request (Client-to-Server Protocol) --> C
        
        %% Le Follower forwarde au Leader
        C -- 2. Forward Request (LOCK/UNLOCK, Client-to-Leader Protocol) --> B
        
        %% Le Leader exécute la logique
        B -- 3. Update/Check Lock Logic --> E
        E -- lockMap State Change --> B
        
        %% Synchronisation du Leader vers les Followers
        B -- 4. SYNC (LOCK/UNLOCK, Leader-to-Follower Protocol) --> C
        B -- 4. SYNC (LOCK/UNLOCK, Leader-to-Follower Protocol) --> D
        
        C -- 5. Update Local Map --> F
        D -- 5. Update Local Map --> G
        
        %% ACK de Sync
        C -- 6. ACK --> B
        D -- 6. ACK --> B
        
        %% Réponse finale au Client
        B -- 7. Response (SUCCESS/FAIL) --> C
        C -- 8. Response (SUCCESS/FAIL) --> A
    end

    %% Communication Flow: Read Operation (OWN)
    subgraph Read Operation (OWN)
        direction LR
        
        %% Client se connecte à n'importe quel serveur (ici, Leader)
        A -- OWN Request (Client-to-Server Protocol) --> B
        
        %% Le Server (Leader ou Follower) répond directement
        B -- Access Local Map --> E
        E -- Read Owner --> B
        B -- Response (Owner/NONE) --> A
        
        %% Alternative pour Follower
        A -- OWN Request (Client-to-Server Protocol) --> C
        C -- Access Local Map --> F
        F -- Read Owner --> C
        C -- Response (Owner/NONE) --> A
    end

    %% Lien des Data Structures aux Servers
    B --> E
    B --> H
    C --> F
    D --> G

    %% Styles pour la clarté
    classDef role fill:#f9f,stroke:#333,stroke-width:2px;
    class B,C,D role
    classDef client fill:#ccf,stroke:#333;
    class A client
    classDef data fill:#afa,stroke:#333;
    class E,F,G,H data
```