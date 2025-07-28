# Pocket Agent Server Architecture

This document provides detailed architectural diagrams and explanations of the Pocket Agent Server components and their interactions.

## Table of Contents

- [System Overview](#system-overview)
- [Component Architecture](#component-architecture)
- [Data Flow](#data-flow)
- [Sequence Diagrams](#sequence-diagrams)
- [State Management](#state-management)
- [Deployment Architecture](#deployment-architecture)

## System Overview

### High-Level Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        AC[Android Client]
        RC1[React Client 1]
        RC2[React Client 2]
        TC[Terminal Client]
    end
    
    subgraph "Network Layer"
        LB[Load Balancer<br/>Optional]
        RP[Reverse Proxy<br/>nginx/traefik]
    end
    
    subgraph "Server Layer"
        WS[WebSocket Server<br/>:8443]
        API[REST API<br/>Future]
    end
    
    subgraph "Business Logic"
        PM[Project Manager]
        CE[Claude Executor]
        SM[Session Manager]
        ML[Message Logger]
    end
    
    subgraph "Storage Layer"
        FS[(File System<br/>./data/)]
        RD[(Redis<br/>Future)]
        PG[(PostgreSQL<br/>Future)]
    end
    
    subgraph "External Services"
        CLI[Claude CLI]
        MON[Monitoring<br/>Prometheus]
        LOG[Logging<br/>ELK Stack]
    end
    
    AC -.->|WSS| LB
    RC1 -.->|WSS| LB
    RC2 -.->|WSS| LB
    TC -.->|WSS| LB
    
    LB --> RP
    RP --> WS
    
    WS --> PM
    WS --> CE
    WS --> SM
    WS --> ML
    
    PM --> FS
    CE --> CLI
    SM --> FS
    ML --> FS
    
    WS --> MON
    WS --> LOG
```

### Component Interaction

```mermaid
graph LR
    subgraph "WebSocket Handler"
        WSH[Handler]
        Router[Message Router]
        Broadcast[Broadcaster]
    end
    
    subgraph "Domain Services"
        PS[Project Service]
        CS[Claude Service]
        SS[Session Service]
        HS[Health Service]
    end
    
    subgraph "Infrastructure"
        PR[Project Repository]
        CE[Claude Executor]
        ML[Message Log]
        SM[Session Manager]
    end
    
    WSH --> Router
    Router --> PS
    Router --> CS
    Router --> SS
    Router --> HS
    
    PS --> PR
    CS --> CE
    SS --> SM
    PS --> ML
    
    Broadcast --> WSH
    PS --> Broadcast
    CS --> Broadcast
```

## Component Architecture

### Hexagonal Architecture Pattern

```mermaid
graph TB
    subgraph "Ports (Interfaces)"
        WP[WebSocket Port]
        HP[HTTP Port]
        MP[Metrics Port]
    end
    
    subgraph "Primary Adapters"
        WSA[WebSocket Adapter]
        HA[HTTP Adapter]
        MA[Metrics Adapter]
    end
    
    subgraph "Application Core"
        subgraph "Use Cases"
            CPU[Create Project]
            EPU[Execute Prompt]
            GPU[Get Messages]
            KPU[Kill Process]
        end
        
        subgraph "Domain"
            P[Project]
            S[Session]
            M[Message]
            E[Execution]
        end
    end
    
    subgraph "Secondary Ports"
        SPR[Project Repository Port]
        SCE[Claude Executor Port]
        SML[Message Logger Port]
    end
    
    subgraph "Secondary Adapters"
        FPR[File Project Repository]
        PCE[Process Claude Executor]
        FML[File Message Logger]
    end
    
    WP --> WSA
    HP --> HA
    MP --> MA
    
    WSA --> CPU
    WSA --> EPU
    WSA --> GPU
    WSA --> KPU
    
    CPU --> P
    EPU --> E
    GPU --> M
    KPU --> E
    
    P --> SPR
    E --> SCE
    M --> SML
    
    SPR --> FPR
    SCE --> PCE
    SML --> FML
```

### Class Diagram

```mermaid
classDiagram
    class Server {
        -config Config
        -wsHandler WebSocketHandler
        -projectMgr ProjectManager
        -executor ClaudeExecutor
        -sessionMgr SessionManager
        +Start() error
        +Shutdown(ctx) error
        -setupRoutes()
        -broadcastStats()
    }
    
    class WebSocketHandler {
        -upgrader websocket.Upgrader
        -projectMgr ProjectManager
        -executor ClaudeExecutor
        -sessions map[string]Session
        -mu sync.RWMutex
        +HandleUpgrade(w, r)
        +RouteMessage(session, msg)
        +BroadcastToProject(projectID, msg)
        -addSession(session)
        -removeSession(sessionID)
    }
    
    class ProjectManager {
        -projects map[string]Project
        -dataDir string
        -mu sync.RWMutex
        -maxProjects int
        +CreateProject(path) (Project, error)
        +DeleteProject(id) error
        +GetProject(id) (Project, error)
        +ListProjects() []Project
        +UpdateSession(projectID, sessionID) error
        -validatePath(path) error
        -checkNesting(path) error
        -saveMetadata(project) error
        -loadProjects() error
    }
    
    class ClaudeExecutor {
        -processes map[string]Process
        -mu sync.Mutex
        -timeout time.Duration
        -claudePath string
        +Execute(ctx, project, cmd) (Response, error)
        +Kill(projectID) error
        -buildCommand(project, cmd) []string
        -parseOutput(data) (Message, error)
        -trackProcess(projectID, cmd)
        -untrackProcess(projectID)
    }
    
    class Project {
        +ID string
        +Path string
        +SessionID string
        +State ProjectState
        +CreatedAt time.Time
        +LastActive time.Time
        +MessageLog MessageLog
        +Subscribers map[string]Session
        +mu sync.RWMutex
        +SetState(state)
        +AddSubscriber(session)
        +RemoveSubscriber(sessionID)
        +BroadcastMessage(msg)
    }
    
    class Session {
        +ID string
        +Conn websocket.Conn
        +ProjectID string
        +CreatedAt time.Time
        +LastPing time.Time
        +send chan Message
        +done chan struct{}
        +SendMessage(msg) error
        +Close()
    }
    
    class MessageLog {
        -projectID string
        -logDir string
        -currentFile File
        -mu sync.Mutex
        -rotationSize int64
        -messageCount int
        +Append(msg) error
        +GetMessagesSince(time) []Message
        -rotateIfNeeded() error
        -openLogFile() error
        -writeMessage(msg) error
    }
    
    Server --> WebSocketHandler
    Server --> ProjectManager
    Server --> ClaudeExecutor
    
    WebSocketHandler --> ProjectManager
    WebSocketHandler --> ClaudeExecutor
    WebSocketHandler --> Session
    
    ProjectManager --> Project
    Project --> MessageLog
    Project --> Session
    
    ClaudeExecutor --> Project
```

## Data Flow

### Message Processing Flow

```mermaid
flowchart TB
    Client[Client Application]
    WS[WebSocket Connection]
    Val[Message Validator]
    Router[Message Router]
    
    subgraph "Message Handlers"
        PCH[Project Create Handler]
        PLH[Project List Handler]
        PDH[Project Delete Handler]
        PJH[Project Join Handler]
        EXH[Execute Handler]
        AKH[Agent Kill Handler]
        GMH[Get Messages Handler]
    end
    
    PM[Project Manager]
    CE[Claude Executor]
    ML[Message Log]
    
    Broadcast[Broadcast Manager]
    Subs[Subscribers]
    
    Client -->|JSON Message| WS
    WS --> Val
    Val -->|Valid| Router
    Val -->|Invalid| Client
    
    Router --> PCH
    Router --> PLH
    Router --> PDH
    Router --> PJH
    Router --> EXH
    Router --> AKH
    Router --> GMH
    
    PCH --> PM
    PLH --> PM
    PDH --> PM
    PJH --> PM
    
    EXH --> CE
    AKH --> CE
    
    GMH --> ML
    
    PM --> Broadcast
    CE --> Broadcast
    ML --> Client
    
    Broadcast --> Subs
    Subs --> Client
```

### Execution Flow

```mermaid
flowchart LR
    subgraph "Request Phase"
        RM[Receive Message]
        VP[Validate Prompt]
        GP[Get Project]
        CS[Check State]
    end
    
    subgraph "Execution Phase"
        AL[Acquire Lock]
        BC[Build Command]
        SP[Start Process]
        TO[Track Output]
    end
    
    subgraph "Output Phase"
        PO[Parse Output]
        BM[Broadcast Messages]
        US[Update Session]
        SM[Save Messages]
    end
    
    subgraph "Completion Phase"
        UP[Update Project]
        RL[Release Lock]
        NS[Notify Subscribers]
        CL[Cleanup]
    end
    
    RM --> VP
    VP --> GP
    GP --> CS
    CS --> AL
    AL --> BC
    BC --> SP
    SP --> TO
    TO --> PO
    PO --> BM
    BM --> US
    US --> SM
    SM --> UP
    UP --> RL
    RL --> NS
    NS --> CL
```

## Sequence Diagrams

### Project Creation and Execution

```mermaid
sequenceDiagram
    participant C as Client
    participant WS as WebSocket Handler
    participant PM as Project Manager
    participant FS as File System
    participant CE as Claude Executor
    participant CLI as Claude CLI
    
    C->>WS: Connect WebSocket
    WS->>WS: Create Session
    WS-->>C: Connection Established
    
    C->>WS: project_create {path: "/project"}
    WS->>PM: CreateProject(path)
    PM->>PM: Validate Path
    PM->>PM: Check Nesting
    PM->>PM: Generate UUID
    PM->>FS: Save Metadata
    PM->>FS: Create Log Directory
    PM-->>WS: Project Created
    WS-->>C: project_state {id, state: IDLE}
    
    C->>WS: project_join {project_id}
    WS->>PM: GetProject(id)
    PM-->>WS: Project
    WS->>WS: Add Subscriber
    WS-->>C: project_joined
    
    C->>WS: execute {prompt: "Hello"}
    WS->>PM: GetProject(id)
    PM-->>WS: Project
    WS->>CE: Execute(project, command)
    CE->>CE: Acquire Lock
    CE->>CE: Update State(EXECUTING)
    CE-->>C: project_state {state: EXECUTING}
    
    CE->>CLI: claude -p "Hello" -c session_id
    CLI-->>CE: JSON Stream
    CE->>CE: Parse Messages
    CE-->>C: agent_message (streaming)
    
    CLI-->>CE: Execution Complete
    CE->>PM: Update Session ID
    CE->>FS: Save Messages
    CE->>CE: Update State(IDLE)
    CE->>CE: Release Lock
    CE-->>C: project_state {state: IDLE}
```

### Multi-Client Synchronization

```mermaid
sequenceDiagram
    participant C1 as Client 1
    participant C2 as Client 2
    participant WS as WebSocket Handler
    participant PM as Project Manager
    participant B as Broadcast Manager
    
    C1->>WS: Connect
    C2->>WS: Connect
    
    C1->>WS: project_create
    WS->>PM: CreateProject
    PM-->>WS: Project
    WS->>B: Broadcast(project_state)
    B-->>C1: project_state
    
    C2->>WS: project_list
    WS->>PM: ListProjects
    PM-->>WS: Projects
    WS-->>C2: project_list_response
    
    C2->>WS: project_join(project_id)
    WS->>PM: AddSubscriber
    WS-->>C2: project_joined
    
    C1->>WS: execute(prompt)
    WS->>PM: Execute
    WS->>B: Broadcast(executing)
    B-->>C1: project_state
    B-->>C2: project_state
    
    Note over WS,B: Claude Execution
    
    loop Streaming Output
        WS->>B: Broadcast(agent_message)
        B-->>C1: agent_message
        B-->>C2: agent_message
    end
    
    WS->>B: Broadcast(idle)
    B-->>C1: project_state
    B-->>C2: project_state
```

### Error Handling Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant WS as WebSocket Handler
    participant PM as Project Manager
    participant CE as Claude Executor
    participant EH as Error Handler
    
    C->>WS: execute {invalid project}
    WS->>PM: GetProject(invalid_id)
    PM-->>WS: Error: Not Found
    WS->>EH: HandleError(PROJECT_NOT_FOUND)
    EH-->>C: error {code: PROJECT_NOT_FOUND}
    
    C->>WS: project_create {path: "../etc"}
    WS->>PM: CreateProject(path)
    PM->>PM: ValidatePath
    PM-->>WS: Error: Invalid Path
    WS->>EH: HandleError(INVALID_PATH)
    EH-->>C: error {code: INVALID_PATH}
    
    C->>WS: execute {timeout scenario}
    WS->>CE: Execute(long_running)
    CE->>CE: Start with Timeout
    Note over CE: 5 minutes pass
    CE->>CE: Context Cancelled
    CE->>CE: Kill Process
    CE-->>WS: Error: Timeout
    WS->>EH: HandleError(EXECUTION_TIMEOUT)
    EH-->>C: error {code: EXECUTION_TIMEOUT}
```

## State Management

### Project State Machine

```mermaid
stateDiagram-v2
    [*] --> IDLE: Project Created
    IDLE --> EXECUTING: Execute Command
    EXECUTING --> IDLE: Execution Complete
    EXECUTING --> ERROR: Execution Failed
    ERROR --> IDLE: Error Acknowledged
    EXECUTING --> IDLE: Process Killed
    IDLE --> [*]: Project Deleted
    ERROR --> [*]: Project Deleted
```

### Connection State Management

```mermaid
stateDiagram-v2
    [*] --> CONNECTING: WebSocket Upgrade
    CONNECTING --> CONNECTED: Handshake Success
    CONNECTING --> [*]: Handshake Failed
    
    CONNECTED --> AUTHENTICATED: Future Auth
    AUTHENTICATED --> SUBSCRIBED: Join Project
    SUBSCRIBED --> AUTHENTICATED: Leave Project
    
    CONNECTED --> IDLE: No Activity
    IDLE --> ACTIVE: Send/Receive Message
    ACTIVE --> IDLE: No Activity
    
    IDLE --> DISCONNECTING: Idle Timeout
    ACTIVE --> DISCONNECTING: Client Disconnect
    DISCONNECTING --> [*]: Cleanup Complete
```

### Message Flow States

```mermaid
flowchart TB
    subgraph "Message Lifecycle"
        REC[Received]
        VAL[Validated]
        QUE[Queued]
        PRO[Processing]
        COM[Complete]
        ERR[Error]
    end
    
    REC --> VAL
    VAL --> QUE
    VAL --> ERR
    QUE --> PRO
    PRO --> COM
    PRO --> ERR
    
    subgraph "Broadcast States"
        PEN[Pending]
        SEN[Sending]
        ACK[Acknowledged]
        FAI[Failed]
    end
    
    COM --> PEN
    PEN --> SEN
    SEN --> ACK
    SEN --> FAI
    FAI --> PEN
```

## Deployment Architecture

### Single Server Deployment

```mermaid
graph TB
    subgraph "Server Host"
        subgraph "Docker Container"
            APP[Pocket Agent Server]
            VOL1[Data Volume]
            VOL2[Certs Volume]
        end
        
        subgraph "Host Services"
            SYSD[Systemd]
            FW[Firewall]
            LOGS[Log Rotation]
        end
        
        subgraph "Monitoring"
            PROM[Prometheus]
            GRAF[Grafana]
            ALERT[Alertmanager]
        end
    end
    
    subgraph "External"
        CDN[CDN/DDoS Protection]
        DNS[DNS]
        CERT[Let's Encrypt]
    end
    
    CDN --> APP
    DNS --> CDN
    CERT --> APP
    
    APP --> VOL1
    APP --> VOL2
    
    SYSD --> APP
    FW --> APP
    LOGS --> VOL1
    
    APP --> PROM
    PROM --> GRAF
    PROM --> ALERT
```

### High Availability Deployment (Future)

```mermaid
graph TB
    subgraph "Load Balancer Layer"
        LB1[HAProxy/Nginx 1]
        LB2[HAProxy/Nginx 2]
        VIP[Virtual IP]
    end
    
    subgraph "Application Layer"
        subgraph "Server 1"
            APP1[PA Server]
            SYNC1[Sync Agent]
        end
        
        subgraph "Server 2"
            APP2[PA Server]
            SYNC2[Sync Agent]
        end
        
        subgraph "Server 3"
            APP3[PA Server]
            SYNC3[Sync Agent]
        end
    end
    
    subgraph "Shared State"
        REDIS[(Redis Cluster)]
        NFS[(Shared Storage)]
    end
    
    subgraph "Monitoring"
        PROM[Prometheus]
        GRAF[Grafana]
        ELK[ELK Stack]
    end
    
    VIP --> LB1
    VIP --> LB2
    
    LB1 --> APP1
    LB1 --> APP2
    LB1 --> APP3
    
    LB2 --> APP1
    LB2 --> APP2
    LB2 --> APP3
    
    APP1 --> REDIS
    APP2 --> REDIS
    APP3 --> REDIS
    
    SYNC1 --> NFS
    SYNC2 --> NFS
    SYNC3 --> NFS
    
    APP1 --> PROM
    APP2 --> PROM
    APP3 --> PROM
```

### Kubernetes Deployment (Future)

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Ingress"
            ING[Nginx Ingress]
            CERT[Cert Manager]
        end
        
        subgraph "Application"
            SVC[Service]
            SS[StatefulSet]
            CM[ConfigMap]
            SEC[Secret]
            
            subgraph "Pods"
                POD1[PA Pod 1]
                POD2[PA Pod 2]
                POD3[PA Pod 3]
            end
        end
        
        subgraph "Storage"
            PVC[PersistentVolumeClaim]
            SC[StorageClass]
        end
        
        subgraph "Monitoring"
            PROM[Prometheus Operator]
            GRAF[Grafana]
            SM[ServiceMonitor]
        end
    end
    
    ING --> SVC
    CERT --> ING
    
    SVC --> POD1
    SVC --> POD2
    SVC --> POD3
    
    SS --> POD1
    SS --> POD2
    SS --> POD3
    
    CM --> SS
    SEC --> SS
    
    PVC --> SS
    SC --> PVC
    
    SM --> PROM
    PROM --> GRAF
```

---

For more detailed information about specific components, see:
- [WebSocket API Design](../../documentation/modules/server/features/websocket-api/design.md)
- [Deployment Guide](../deployment/README.md)
- [Security Architecture](../security/README.md)