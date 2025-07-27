# Pocket Agent Server Architecture Proposal (MVP)

## Executive Summary

This proposal defines a minimal viable product (MVP) architecture for the Pocket Agent server module, which manages Claude CLI SDK executions on a per-project basis. The server handles project-based session management, executes Claude commands sequentially per project, and maintains WebSocket communication with clients.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Management](#project-management)
3. [Claude Execution Model](#claude-execution-model)
4. [Communication Protocol](#communication-protocol)
5. [MVP Implementation](#mvp-implementation)
6. [Future Work: Security](#future-work-security)

## Architecture Overview

### System Context

```
┌─────────────────┐     ┌─────────────────┐     
│  Android Client │     │  React Client   │     
└────────┬────────┘     └────────┬────────┘     
         │ WSS/TLS               │ WSS/TLS       
         └───────────┬───────────┘                
                     │
        ┌────────────┴────────────┐
        │   Pocket Agent Server   │
        │                         │
        │ ┌─────────────────────┐ │
        │ │  WebSocket Handler  │ │
        │ └──────────┬──────────┘ │
        │            │            │
        │ ┌──────────┴──────────┐ │
        │ │  Project Manager    │ │
        │ └──────────┬──────────┘ │
        │            │            │
        │ ┌──────────┴──────────┐ │
        │ │ Claude Executor     │ │
        │ └──────────┬──────────┘ │
        │            │            │
        │    Claude CLI SDK       │
        │  (Sequential Execution) │
        └─────────────────────────┘
```

### Core Components (MVP)

1. **WebSocket Handler**: Manages connections and routes messages by project
2. **Project Manager**: Tracks projects, sessions, and execution state
3. **Claude Executor**: Executes Claude CLI commands sequentially per project
4. **Session Manager**: Maintains Claude session IDs for conversation continuity

## Project Management

### Project Model

```go
type Project struct {
    ID          string              // Unique project identifier
    Path        string              // Absolute path (e.g., /wip/project1)
    SessionID   string              // Current Claude session ID
    State       State               // IDLE, EXECUTING, ERROR
    CreatedAt   time.Time
    LastActive  time.Time
    MessageLog  *MessageLog         // File-based message storage
    Subscribers map[string]*Session // Active sessions watching this project
    mu          sync.Mutex          // Ensures sequential execution
}

type MessageLog struct {
    projectID    string
    logDir       string
    currentFile  *os.File
    currentDate  string // YYYY-MM-DD format
    messageCount int64  // Messages in current file
    mu           sync.Mutex
}

type TimestampedMessage struct {
    Timestamp time.Time       `json:"timestamp"`
    Message   ClaudeMessage   `json:"message"`
    Direction string          `json:"direction"` // "client" or "claude"
}

const (
    maxMessagesPerFile = 10000 // Rotate after 10k messages
    maxFileSizeMB      = 100   // Or rotate after 100MB
)

// Message log operations
func NewMessageLog(projectID string) (*MessageLog, error) {
    // Create project-specific log directory
    logDir := filepath.Join("data", "projects", projectID, "logs")
    if err := os.MkdirAll(logDir, 0755); err != nil {
        return nil, err
    }
    
    ml := &MessageLog{
        projectID: projectID,
        logDir:    logDir,
    }
    
    // Open or create today's log file
    if err := ml.rotateIfNeeded(); err != nil {
        return nil, err
    }
    
    return ml, nil
}

func (ml *MessageLog) rotateIfNeeded() error {
    today := time.Now().Format("2006-01-02")
    
    // Check if we need to rotate based on date
    if ml.currentFile != nil && ml.currentDate != today {
        ml.currentFile.Close()
        ml.currentFile = nil
    }
    
    // Check if we need to rotate based on size/count
    if ml.currentFile != nil {
        stat, err := ml.currentFile.Stat()
        if err == nil {
            sizeMB := stat.Size() / (1024 * 1024)
            if ml.messageCount >= maxMessagesPerFile || sizeMB >= maxFileSizeMB {
                ml.currentFile.Close()
                ml.currentFile = nil
            }
        }
    }
    
    // Open new file if needed
    if ml.currentFile == nil {
        timestamp := time.Now().Format("2006-01-02_15-04-05")
        filename := fmt.Sprintf("messages_%s.jsonl", timestamp)
        logPath := filepath.Join(ml.logDir, filename)
        
        file, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_RDWR, 0644)
        if err != nil {
            return err
        }
        
        ml.currentFile = file
        ml.currentDate = today
        ml.messageCount = 0
        
        // Create symlink to latest file for easy access
        latestLink := filepath.Join(ml.logDir, "latest.jsonl")
        os.Remove(latestLink) // Remove old link
        os.Symlink(filename, latestLink)
    }
    
    return nil
}

func (ml *MessageLog) Append(msg TimestampedMessage) error {
    ml.mu.Lock()
    defer ml.mu.Unlock()
    
    // Check if rotation needed
    if err := ml.rotateIfNeeded(); err != nil {
        return err
    }
    
    data, err := json.Marshal(msg)
    if err != nil {
        return err
    }
    
    _, err = ml.currentFile.Write(append(data, '\n'))
    if err == nil {
        ml.messageCount++
    }
    
    return err
}

func (ml *MessageLog) GetMessagesSince(since time.Time) ([]TimestampedMessage, error) {
    ml.mu.Lock()
    defer ml.mu.Unlock()
    
    var messages []TimestampedMessage
    
    // List all log files
    files, err := ioutil.ReadDir(ml.logDir)
    if err != nil {
        return nil, err
    }
    
    // Sort files by name (which includes timestamp)
    sort.Slice(files, func(i, j int) bool {
        return files[i].Name() < files[j].Name()
    })
    
    // Read files starting from the one that might contain 'since' timestamp
    for _, file := range files {
        if !strings.HasPrefix(file.Name(), "messages_") || !strings.HasSuffix(file.Name(), ".jsonl") {
            continue
        }
        
        // Parse file timestamp from name
        parts := strings.TrimSuffix(strings.TrimPrefix(file.Name(), "messages_"), ".jsonl")
        fileTime, err := time.Parse("2006-01-02_15-04-05", parts)
        if err != nil {
            continue
        }
        
        // Skip files that are definitely too old (with 1 day buffer)
        if fileTime.Add(24 * time.Hour).Before(since) {
            continue
        }
        
        // Read messages from this file
        filePath := filepath.Join(ml.logDir, file.Name())
        fileMessages, err := ml.readMessagesFromFile(filePath, since)
        if err != nil {
            logError("failed to read messages from %s: %v", filePath, err)
            continue
        }
        
        messages = append(messages, fileMessages...)
    }
    
    return messages, nil
}

func (ml *MessageLog) readMessagesFromFile(filePath string, since time.Time) ([]TimestampedMessage, error) {
    file, err := os.Open(filePath)
    if err != nil {
        return nil, err
    }
    defer file.Close()
    
    var messages []TimestampedMessage
    scanner := bufio.NewScanner(file)
    
    for scanner.Scan() {
        var msg TimestampedMessage
        if err := json.Unmarshal(scanner.Bytes(), &msg); err != nil {
            continue
        }
        
        if msg.Timestamp.After(since) {
            messages = append(messages, msg)
        }
    }
    
    return messages, scanner.Err()
}

func (ml *MessageLog) GetStats() (firstTime *time.Time, lastTime *time.Time, count int, err error) {
    ml.mu.Lock()
    defer ml.mu.Unlock()
    
    // List all log files
    files, err := ioutil.ReadDir(ml.logDir)
    if err != nil {
        return nil, nil, 0, err
    }
    
    // Sort files by name
    sort.Slice(files, func(i, j int) bool {
        return files[i].Name() < files[j].Name()
    })
    
    // Read first and last message times
    for i, file := range files {
        if !strings.HasPrefix(file.Name(), "messages_") || !strings.HasSuffix(file.Name(), ".jsonl") {
            continue
        }
        
        filePath := filepath.Join(ml.logDir, file.Name())
        
        // Get first message time from first file
        if firstTime == nil {
            if msg, err := ml.getFirstMessage(filePath); err == nil && msg != nil {
                firstTime = &msg.Timestamp
            }
        }
        
        // Always update last time and count from latest files
        if i == len(files)-1 || files[i+1].Name() == "latest.jsonl" {
            if msg, err := ml.getLastMessage(filePath); err == nil && msg != nil {
                lastTime = &msg.Timestamp
            }
        }
        
        // Count messages
        if c, err := ml.countMessages(filePath); err == nil {
            count += c
        }
    }
    
    return firstTime, lastTime, count, nil
}

func (ml *MessageLog) getFirstMessage(filePath string) (*TimestampedMessage, error) {
    file, err := os.Open(filePath)
    if err != nil {
        return nil, err
    }
    defer file.Close()
    
    scanner := bufio.NewScanner(file)
    if scanner.Scan() {
        var msg TimestampedMessage
        if err := json.Unmarshal(scanner.Bytes(), &msg); err == nil {
            return &msg, nil
        }
    }
    
    return nil, nil
}

func (ml *MessageLog) getLastMessage(filePath string) (*TimestampedMessage, error) {
    // For efficiency, we could optimize this by reading from the end
    // For now, scan the whole file
    file, err := os.Open(filePath)
    if err != nil {
        return nil, err
    }
    defer file.Close()
    
    var lastMsg *TimestampedMessage
    scanner := bufio.NewScanner(file)
    
    for scanner.Scan() {
        var msg TimestampedMessage
        if err := json.Unmarshal(scanner.Bytes(), &msg); err == nil {
            lastMsg = &msg
        }
    }
    
    return lastMsg, scanner.Err()
}

func (ml *MessageLog) countMessages(filePath string) (int, error) {
    file, err := os.Open(filePath)
    if err != nil {
        return 0, err
    }
    defer file.Close()
    
    count := 0
    scanner := bufio.NewScanner(file)
    for scanner.Scan() {
        count++
    }
    
    return count, scanner.Err()
}

type ProjectManager struct {
    projects map[string]*Project // Key: project path
    mu       sync.RWMutex
    dataDir  string
}

// Project metadata for persistence
type ProjectMetadata struct {
    ID         string    `json:"id"`
    Path       string    `json:"path"`
    SessionID  string    `json:"session_id"`
    CreatedAt  time.Time `json:"created_at"`
    LastActive time.Time `json:"last_active"`
}

func NewProjectManager(dataDir string) *ProjectManager {
    pm := &ProjectManager{
        projects: make(map[string]*Project),
        dataDir:  dataDir,
    }
    
    // Load existing projects on startup
    if err := pm.loadProjects(); err != nil {
        logError("failed to load projects: %v", err)
    }
    
    return pm
}

// Save project metadata to disk
func (pm *ProjectManager) saveProjectMetadata(project *Project) error {
    metadataDir := filepath.Join(pm.dataDir, "projects", project.ID)
    if err := os.MkdirAll(metadataDir, 0755); err != nil {
        return err
    }
    
    metadata := ProjectMetadata{
        ID:         project.ID,
        Path:       project.Path,
        SessionID:  project.SessionID,
        CreatedAt:  project.CreatedAt,
        LastActive: project.LastActive,
    }
    
    data, err := json.MarshalIndent(metadata, "", "  ")
    if err != nil {
        return err
    }
    
    metadataFile := filepath.Join(metadataDir, "metadata.json")
    return ioutil.WriteFile(metadataFile, data, 0644)
}

// Load all projects from disk on startup
func (pm *ProjectManager) loadProjects() error {
    projectsDir := filepath.Join(pm.dataDir, "projects")
    if _, err := os.Stat(projectsDir); os.IsNotExist(err) {
        return nil // No projects to load
    }
    
    entries, err := ioutil.ReadDir(projectsDir)
    if err != nil {
        return err
    }
    
    for _, entry := range entries {
        if !entry.IsDir() {
            continue
        }
        
        projectID := entry.Name()
        metadataFile := filepath.Join(projectsDir, projectID, "metadata.json")
        
        data, err := ioutil.ReadFile(metadataFile)
        if err != nil {
            logError("failed to read metadata for project %s: %v", projectID, err)
            continue
        }
        
        var metadata ProjectMetadata
        if err := json.Unmarshal(data, &metadata); err != nil {
            logError("failed to parse metadata for project %s: %v", projectID, err)
            continue
        }
        
        // Recreate message log
        messageLog, err := NewMessageLog(projectID)
        if err != nil {
            logError("failed to recreate message log for project %s: %v", projectID, err)
            continue
        }
        
        // Reconstruct project
        project := &Project{
            ID:          metadata.ID,
            Path:        metadata.Path,
            SessionID:   metadata.SessionID,
            State:       IDLE, // Always start as IDLE after restart
            CreatedAt:   metadata.CreatedAt,
            LastActive:  metadata.LastActive,
            MessageLog:  messageLog,
            Subscribers: make(map[string]*Session),
        }
        
        pm.projects[metadata.Path] = project
        logInfo("loaded project %s at path %s with session %s", projectID, metadata.Path, metadata.SessionID)
    }
    
    logInfo("loaded %d projects from disk", len(pm.projects))
    return nil
}

// Project validation - no nesting allowed
func (pm *ProjectManager) CreateProject(path string) (*Project, error) {
    path = filepath.Clean(path)
    
    // Check for nesting conflicts
    pm.mu.RLock()
    for existingPath := range pm.projects {
        if isNested(path, existingPath) || isNested(existingPath, path) {
            pm.mu.RUnlock()
            return nil, fmt.Errorf("project nesting not allowed: %s conflicts with %s", path, existingPath)
        }
    }
    pm.mu.RUnlock()
    
    projectID := generateID()
    
    // Create message log
    messageLog, err := NewMessageLog(projectID)
    if err != nil {
        return nil, fmt.Errorf("failed to create message log: %w", err)
    }
    
    project := &Project{
        ID:          projectID,
        Path:        path,
        State:       IDLE,
        CreatedAt:   time.Now(),
        MessageLog:  messageLog,
        Subscribers: make(map[string]*Session),
    }
    
    pm.mu.Lock()
    pm.projects[path] = project
    pm.mu.Unlock()
    
    // Save project metadata to disk
    if err := pm.saveProjectMetadata(project); err != nil {
        logError("failed to save project metadata: %v", err)
    }
    
    return project, nil
}

// Update project metadata when session ID changes
func (pm *ProjectManager) UpdateProjectSession(project *Project) error {
    return pm.saveProjectMetadata(project)
}

// Delete project and all associated data
func (pm *ProjectManager) DeleteProject(projectID string) error {
    pm.mu.Lock()
    defer pm.mu.Unlock()
    
    // Find project by ID
    var project *Project
    var projectPath string
    for path, p := range pm.projects {
        if p.ID == projectID {
            project = p
            projectPath = path
            break
        }
    }
    
    if project == nil {
        return fmt.Errorf("project not found: %s", projectID)
    }
    
    // Remove from active projects
    delete(pm.projects, projectPath)
    
    // Delete project data directory
    projectDir := filepath.Join(pm.dataDir, "projects", projectID)
    if err := os.RemoveAll(projectDir); err != nil {
        logError("failed to remove project directory: %v", err)
        // Continue anyway - project is removed from memory
    }
    
    return nil
}

// Get project by ID (not path)
func (pm *ProjectManager) GetProjectByID(projectID string) (*Project, bool) {
    pm.mu.RLock()
    defer pm.mu.RUnlock()
    
    for _, project := range pm.projects {
        if project.ID == projectID {
            return project, true
        }
    }
    return nil, false
}

// Get project by path
func (pm *ProjectManager) GetProject(path string) (*Project, bool) {
    pm.mu.RLock()
    defer pm.mu.RUnlock()
    
    project, exists := pm.projects[path]
    return project, exists
}

// Get all projects
func (pm *ProjectManager) GetAllProjects() []*Project {
    pm.mu.RLock()
    defer pm.mu.RUnlock()
    
    projects := make([]*Project, 0, len(pm.projects))
    for _, p := range pm.projects {
        projects = append(projects, p)
    }
    return projects
}

func isNested(path1, path2 string) bool {
    return strings.HasPrefix(path1, path2+"/") || strings.HasPrefix(path2, path1+"/")
}
```

## Claude Execution Model

### Sequential Execution Per Project

```go
type ClaudeExecutor struct {
    activeProcesses map[string]*exec.Cmd // Track running processes by project ID
    processMu       sync.RWMutex
}

type ExecuteRequest struct {
    ProjectID string
    Prompt    string
    Response  chan *ExecuteResponse
}

type ExecuteResponse struct {
    SessionID string
    Messages  []ClaudeMessage
    Error     error
}

// Execute Claude command for a project
func (e *ClaudeExecutor) Execute(project *Project, cmd ExecuteCommand) (*ExecuteResponse, error) {
    // Ensure sequential execution per project
    project.mu.Lock()
    defer project.mu.Unlock()
    
    // Build command arguments
    args := buildClaudeArgs(cmd, project.SessionID)
    
    // Create command
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
    defer cancel()
    
    command := exec.CommandContext(ctx, "claude", args...)
    command.Dir = project.Path
    
    // Track the process
    e.processMu.Lock()
    e.activeProcesses[project.ID] = command
    e.processMu.Unlock()
    
    // Ensure we remove from tracking when done
    defer func() {
        e.processMu.Lock()
        delete(e.activeProcesses, project.ID)
        e.processMu.Unlock()
    }()
    
    // Execute and capture output
    output, err := command.Output()
    if err != nil {
        project.State = ERROR
        if ctx.Err() == context.DeadlineExceeded {
            return nil, fmt.Errorf("execution timeout exceeded")
        }
        return nil, err
    }
    
    // Parse JSON messages from output
    messages, sessionID, err := parseClaudeOutput(output)
    if err != nil {
        project.State = ERROR
        return nil, err
    }
    
    // Update project state
    project.SessionID = sessionID
    project.State = IDLE
    project.LastActive = time.Now()
    
    return &ExecuteResponse{
        SessionID: sessionID,
        Messages:  messages,
    }, nil
}

// Kill a running Claude execution
func (e *ClaudeExecutor) KillExecution(projectID string) error {
    e.processMu.RLock()
    cmd, exists := e.activeProcesses[projectID]
    e.processMu.RUnlock()
    
    if !exists {
        return fmt.Errorf("no active process for project %s", projectID)
    }
    
    // Kill the process
    if cmd.Process != nil {
        if err := cmd.Process.Kill(); err != nil {
            return fmt.Errorf("failed to kill process: %w", err)
        }
    }
    
    return nil
}

// Build Claude CLI arguments from options
func buildClaudeArgs(cmd ExecuteCommand, sessionID string) []string {
    args := []string{}
    
    // Add options if provided
    if cmd.Options != nil {
        if cmd.Options.DangerouslySkipPermissions {
            args = append(args, "--dangerously-skip-permissions")
        }
        if len(cmd.Options.AllowedTools) > 0 {
            args = append(args, "--allowedTools", strings.Join(cmd.Options.AllowedTools, " "))
        }
        if len(cmd.Options.DisallowedTools) > 0 {
            args = append(args, "--disallowedTools", strings.Join(cmd.Options.DisallowedTools, " "))
        }
        if cmd.Options.MCPConfig != "" {
            args = append(args, "--mcp-config", cmd.Options.MCPConfig)
        }
        if cmd.Options.AppendSystemPrompt != "" {
            args = append(args, "--append-system-prompt", cmd.Options.AppendSystemPrompt)
        }
        if cmd.Options.PermissionMode != "" {
            args = append(args, "--permission-mode", cmd.Options.PermissionMode)
        }
        if cmd.Options.Model != "" {
            args = append(args, "--model", cmd.Options.Model)
        }
        if cmd.Options.FallbackModel != "" {
            args = append(args, "--fallback-model", cmd.Options.FallbackModel)
        }
        if len(cmd.Options.AddDirs) > 0 {
            args = append(args, "--add-dir")
            args = append(args, cmd.Options.AddDirs...)
        }
        if cmd.Options.StrictMCPConfig {
            args = append(args, "--strict-mcp-config")
        }
    }
    
    // Add prompt
    args = append(args, "-p", cmd.Prompt)
    
    // Add session ID if continuing conversation
    if sessionID != "" {
        args = append(args, "-c", sessionID)
    }
    
    return args
}

// Parse streamed JSON messages from Claude output
func parseClaudeOutput(output []byte) ([]ClaudeMessage, string, error) {
    var messages []ClaudeMessage
    var sessionID string
    
    decoder := json.NewDecoder(bytes.NewReader(output))
    for {
        var msg ClaudeMessage
        if err := decoder.Decode(&msg); err == io.EOF {
            break
        } else if err != nil {
            return nil, "", err
        }
        
        messages = append(messages, msg)
        
        // Extract session ID from system init message
        if msg.Type == "system" && msg.Subtype == "init" {
            sessionID = msg.SessionID
        }
    }
    
    return messages, sessionID, nil
}
```

## Communication Protocol

### Message Types

```go
// All messages include project context
type WebSocketMessage struct {
    Type      string          `json:"type"`
    ProjectID string          `json:"project_id,omitempty"`
    Payload   json.RawMessage `json:"payload"`
}

// Client to Server messages
type ClientMessage struct {
    Type      string      `json:"type"` // "execute", "project_create", "project_delete", "project_list", "project_join", "project_leave", "agent_kill", "get_messages", "agent_new_session"
    ProjectID string      `json:"project_id,omitempty"`
    Data      interface{} `json:"data"`
}

// Server to Client messages  
type ServerMessage struct {
    Type      string      `json:"type"` // "agent_message", "update", "error", "project_list", "project_deleted", "message_history"
    ProjectID string      `json:"project_id,omitempty"`
    Data      interface{} `json:"data"`
}

// Specific message types
type ExecuteCommand struct {
    Prompt  string         `json:"prompt"`
    Options *ClaudeOptions `json:"options,omitempty"`
}

type ClaudeOptions struct {
    DangerouslySkipPermissions bool     `json:"dangerously_skip_permissions,omitempty"`
    AllowedTools              []string  `json:"allowed_tools,omitempty"`
    DisallowedTools           []string  `json:"disallowed_tools,omitempty"`
    MCPConfig                 string    `json:"mcp_config,omitempty"`      // File path or JSON string
    AppendSystemPrompt        string    `json:"append_system_prompt,omitempty"`
    PermissionMode            string    `json:"permission_mode,omitempty"`  // "acceptEdits", "bypassPermissions", "default", "plan"
    Model                     string    `json:"model,omitempty"`            // e.g., "sonnet", "opus", "claude-sonnet-4-20250514"
    FallbackModel             string    `json:"fallback_model,omitempty"`
    AddDirs                   []string  `json:"add_dirs,omitempty"`         // Additional directories to allow
    StrictMCPConfig           bool      `json:"strict_mcp_config,omitempty"`
}

type ProjectCreate struct {
    Path string `json:"path"`
}

type ProjectJoin struct {
    ProjectID string `json:"project_id"`
}

type GetMessages struct {
    Since time.Time `json:"since"` // Get messages after this timestamp
}

type AgentMessage struct {
    Messages  []ClaudeMessage `json:"messages"`
    SessionID string          `json:"session_id"`
}

type ProjectListResponse struct {
    Projects []ProjectInfo `json:"projects"`
}

type ProjectInfo struct {
    ID               string    `json:"id"`
    Path             string    `json:"path"`
    State            State     `json:"state"`
    CreatedAt        time.Time `json:"created_at"`
    LastActive       time.Time `json:"last_active"`
    FirstMessageTime *time.Time `json:"first_message_time,omitempty"`
    LastMessageTime  *time.Time `json:"last_message_time,omitempty"`
    MessageCount     int       `json:"message_count"`
}

type ProjectStatus struct {
    ProjectInfo
    SessionID string `json:"session_id"`
}

type UpdateMessage struct {
    UpdateType string      `json:"update_type"` // "stats", "project_state", "project_joined", "session_reset", "execution_killed", "project_deleted"
    Data       interface{} `json:"data"`
}
```

### Message Routing

```go
func (h *WebSocketHandler) RouteMessage(session *Session, msg ClientMessage) {
    switch msg.Type {
    case "execute":
        h.handleExecute(session, msg.ProjectID, msg.Data)
        
    case "project_create":
        h.handleProjectCreate(session, msg.Data)
        
    case "project_delete":
        h.handleProjectDelete(session, msg.ProjectID)
        
    case "project_list":
        h.handleProjectList(session)
        
    case "project_join":
        h.handleProjectJoin(session, msg.Data)
        
    case "project_leave":
        h.handleProjectLeave(session, msg.ProjectID)
        
    case "get_messages":
        h.handleGetMessages(session, msg.ProjectID, msg.Data)
        
    case "agent_kill":
        h.handleAgentKill(session, msg.ProjectID)
        
    case "agent_new_session":
        h.handleAgentNewSession(session, msg.ProjectID)
        
    default:
        h.sendError(session, "", fmt.Errorf("unknown message type: %s", msg.Type))
    }
}

// Execute command in project context
func (h *WebSocketHandler) handleExecute(session *Session, projectID string, data interface{}) {
    var cmd ExecuteCommand
    if err := mapstructure.Decode(data, &cmd); err != nil {
        h.sendError(session, projectID, err)
        return
    }
    
    project, exists := h.projectMgr.GetProjectByID(projectID)
    if !exists {
        h.sendError(session, projectID, fmt.Errorf("project not found"))
        return
    }
    
    // Store user message
    userMsg := TimestampedMessage{
        Timestamp: time.Now(),
        Message: ClaudeMessage{
            Type: "user",
            Message: json.RawMessage(fmt.Sprintf(`{"prompt": "%s"}`, cmd.Prompt)),
        },
        Direction: "client",
    }
    if err := project.MessageLog.Append(userMsg); err != nil {
        logError("failed to log user message: %v", err)
    }
    
    // Update state
    project.State = EXECUTING
    h.broadcastProjectState(project)
    
    // Execute Claude (this blocks until complete)
    response, err := h.executor.Execute(project, cmd)
    if err != nil {
        h.sendError(session, projectID, err)
        return
    }
    
    // Store Claude messages
    for _, msg := range response.Messages {
        claudeMsg := TimestampedMessage{
            Timestamp: time.Now(),
            Message:   msg,
            Direction: "claude",
        }
        if err := project.MessageLog.Append(claudeMsg); err != nil {
            logError("failed to log claude message: %v", err)
        }
    }
    
    // Save updated session ID to disk
    if err := h.projectMgr.UpdateProjectSession(project); err != nil {
        logError("failed to save project session: %v", err)
    }
    
    // Send Claude messages to all project subscribers
    h.broadcastToProject(project, ServerMessage{
        Type:      "agent_message",
        ProjectID: projectID,
        Data:      response,
    })
}

// List all projects
func (h *WebSocketHandler) handleProjectList(session *Session) {
    projects := h.projectMgr.GetAllProjects()
    
    var projectInfos []ProjectInfo
    for _, p := range projects {
        // Get message stats from log
        firstTime, lastTime, count, err := p.MessageLog.GetStats()
        if err != nil {
            logError("failed to get message stats: %v", err)
        }
        
        info := ProjectInfo{
            ID:               p.ID,
            Path:             p.Path,
            State:            p.State,
            CreatedAt:        p.CreatedAt,
            LastActive:       p.LastActive,
            MessageCount:     count,
            FirstMessageTime: firstTime,
            LastMessageTime:  lastTime,
        }
        
        projectInfos = append(projectInfos, info)
    }
    
    msg := ServerMessage{
        Type: "project_list",
        Data: ProjectListResponse{Projects: projectInfos},
    }
    session.Conn.WriteJSON(msg)
}

// Join a project (subscribe to updates)
func (h *WebSocketHandler) handleProjectJoin(session *Session, data interface{}) {
    var join ProjectJoin
    if err := mapstructure.Decode(data, &join); err != nil {
        h.sendError(session, "", err)
        return
    }
    
    project, exists := h.projectMgr.GetProject(join.ProjectID)
    if !exists {
        h.sendError(session, "", fmt.Errorf("project not found"))
        return
    }
    
    // Add session to project subscribers
    project.mu.Lock()
    if project.Subscribers == nil {
        project.Subscribers = make(map[string]*Session)
    }
    project.Subscribers[session.ID] = session
    project.mu.Unlock()
    
    // Get message stats
    firstTime, lastTime, count, _ := project.MessageLog.GetStats()
    
    // Send project status
    status := ProjectStatus{
        ProjectInfo: ProjectInfo{
            ID:               project.ID,
            Path:             project.Path,
            State:            project.State,
            CreatedAt:        project.CreatedAt,
            LastActive:       project.LastActive,
            MessageCount:     count,
            FirstMessageTime: firstTime,
            LastMessageTime:  lastTime,
        },
        SessionID: project.SessionID,
    }
    
    msg := ServerMessage{
        Type:      "update",
        ProjectID: project.ID,
        Data: UpdateMessage{
            UpdateType: "project_joined",
            Data:       status,
        },
    }
    session.Conn.WriteJSON(msg)
}

// Leave a project (unsubscribe)
func (h *WebSocketHandler) handleProjectLeave(session *Session, projectID string) {
    project, exists := h.projectMgr.GetProject(projectID)
    if exists {
        project.mu.Lock()
        delete(project.Subscribers, session.ID)
        project.mu.Unlock()
    }
}

// Get messages since timestamp
func (h *WebSocketHandler) handleGetMessages(session *Session, projectID string, data interface{}) {
    var req GetMessages
    if err := mapstructure.Decode(data, &req); err != nil {
        h.sendError(session, projectID, err)
        return
    }
    
    project, exists := h.projectMgr.GetProjectByID(projectID)
    if !exists {
        h.sendError(session, projectID, fmt.Errorf("project not found"))
        return
    }
    
    // Get messages from log
    messages, err := project.MessageLog.GetMessagesSince(req.Since)
    if err != nil {
        h.sendError(session, projectID, fmt.Errorf("failed to retrieve messages: %w", err))
        return
    }
    
    // Send messages
    msg := ServerMessage{
        Type:      "message_history",
        ProjectID: projectID,
        Data:      messages,
    }
    session.Conn.WriteJSON(msg)
}

// Handle request to start a new Claude session for a project
func (h *WebSocketHandler) handleAgentNewSession(session *Session, projectID string) {
    project, exists := h.projectMgr.GetProjectByID(projectID)
    if !exists {
        h.sendError(session, projectID, fmt.Errorf("project not found"))
        return
    }
    
    // Clear the session ID to force a new session on next execute
    project.mu.Lock()
    oldSessionID := project.SessionID
    project.SessionID = ""
    project.mu.Unlock()
    
    // Save cleared session ID to disk
    if err := h.projectMgr.UpdateProjectSession(project); err != nil {
        logError("failed to save cleared session: %v", err)
    }
    
    // Log the session reset
    sessionResetMsg := TimestampedMessage{
        Timestamp: time.Now(),
        Message: ClaudeMessage{
            Type: "system",
            Subtype: "session_reset",
            Message: json.RawMessage(fmt.Sprintf(`{"old_session_id": "%s", "reason": "user_requested"}`, oldSessionID)),
        },
        Direction: "server",
    }
    if err := project.MessageLog.Append(sessionResetMsg); err != nil {
        logError("failed to log session reset: %v", err)
    }
    
    // Broadcast the updated project state
    h.broadcastProjectState(project)
    
    // Send confirmation to the requesting client
    msg := ServerMessage{
        Type:      "update",
        ProjectID: projectID,
        Data: UpdateMessage{
            UpdateType: "session_reset",
            Data: map[string]interface{}{
                "old_session_id": oldSessionID,
                "new_session_id": "", // Empty until next execute
                "project_id":     projectID,
            },
        },
    }
    session.Conn.WriteJSON(msg)
}

// Handle agent kill request to terminate running Claude process
func (h *WebSocketHandler) handleAgentKill(session *Session, projectID string) {
    project, exists := h.projectMgr.GetProjectByID(projectID)
    if !exists {
        h.sendError(session, projectID, fmt.Errorf("project not found"))
        return
    }
    
    // Check if there's an active execution
    if project.State != EXECUTING {
        h.sendError(session, projectID, fmt.Errorf("no active execution to kill"))
        return
    }
    
    // Kill the Claude process
    if err := h.executor.KillExecution(projectID); err != nil {
        h.sendError(session, projectID, fmt.Errorf("failed to kill execution: %w", err))
        return
    }
    
    // Update project state
    project.mu.Lock()
    project.State = IDLE
    project.mu.Unlock()
    
    // Log the kill event
    killMsg := TimestampedMessage{
        Timestamp: time.Now(),
        Message: ClaudeMessage{
            Type: "system",
            Subtype: "execution_killed",
            Message: json.RawMessage(`{"reason": "user_requested"}`),
        },
        Direction: "server",
    }
    if err := project.MessageLog.Append(killMsg); err != nil {
        logError("failed to log kill event: %v", err)
    }
    
    // Broadcast updated state
    h.broadcastProjectState(project)
    
    // Send confirmation
    msg := ServerMessage{
        Type:      "update",
        ProjectID: projectID,
        Data: UpdateMessage{
            UpdateType: "execution_killed",
            Data: map[string]interface{}{
                "project_id": projectID,
                "state":      "IDLE",
            },
        },
    }
    session.Conn.WriteJSON(msg)
}

// Handle project deletion
func (h *WebSocketHandler) handleProjectDelete(session *Session, projectID string) {
    project, exists := h.projectMgr.GetProjectByID(projectID)
    if !exists {
        h.sendError(session, projectID, fmt.Errorf("project not found"))
        return
    }
    
    // Check if project is executing
    if project.State == EXECUTING {
        h.sendError(session, projectID, fmt.Errorf("cannot delete project while executing"))
        return
    }
    
    // Notify all subscribers before deletion
    deleteMsg := ServerMessage{
        Type:      "update",
        ProjectID: projectID,
        Data: UpdateMessage{
            UpdateType: "project_deleted",
            Data: map[string]interface{}{
                "project_id": projectID,
                "path":       project.Path,
            },
        },
    }
    h.broadcastToProject(project, deleteMsg)
    
    // Delete the project
    if err := h.projectMgr.DeleteProject(projectID); err != nil {
        h.sendError(session, projectID, fmt.Errorf("failed to delete project: %w", err))
        return
    }
    
    // Send confirmation
    msg := ServerMessage{
        Type: "project_deleted",
        Data: map[string]interface{}{
            "project_id": projectID,
            "success":    true,
        },
    }
    session.Conn.WriteJSON(msg)
}

// Broadcast updates to all project subscribers
func (h *WebSocketHandler) broadcastToProject(project *Project, msg ServerMessage) {
    project.mu.Lock()
    defer project.mu.Unlock()
    
    for _, session := range project.Subscribers {
        session.Conn.WriteJSON(msg)
    }
}

// Broadcast project state to subscribers
func (h *WebSocketHandler) broadcastProjectState(project *Project) {
    msg := ServerMessage{
        Type:      "update",
        ProjectID: project.ID,
        Data: UpdateMessage{
            UpdateType: "project_state",
            Data: map[string]interface{}{
                "state":      project.State,
                "session_id": project.SessionID,
                "path":       project.Path,
            },
        },
    }
    
    h.broadcastToProject(project, msg)
}
```

## MVP Implementation

### Main Server Structure

```go
type Server struct {
    wsHandler   *WebSocketHandler
    projectMgr  *ProjectManager
    executor    *ClaudeExecutor
    sessionMgr  *SessionManager
}

func NewServer(config Config) *Server {
    executor := &ClaudeExecutor{
        activeProcesses: make(map[string]*exec.Cmd),
    }
    
    projectMgr := NewProjectManager(config.DataDir)
    
    return &Server{
        wsHandler:   NewWebSocketHandler(projectMgr, executor),
        projectMgr:  projectMgr,
        executor:    executor,
        sessionMgr:  NewSessionManager(),
    }
}

func (s *Server) Start() error {
    // Start stats broadcaster
    go s.broadcastStats()
    
    // WebSocket server only - NO REST API
    http.HandleFunc("/ws", s.wsHandler.HandleUpgrade)
    
    // Start HTTPS server
    return http.ListenAndServeTLS(":8443", "cert.pem", "key.pem", nil)
}

// Broadcast server stats periodically
func (s *Server) broadcastStats() {
    ticker := time.NewTicker(10 * time.Second)
    defer ticker.Stop()
    
    for range ticker.C {
        stats := s.collectStats()
        msg := ServerMessage{
            Type: "update",
            Data: UpdateMessage{
                UpdateType: "stats",
                Data:       stats,
            },
        }
        s.sessionMgr.Broadcast(msg)
    }
}

type ServerStats struct {
    Projects      int            `json:"projects"`
    ActiveSessions int           `json:"active_sessions"`
    ProjectStates map[string]State `json:"project_states"`
    CPUPercent    float64       `json:"cpu_percent"`
    MemoryMB      int64         `json:"memory_mb"`
}
```

### Configuration

```go
type Config struct {
    Port           string
    CertFile       string
    KeyFile        string
    DataDir        string
}

func LoadConfig() Config {
    return Config{
        Port:     getEnv("PORT", "8443"),
        CertFile: getEnv("CERT_FILE", "cert.pem"),
        KeyFile:  getEnv("KEY_FILE", "key.pem"),
        DataDir:  getEnv("DATA_DIR", "./data"),
    }
}
```

### Error Handling

```go
// Project-aware error handling
func (h *WebSocketHandler) sendError(session *Session, projectID string, err error) {
    msg := ServerMessage{
        Type:      "error",
        ProjectID: projectID,
        Data: map[string]string{
            "error": err.Error(),
        },
    }
    session.Conn.WriteJSON(msg)
}
```

## MVP Deliverables

### Core Features
1. WebSocket server with TLS (no authentication)
2. Project-based Claude execution management
3. Sequential command execution per project
4. Session continuity across executions
5. Multi-project support with nesting validation
6. Real-time status updates via WebSocket
7. Full Claude CLI options support
8. Persistent project state (survives client disconnect)
9. Project subscription model (join/leave)
10. Message history retrieval
11. Selective broadcasting to project subscribers
12. New session creation via `agent_new_session` command
13. Server restart persistence - projects and session IDs survive server restarts

### Message Flow Example

```
// Initial connection and project creation
1. Client connects via WebSocket
2. Client: {"type": "project_list"}
3. Server: {"type": "project_list", "data": {"projects": []}}
4. Client: {"type": "project_create", "data": {"path": "/wip/project1"}}
5. Server: {"type": "update", "project_id": "proj_123", "data": {"update_type": "project_state", ...}}
6. Client: {"type": "project_join", "data": {"project_id": "proj_123"}}
7. Server: {"type": "update", "project_id": "proj_123", "data": {"update_type": "project_joined", "data": {...}}}
8. Client: {"type": "execute", "project_id": "proj_123", "data": {"prompt": "Create hello world"}}
9. Server: {"type": "update", "project_id": "proj_123", "data": {"update_type": "project_state", "data": {"state": "EXECUTING"}}}
10. Server: {"type": "agent_message", "project_id": "proj_123", "data": {"messages": [...], "session_id": "sess_abc"}}

// Client disconnects (Claude keeps running if executing)
11. WebSocket connection closed
12. Server: Project continues executing if in progress
13. Server: Messages are stored in project.Messages

// Client reconnects
14. Client connects via WebSocket
15. Client: {"type": "project_list"}
16. Server: {"type": "project_list", "data": {"projects": [
    {
        "id": "proj_123",
        "path": "/wip/project1",
        "state": "IDLE",
        "created_at": "2024-01-27T10:00:00Z",
        "last_active": "2024-01-27T10:05:00Z",
        "first_message_time": "2024-01-27T10:01:00Z",
        "last_message_time": "2024-01-27T10:05:00Z",
        "message_count": 4
    }
]}}
17. Client: {"type": "project_join", "data": {"project_id": "proj_123"}}
18. Server: {"type": "update", "project_id": "proj_123", "data": {"update_type": "project_joined", ...}}
19. Client: {"type": "get_messages", "project_id": "proj_123", "data": {"since": "2024-01-27T10:02:00Z"}}
20. Server: {"type": "message_history", "project_id": "proj_123", "data": [/* messages after timestamp */]}

// Multiple clients
21. Client2 connects and joins same project
22. Both clients receive updates when either executes commands

// Starting a new session
23. Client: {"type": "agent_new_session", "project_id": "proj_123"}
24. Server: {"type": "update", "project_id": "proj_123", "data": {"update_type": "session_reset", "data": {"old_session_id": "sess_abc", "new_session_id": "", "project_id": "proj_123"}}}
25. Server broadcasts to all subscribers: {"type": "update", "project_id": "proj_123", "data": {"update_type": "project_state", "data": {"state": "IDLE", "session_id": "", "path": "/wip/project1"}}}
26. Client: {"type": "execute", "project_id": "proj_123", "data": {"prompt": "Start fresh context"}}
27. Server: New Claude session created without -c flag, new session_id generated

// Server restart behavior
28. Server process stops (crash, restart, etc.)
29. Server starts up again
30. Server loads all projects from data/projects/*/metadata.json
31. Client connects via WebSocket
32. Client: {"type": "project_list"}
33. Server: {"type": "project_list", "data": {"projects": [/* all previously created projects with their session IDs intact */]}}
34. Client: {"type": "project_join", "data": {"project_id": "proj_123"}}
35. Client: {"type": "execute", "project_id": "proj_123", "data": {"prompt": "Continue where we left off"}}
36. Server: Uses stored session_id "sess_abc" with -c flag to continue conversation
```

### Not Included in MVP
- Process sandboxing
- Resource limiting (only monitoring)
- Complex error recovery
- Message persistence
- Multi-user access control

## Summary

This MVP architecture correctly handles Claude CLI's execution model with robust client-server patterns:

- **Project-Based**: All operations scoped to projects with path validation
- **Sequential**: One Claude execution at a time per project
- **Stateful**: Maintains session IDs for conversation continuity
- **Persistent**: Projects and messages survive client disconnections AND server restarts
- **Concurrent**: Multiple projects can execute in parallel
- **Real-time**: Updates streamed only to subscribed clients
- **Reconnectable**: Clients can reconnect and resume where they left off
- **Multi-Client**: Multiple clients can monitor the same project simultaneously
- **Session Control**: Users can start fresh Claude sessions with `agent_new_session`
- **Durable**: Project metadata and session IDs persist to disk for restart recovery

The architecture ensures that Claude processes continue running even when clients disconnect, with full message history available on reconnection. Projects and their Claude session IDs are persisted to disk, allowing the server to recover state after restarts. The subscription model (join/leave) ensures efficient broadcasting only to interested clients.

### File Storage Structure

```
data/
└── projects/
    ├── proj_123/
    │   ├── metadata.json                           # Project metadata with session ID
    │   └── logs/
    │       ├── messages_2024-01-27_10-00-00.jsonl  # First log file
    │       ├── messages_2024-01-27_14-30-45.jsonl  # Rotated after size limit
    │       ├── messages_2024-01-28_00-00-01.jsonl  # New day rotation
    │       ├── messages_2024-01-28_11-22-33.jsonl  # Size rotation
    │       └── latest.jsonl -> messages_2024-01-28_11-22-33.jsonl  # Symlink
    ├── proj_456/
    │   ├── metadata.json
    │   └── logs/
    │       ├── messages_2024-01-25_09-15-00.jsonl
    │       ├── messages_2024-01-26_00-00-01.jsonl
    │       └── latest.jsonl -> messages_2024-01-26_00-00-01.jsonl
    └── proj_789/
        ├── metadata.json
        └── logs/
            ├── messages_2024-01-27_16-45-00.jsonl
            └── latest.jsonl -> messages_2024-01-27_16-45-00.jsonl
```

#### Example metadata.json
```json
{
  "id": "proj_123",
  "path": "/wip/project1",
  "session_id": "sess_abc123",
  "created_at": "2024-01-27T10:00:00Z",
  "last_active": "2024-01-27T15:30:00Z"
}
```

### Log Rotation Strategy

1. **Time-based rotation**: New file each day at midnight
2. **Size-based rotation**: New file after 100MB or 10,000 messages
3. **Filename format**: `messages_YYYY-MM-DD_HH-MM-SS.jsonl`
4. **Latest symlink**: Always points to current active log file
5. **Efficient querying**: File timestamps in names allow skipping old files

This prevents any single log file from becoming too large while maintaining chronological organization and query efficiency.

## Implementation Requirements

### Critical Security Requirements

1. **Path Validation**
   - MUST validate all project paths to prevent command injection
   - MUST reject paths containing ".." or other traversal attempts
   - MUST ensure paths are absolute and within allowed directories
   - MUST sanitize paths before passing to exec.Command
   - MUST validate paths exist and are directories

2. **Process Security**
   - MUST NOT pass user input directly to shell commands
   - MUST use exec.Command with explicit argument arrays
   - MUST set appropriate process limits and timeouts
   - MUST run Claude processes with restricted permissions

### Process Management Requirements

1. **Claude CLI Execution**
   - MUST implement timeout mechanism (configurable, default 5 minutes)
   - MUST capture both stdout and stderr from Claude process
   - MUST handle Claude CLI not installed/not found errors
   - MUST implement process cancellation for `agent_kill` command
   - MUST clean up zombie processes
   - MUST limit concurrent executions per project (1) and globally (configurable)

2. **Process Lifecycle**
   - MUST track all running processes
   - MUST kill all child processes on server shutdown
   - MUST handle process cleanup on abnormal termination
   - MUST implement graceful shutdown with timeout

### Data Integrity Requirements

1. **File Operations**
   - MUST use atomic writes (write to temp file, then rename)
   - MUST handle concurrent access to message logs with proper locking
   - MUST validate JSON before writing to logs
   - MUST handle corrupted metadata files gracefully
   - MUST implement file size limits

2. **Persistence**
   - MUST save project metadata atomically
   - MUST handle disk space exhaustion gracefully
   - MUST validate loaded metadata before use
   - MUST handle missing/corrupted files during startup
   - MUST implement backup metadata writes

### Connection Management Requirements

1. **WebSocket Health**
   - MUST implement ping/pong heartbeat (30-second interval)
   - MUST detect and clean up stale connections
   - MUST limit connections per IP (configurable)
   - MUST implement connection timeout (configurable)
   - MUST validate WebSocket origin headers

2. **Session Management**
   - MUST clean up subscribers map on disconnect
   - MUST implement session timeout for idle connections
   - MUST limit message size (default 1MB)
   - MUST handle partial/fragmented messages
   - MUST implement backpressure for slow clients

### Error Handling Requirements

1. **Structured Errors**
   - MUST use consistent error codes across all responses
   - MUST include error context in responses
   - MUST log all errors with appropriate severity
   - MUST NOT leak internal paths or system info in errors
   - MUST handle panics gracefully

2. **Logging Requirements**
   - MUST implement structured logging with levels
   - MUST include correlation IDs for request tracking
   - MUST log all security-relevant events
   - MUST rotate logs to prevent disk exhaustion
   - MUST sanitize sensitive data from logs

### Operational Requirements

1. **Resource Limits**
   - MUST limit total active projects (default 100)
   - MUST limit message log size per project
   - MUST limit total disk usage
   - MUST limit memory usage per connection
   - MUST implement rate limiting for executions

2. **Monitoring**
   - MUST expose health check endpoint (not REST, via WebSocket)
   - MUST track execution times and success rates
   - MUST monitor disk usage and alert on thresholds
   - MUST track active connections and subscriptions
   - MUST log performance metrics

### Platform Requirements

1. **Cross-Platform Support**
   - MUST handle Windows path separators correctly
   - MUST work without symlinks on Windows (use latest.txt instead)
   - MUST handle different file locking semantics
   - MUST test on Linux, macOS, and Windows

2. **Configuration**
   - MUST validate all configuration on startup
   - MUST provide sensible defaults
   - MUST document all configuration options
   - MUST support environment variable overrides
   - MUST validate port numbers and file paths

### Implementation Checklist

The implementation MUST complete these items before deployment:

- [ ] Path validation and sanitization
- [ ] Process timeout mechanism
- [ ] Atomic file operations
- [ ] WebSocket ping/pong
- [ ] Connection cleanup
- [ ] Error standardization
- [ ] Structured logging
- [ ] Graceful shutdown
- [ ] Resource limits
- [ ] Platform testing

### Performance Requirements

1. **Response Times**
   - WebSocket message routing: < 10ms
   - Project list operations: < 100ms
   - Message history retrieval: < 500ms for 1000 messages
   - State persistence: < 50ms

2. **Scalability**
   - Support 100+ concurrent WebSocket connections
   - Support 50+ active projects
   - Handle 10+ executions per minute
   - Message throughput: 1000+ messages/second

### Testing Requirements

1. **Unit Tests**
   - MUST test path validation edge cases
   - MUST test concurrent operations
   - MUST test error conditions
   - MUST test resource limits

2. **Integration Tests**
   - MUST test server restart recovery
   - MUST test client reconnection scenarios
   - MUST test process timeout and cancellation
   - MUST test multi-client synchronization

## Future Work: Security

**⚠️ NOTE: This section describes future security features that are NOT part of the MVP implementation.**

### SSH Authentication (Future)

When security is implemented in a future version, the server will support SSH key-based authentication:

```go
type SSHAuthenticator struct {
    authorizedKeys map[string]ssh.PublicKey
}

func (a *SSHAuthenticator) Authenticate(pubKeyData []byte, signature []byte, challenge []byte) bool {
    pubKey, err := ssh.ParsePublicKey(pubKeyData)
    if err != nil {
        return false
    }
    
    // Verify signature
    err = pubKey.Verify(challenge, signature)
    return err == nil
}

// WebSocket upgrade with auth
func (h *WebSocketHandler) HandleUpgrade(w http.ResponseWriter, r *http.Request) {
    // Get auth headers
    pubKey := r.Header.Get("X-SSH-PublicKey")
    signature := r.Header.Get("X-SSH-Signature")
    challenge := r.Header.Get("X-SSH-Challenge")
    
    if !h.auth.Authenticate([]byte(pubKey), []byte(signature), []byte(challenge)) {
        http.Error(w, "unauthorized", http.StatusUnauthorized)
        return
    }
    
    // ... continue with WebSocket upgrade
}
```

### Session Security (Future)

Future versions will implement project-level access control:

```go
type Session struct {
    ID         string
    Conn       *websocket.Conn
    CreatedAt  time.Time
}
```

### Why Security is Deferred

1. **MVP Focus**: Get core functionality working first
2. **Development Speed**: Easier testing without auth during development
3. **Local Development**: Initially targeting local/trusted environments
4. **Iterative Approach**: Security can be layered on after validating core design

### Security Implementation Plan (Future)

When implementing security in the future:

1. **Phase 1**: Basic SSH key authentication
2. **Phase 2**: Project-level access control
3. **Phase 3**: Rate limiting and DOS protection
4. **Phase 4**: Audit logging and monitoring
5. **Phase 5**: Multi-user support with isolation