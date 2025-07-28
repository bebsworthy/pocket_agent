# Contributing to Pocket Agent Server

Thank you for your interest in contributing to Pocket Agent Server! This guide will help you get started with contributing to the project.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Setup](#development-setup)
4. [Making Changes](#making-changes)
5. [Code Style](#code-style)
6. [Testing](#testing)
7. [Submitting Changes](#submitting-changes)
8. [Review Process](#review-process)

## Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct:

- Be respectful and inclusive
- Welcome newcomers and help them get started
- Focus on constructive criticism
- Respect differing viewpoints and experiences
- Show empathy towards other community members

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/pocket_agent.git
   cd pocket_agent/server
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/boyd/pocket_agent.git
   ```

## Development Setup

### Prerequisites

- Go 1.21 or later
- Make
- Docker (optional, for containerized testing)
- Pre-commit (for git hooks)

### Initial Setup

1. **Install Go dependencies**:
   ```bash
   make deps
   ```

2. **Install development tools**:
   ```bash
   make install-tools
   ```

3. **Install pre-commit hooks**:
   ```bash
   make pre-commit-install
   ```

4. **Verify setup**:
   ```bash
   make check
   ```

## Making Changes

### Workflow

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Write clear, concise commit messages
   - Keep commits focused and atomic
   - Update tests for any new functionality
   - Update documentation as needed

3. **Run tests and checks**:
   ```bash
   make check
   ```

4. **Commit your changes**:
   ```bash
   git add .
   git commit -m "feat: add new feature"
   ```
   
   Commit message format:
   - `feat:` New feature
   - `fix:` Bug fix
   - `docs:` Documentation changes
   - `test:` Test additions or modifications
   - `refactor:` Code refactoring
   - `perf:` Performance improvements
   - `chore:` Maintenance tasks

### Branch Naming

Use descriptive branch names:
- `feature/websocket-compression`
- `fix/memory-leak-executor`
- `docs/api-examples`
- `refactor/project-manager`

## Code Style

### Go Code Style

We follow standard Go conventions with some additional guidelines:

1. **Format your code**:
   ```bash
   make fmt
   ```

2. **Run linters**:
   ```bash
   make lint
   ```

3. **Key guidelines**:
   - Use meaningful variable and function names
   - Keep functions small and focused
   - Add comments for exported types and functions
   - Handle errors explicitly
   - Use table-driven tests
   - Avoid global variables

### Example Code Style

```go
// Package project provides project management functionality.
package project

import (
    "errors"
    "fmt"
)

// ErrProjectNotFound is returned when a project cannot be found.
var ErrProjectNotFound = errors.New("project not found")

// Manager handles project lifecycle operations.
type Manager struct {
    dataDir string
    mu      sync.RWMutex
    projects map[string]*Project
}

// NewManager creates a new project manager.
func NewManager(dataDir string) (*Manager, error) {
    if dataDir == "" {
        return nil, errors.New("data directory cannot be empty")
    }
    
    return &Manager{
        dataDir: dataDir,
        projects: make(map[string]*Project),
    }, nil
}

// GetProject retrieves a project by ID.
func (m *Manager) GetProject(id string) (*Project, error) {
    m.mu.RLock()
    defer m.mu.RUnlock()
    
    project, exists := m.projects[id]
    if !exists {
        return nil, fmt.Errorf("%w: %s", ErrProjectNotFound, id)
    }
    
    return project, nil
}
```

## Testing

### Writing Tests

1. **Unit tests** go next to the code they test:
   ```
   internal/project/manager.go
   internal/project/manager_test.go
   ```

2. **Use table-driven tests**:
   ```go
   func TestManager_GetProject(t *testing.T) {
       tests := []struct {
           name    string
           id      string
           want    *Project
           wantErr error
       }{
           {
               name:    "existing project",
               id:      "test-id",
               want:    &Project{ID: "test-id"},
               wantErr: nil,
           },
           {
               name:    "non-existent project",
               id:      "missing",
               want:    nil,
               wantErr: ErrProjectNotFound,
           },
       }
       
       for _, tt := range tests {
           t.Run(tt.name, func(t *testing.T) {
               // test implementation
           })
       }
   }
   ```

3. **Run tests**:
   ```bash
   make test
   make test-integration
   make coverage
   ```

### Test Coverage

- Aim for >80% test coverage
- Focus on testing business logic
- Test error paths and edge cases
- Use mocks for external dependencies

## Submitting Changes

### Before Submitting

1. **Update your branch**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run all checks**:
   ```bash
   make check
   ```

3. **Update documentation**:
   - Update README if needed
   - Add/update API documentation
   - Include examples for new features

### Pull Request Process

1. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create a Pull Request** on GitHub:
   - Use a clear, descriptive title
   - Reference any related issues
   - Describe what changes you made and why
   - Include screenshots for UI changes
   - List any breaking changes

3. **PR Description Template**:
   ```markdown
   ## Description
   Brief description of changes
   
   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Breaking change
   - [ ] Documentation update
   
   ## Testing
   - [ ] Unit tests pass
   - [ ] Integration tests pass
   - [ ] Manual testing completed
   
   ## Checklist
   - [ ] Code follows project style guidelines
   - [ ] Self-review completed
   - [ ] Documentation updated
   - [ ] No new linting warnings
   ```

## Review Process

### What to Expect

1. **Automated checks** will run on your PR
2. **Code review** by maintainers
3. **Feedback and discussion**
4. **Approval and merge**

### Review Timeline

- Initial review: 1-3 business days
- Follow-up reviews: 1-2 business days
- Emergency fixes: ASAP

### Common Review Feedback

- Missing tests
- Documentation needs update
- Code style inconsistencies
- Performance concerns
- Security issues

## Getting Help

### Resources

- [Project README](README.md)
- [API Documentation](docs/api.md)
- [Architecture Documentation](docs/architecture/)
- [Go Documentation](https://golang.org/doc/)

### Communication

- Open an issue for bugs or feature requests
- Use discussions for questions
- Tag maintainers for urgent issues

## Recognition

Contributors are recognized in:
- Release notes
- Contributors file
- Project statistics

Thank you for contributing to Pocket Agent Server!

---

By contributing to this project, you agree that your contributions will be licensed under the project's MIT License.