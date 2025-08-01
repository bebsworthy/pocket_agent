# Implementation Tasks: Application Base (Revised)

## Task Optimization Notice
This task list has been optimized with specialized agents for higher quality implementation. Each track now has designated specialists and consultants based on the technologies and requirements. Code reviews and product reviews are integrated after each development phase.

## Review and Rework Process
1. After each track's development tasks, a **Code Review (CR)** is performed by the typescript-react-code-reviewer
2. Following the code review, a **Product Review (PR)** is performed by the product-owner-reviewer
3. If either review fails (status: "Requires changes"), a **Rework (RW)** task is triggered
4. The typescript-react-developer addresses all findings from the failed reviews
5. After rework, the review process repeats until approval is achieved
6. Only after both CR and PR are approved can dependent tracks proceed

## Available Specialized Agents
- **typescript-react-developer**: React/TypeScript development specialist (ensures latest versions)
- **typescript-react-code-reviewer**: Specialized code reviewer for React/TypeScript/Vite mobile apps
- **product-owner-reviewer**: Product owner specializing in specification compliance validation
- **frontend-architect**: Architecture and design patterns expert
- **frontend-test-engineer**: Testing frameworks specialist
- **ui-accessibility-specialist**: Accessibility and mobile-first design expert
- **go-websocket-specialist**: WebSocket expertise (consultation)

## Parallel Execution Tracks

### Track A: Foundation & Setup (No Dependencies)
> Primary Agent: typescript-react-developer
> Consultants: frontend-architect (for build optimization)

- [ ] 1. **Initialize React SPA with Vite**
  - Create `frontend-spa/` directory
  - Initialize Vite project with React and TypeScript (latest versions)
  - Configure `tsconfig.json` with strict mode
  - Set up `vite.config.ts` with build optimizations
  - Create mobile-optimized `index.html` with viewport meta tags
  - Files: `frontend-spa/package.json`, `tsconfig.json`, `vite.config.ts`, `index.html`
  - _Requirements: 1.1, 1.2, 1.3, 1.5_
  - _Agent: typescript-react-developer_

- [ ] 2. **Set up TailwindCSS and styling infrastructure**
  - Install and configure TailwindCSS (latest version)
  - Create `tailwind.config.js` with mobile-first design system
  - Set up `src/styles/globals.css` with Tailwind imports
  - Create `src/styles/themes.ts` with light/dark theme constants
  - Configure PostCSS for Tailwind
  - Files: `tailwind.config.js`, `postcss.config.js`, `src/styles/globals.css`, `src/styles/themes.ts`
  - _Requirements: 1.4, 11.1, 11.4_
  - _Agent: typescript-react-developer_

- [ ] 3. **Set up linting and type checking**
  - Install ESLint with TypeScript plugin (latest versions)
  - Configure ESLint for React/TypeScript best practices
  - Set up Prettier for code formatting
  - Configure pre-commit hooks with Husky
  - Add npm scripts: `lint`, `lint:fix`, `typecheck`
  - Ensure VS Code integration works
  - Files: `.eslintrc.json`, `.prettierrc`, `.husky/pre-commit`, `package.json` scripts
  - _Requirements: Code quality and consistency_
  - _Agent: typescript-react-developer_

- [ ] 4. **Copy TypeScript message types from test-client**
  - Copy message type definitions from `test-client/src/types/messages.ts`
  - Create `src/types/messages.ts` with all message interfaces
  - Create `src/types/models.ts` with Project and Server interfaces
  - Create `src/types/index.ts` as barrel export
  - Files: `src/types/messages.ts`, `src/types/models.ts`, `src/types/index.ts`
  - _Requirements: 5.3_
  - _Agent: typescript-react-developer_

- [ ] CR-A. **Code Review: Foundation Setup**
  - Review project configuration and build setup
  - Verify all libraries use latest stable versions
  - Check linting and TypeScript configuration
  - Validate type definitions match server protocol
  - Ensure mobile viewport configuration is correct
  - _Dependencies: Tasks 1-4_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-A. **Product Review: Track A Foundation**
  - Validate project setup meets all requirements in sections 1.x
  - Verify TypeScript strict mode configuration
  - Check mobile viewport and meta tags implementation
  - Ensure all type definitions match server protocol specs
  - Validate linting and formatting setup
  - Review output saved to: `product_review/track-a.md`
  - _Spec References: requirements.md sections 1.x, 5.3; design.md Project Setup_
  - _Dependencies: CR-A_
  - _Agent: product-owner-reviewer_

- [ ] RW-A. **Rework: Address Track A Review Findings**
  - Review findings from `code_review/CR-A.md` and/or `product_review/track-a.md`
  - Address all critical issues identified in reviews
  - Implement required changes and improvements
  - Re-run linting and type checking
  - Update documentation if needed
  - _Trigger: Only if CR-A or PR-A status is "Requires changes"_
  - _Dependencies: CR-A and/or PR-A (failed)_
  - _Agent: typescript-react-developer_

### Track B: Component Library - Atoms (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: typescript-react-developer
> Consultants: ui-accessibility-specialist (for touch targets and ARIA)

- [ ] 5. **Implement Button atom component**
  - Create `src/components/ui/atoms/Button.tsx` with mobile-optimized design
  - Implement variants (primary, secondary, ghost)
  - Ensure minimum 44px touch targets
  - Add loading and disabled states
  - Create barrel export in `src/components/ui/atoms/index.ts`
  - Files: `src/components/ui/atoms/Button.tsx`, `src/components/ui/atoms/index.ts`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] 6. **Implement Input and IconButton atoms**
  - Create `src/components/ui/atoms/Input.tsx` with mobile keyboard support
  - Create `src/components/ui/atoms/IconButton.tsx` with touch optimization
  - Add proper ARIA labels and inputMode attributes
  - Update barrel export
  - Files: `src/components/ui/atoms/Input.tsx`, `src/components/ui/atoms/IconButton.tsx`
  - _Requirements: 2.1, 2.3, 2.4, 2.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] CR-B. **Code Review: Atom Components**
  - Review component API consistency
  - Validate 44px touch targets
  - Check ARIA implementation
  - Ensure TypeScript types are strict
  - Test keyboard navigation
  - _Dependencies: Tasks 5-6_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-B. **Product Review: Track B Atoms**
  - Validate all atom components meet requirements 2.x
  - Verify 44px minimum touch targets
  - Check mobile-first design implementation
  - Ensure accessibility compliance (ARIA labels)
  - Validate component API consistency
  - Review output saved to: `product_review/track-b.md`
  - _Spec References: requirements.md sections 2.x; design.md Component Architecture_
  - _Dependencies: CR-B_
  - _Agent: product-owner-reviewer_

- [ ] RW-B. **Rework: Address Track B Review Findings**
  - Review findings from `code_review/CR-B.md` and/or `product_review/track-b.md`
  - Fix all touch target issues
  - Correct accessibility implementations
  - Address TypeScript type issues
  - Update component APIs as needed
  - _Trigger: Only if CR-B or PR-B status is "Requires changes"_
  - _Dependencies: CR-B and/or PR-B (failed)_
  - _Agent: typescript-react-developer_

### Track C: Component Library - Molecules (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: typescript-react-developer
> Consultants: ui-accessibility-specialist (for mobile interactions)

- [ ] 7. **Implement Card and StatusIndicator molecules**
  - Create `src/components/ui/molecules/Card.tsx` with press states
  - Create `src/components/ui/molecules/StatusIndicator.tsx` for connection status
  - Implement touch-friendly interactions
  - Create barrel export in `src/components/ui/molecules/index.ts`
  - Files: `src/components/ui/molecules/Card.tsx`, `src/components/ui/molecules/StatusIndicator.tsx`, `src/components/ui/molecules/index.ts`
  - _Requirements: 2.1, 2.2, 2.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] 8. **Implement SegmentedControl molecule**
  - Create `src/components/ui/molecules/SegmentedControl.tsx` for tab navigation
  - Implement generic type support for tab values
  - Add mobile-optimized styling and animations
  - Update barrel export
  - Files: `src/components/ui/molecules/SegmentedControl.tsx`
  - _Requirements: 2.1, 2.2, 2.6, 7.2_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] CR-C. **Code Review: Molecule Components**
  - Review component composition patterns
  - Validate mobile interactions
  - Check animation performance
  - Ensure proper TypeScript generics
  - _Dependencies: Tasks 7-8_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-C. **Product Review: Track C Molecules**
  - Validate molecule components meet requirements 2.x and 7.2
  - Verify SegmentedControl matches mockup navigation
  - Check Card component press states
  - Ensure StatusIndicator shows connection states correctly
  - Validate component composition patterns
  - Review output saved to: `product_review/track-c.md`
  - _Spec References: requirements.md sections 2.x, 7.2; mockups/mobile-mockup.png_
  - _Dependencies: CR-C_
  - _Agent: product-owner-reviewer_

- [ ] RW-C. **Rework: Address Track C Review Findings**
  - Review findings from `code_review/CR-C.md` and/or `product_review/track-c.md`
  - Fix component composition issues
  - Optimize mobile interactions
  - Improve animation performance
  - Correct TypeScript generic implementations
  - _Trigger: Only if CR-C or PR-C status is "Requires changes"_
  - _Dependencies: CR-C and/or PR-C (failed)_
  - _Agent: typescript-react-developer_

### Track D: State Management & Services (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: typescript-react-developer
> Consultants: frontend-architect (for state architecture)

- [ ] 9. **Set up Jotai state management**
  - Install Jotai and jotai/utils (latest versions)
  - Create `src/store/atoms/projects.ts` with project atoms
  - Create `src/store/atoms/servers.ts` with server atoms
  - Create `src/store/atoms/ui.ts` with theme and loading atoms
  - Create `src/store/atoms/websocket.ts` for WebSocket state
  - Files: `src/store/atoms/*.ts`
  - _Requirements: 4.1, 4.2, 4.5_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] 10. **Implement custom state hooks**
  - Create `src/store/hooks/useProjects.ts` with project management logic
  - Create `src/store/hooks/useServers.ts` with server management logic
  - Create `src/store/hooks/useWebSocket.ts` for WebSocket integration
  - Implement localStorage persistence with atomWithStorage
  - Files: `src/store/hooks/*.ts`
  - _Requirements: 4.1, 4.5, 8.1, 8.2_
  - _Dependencies: Task 9_
  - _Agent: typescript-react-developer_

- [ ] CR-D. **Code Review: State Management**
  - Review Jotai atom structure
  - Validate state update patterns
  - Check localStorage implementation
  - Ensure proper TypeScript types for atoms
  - _Dependencies: Tasks 9-10_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-D. **Product Review: Track D State Management**
  - Validate Jotai implementation meets requirements 4.x
  - Verify state persistence with localStorage
  - Check project and server state management
  - Ensure WebSocket state integration
  - Validate theme state management
  - Review output saved to: `product_review/track-d.md`
  - _Spec References: requirements.md sections 4.x, 8.x; design.md State Management_
  - _Dependencies: CR-D_
  - _Agent: product-owner-reviewer_

- [ ] RW-D. **Rework: Address Track D Review Findings**
  - Review findings from `code_review/CR-D.md` and/or `product_review/track-d.md`
  - Fix Jotai atom structure issues
  - Correct state update patterns
  - Improve localStorage implementation
  - Address TypeScript type issues for atoms
  - _Trigger: Only if CR-D or PR-D status is "Requires changes"_
  - _Dependencies: CR-D and/or PR-D (failed)_
  - _Agent: typescript-react-developer_

### Track E: Storage & Utils (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: typescript-react-developer

- [ ] 11. **Implement localStorage service**
  - Create `src/services/storage/LocalStorageService.ts`
  - Create `src/services/storage/hooks.ts` for React integration
  - Implement project and server persistence
  - Add error handling for storage quota
  - Files: `src/services/storage/*.ts`
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] 12. **Create utility functions**
  - Create `src/utils/constants.ts` with app constants
  - Create `src/utils/helpers.ts` with helper functions
  - Create `src/utils/cn.ts` for className merging (clsx + tailwind-merge)
  - Create `src/utils/sanitize.ts` for input sanitization
  - Files: `src/utils/*.ts`
  - _Requirements: Security considerations_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_

- [ ] CR-E. **Code Review: Storage and Utilities**
  - Review storage error handling
  - Validate input sanitization
  - Check utility function implementations
  - Ensure no sensitive data in localStorage
  - _Dependencies: Tasks 11-12_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-E. **Product Review: Track E Storage & Utils**
  - Validate localStorage implementation meets requirements 8.x
  - Verify storage quota error handling
  - Check project/server data persistence
  - Ensure input sanitization for security
  - Validate utility functions implementation
  - Review output saved to: `product_review/track-e.md`
  - _Spec References: requirements.md sections 8.x, 10.x; design.md Storage Layer_
  - _Dependencies: CR-E_
  - _Agent: product-owner-reviewer_

- [ ] RW-E. **Rework: Address Track E Review Findings**
  - Review findings from `code_review/CR-E.md` and/or `product_review/track-e.md`
  - Fix storage error handling
  - Improve input sanitization
  - Correct utility function implementations
  - Ensure no sensitive data in localStorage
  - _Trigger: Only if CR-E or PR-E status is "Requires changes"_
  - _Dependencies: CR-E and/or PR-E (failed)_
  - _Agent: typescript-react-developer_

### Checkpoint Review 1
- [ ] CR1. **Comprehensive Review: Foundation and Components**
  - Review overall project structure
  - Validate component library consistency
  - Check accessibility compliance
  - Verify atomic design principles
  - Ensure all linting passes
  - _Dependencies: CR-A through CR-E_
  - _Agent: typescript-react-code-reviewer_

### Track F: WebSocket Service (Dependencies: CR1)
> Primary Agent: typescript-react-developer
> Consultants: go-websocket-specialist (for protocol guidance)

- [ ] 13. **Implement WebSocket service core**
  - Create `src/services/websocket/WebSocketService.ts` with EventEmitter
  - Implement connection management with native WebSocket API
  - Add automatic reconnection with exponential backoff
  - Implement project join/leave tracking
  - Support both WS and WSS protocols
  - Files: `src/services/websocket/WebSocketService.ts`
  - _Requirements: 5.1, 5.2, 5.4, 5.5, 5.7_
  - _Dependencies: CR1_
  - _Agent: typescript-react-developer_

- [ ] 14. **Create WebSocket React integration**
  - Create `src/services/websocket/hooks.ts` with useWebSocket hook
  - Create `src/services/websocket/WebSocketContext.tsx` for provider
  - Implement message type handlers
  - Add connection state management
  - Files: `src/services/websocket/hooks.ts`, `src/services/websocket/WebSocketContext.tsx`
  - _Requirements: 5.3, 5.6_
  - _Dependencies: Tasks 10, 13_
  - _Agent: typescript-react-developer_

- [ ] CR-F. **Code Review: WebSocket Service**
  - Review reconnection logic
  - Validate message handling
  - Check error scenarios
  - Ensure proper cleanup on unmount
  - _Dependencies: Tasks 13-14_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-F. **Product Review: Track F WebSocket**
  - Validate WebSocket service meets requirements 5.x
  - Verify automatic reconnection with exponential backoff
  - Check WS and WSS protocol support
  - Ensure project join/leave tracking
  - Validate message type handling
  - Review output saved to: `product_review/track-f.md`
  - _Spec References: requirements.md sections 5.x; design.md WebSocket Service_
  - _Dependencies: CR-F_
  - _Agent: product-owner-reviewer_

- [ ] RW-F. **Rework: Address Track F Review Findings**
  - Review findings from `code_review/CR-F.md` and/or `product_review/track-f.md`
  - Fix reconnection logic issues
  - Improve message handling
  - Address error scenarios
  - Ensure proper cleanup on unmount
  - _Trigger: Only if CR-F or PR-F status is "Requires changes"_
  - _Dependencies: CR-F and/or PR-F (failed)_
  - _Agent: typescript-react-developer_

### Track G: Routing & App Shell (Dependencies: CR1)
> Primary Agent: typescript-react-developer
> Consultants: frontend-architect (for app architecture)

- [ ] 15. **Set up React Router and app structure**
  - Install react-router-dom (latest version)
  - Create `src/Router.tsx` with route definitions
  - Create `src/App.tsx` with theme provider and error boundary
  - Create `src/main.tsx` as entry point
  - Implement lazy loading for routes
  - Files: `src/Router.tsx`, `src/App.tsx`, `src/main.tsx`
  - _Requirements: 3.1, 3.2, 3.4, 3.5_
  - _Dependencies: CR1_
  - _Agent: typescript-react-developer_

- [ ] 16. **Implement error boundary and theme system**
  - Create `src/components/ErrorBoundary.tsx` for error handling
  - Implement theme switching logic in App.tsx
  - Create `src/components/LoadingScreen.tsx`
  - Set up dark mode class toggling
  - Files: `src/components/ErrorBoundary.tsx`, `src/components/LoadingScreen.tsx`
  - _Requirements: 10.5, 11.2, 11.3, 11.5_
  - _Dependencies: Tasks 10, 15_
  - _Agent: typescript-react-developer_

- [ ] CR-G. **Code Review: Routing and App Shell**
  - Review route structure
  - Validate error boundary implementation
  - Check theme switching logic
  - Ensure proper lazy loading
  - _Dependencies: Tasks 15-16_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-G. **Product Review: Track G Routing & Shell**
  - Validate routing meets requirements 3.x
  - Verify error boundary implementation
  - Check theme switching functionality
  - Ensure lazy loading for routes
  - Validate loading screen implementation
  - Review output saved to: `product_review/track-g.md`
  - _Spec References: requirements.md sections 3.x, 11.x; design.md Application Shell_
  - _Dependencies: CR-G_
  - _Agent: product-owner-reviewer_

- [ ] RW-G. **Rework: Address Track G Review Findings**
  - Review findings from `code_review/CR-G.md` and/or `product_review/track-g.md`
  - Fix route structure issues
  - Improve error boundary implementation
  - Correct theme switching logic
  - Ensure proper lazy loading
  - _Trigger: Only if CR-G or PR-G status is "Requires changes"_
  - _Dependencies: CR-G and/or PR-G (failed)_
  - _Agent: typescript-react-developer_

### Track H: Component Library - Organisms (Dependencies: CR-B, CR-C)
> Primary Agent: typescript-react-developer
> Consultants: ui-accessibility-specialist (for complex components)

- [ ] 17. **Implement organism components**
  - Create `src/components/ui/organisms/ProjectCard.tsx`
  - Create `src/components/ui/organisms/ServerForm.tsx`
  - Create `src/components/ui/organisms/EmptyState.tsx`
  - Create barrel export
  - Integrate with molecules and atoms
  - Files: `src/components/ui/organisms/*.tsx`
  - _Requirements: 2.1, 6.2, 6.7_
  - _Dependencies: CR-B, CR-C_
  - _Agent: typescript-react-developer_

- [ ] CR-H. **Code Review: Organism Components**
  - Review component integration
  - Validate form accessibility
  - Check empty state patterns
  - Ensure proper composition
  - _Dependencies: Task 17_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-H. **Product Review: Track H Organisms**
  - Validate organism components meet requirements 2.1, 6.x
  - Verify ProjectCard matches mockup design
  - Check ServerForm functionality and validation
  - Ensure EmptyState component implementation
  - Validate atomic design composition
  - Review output saved to: `product_review/track-h.md`
  - _Spec References: requirements.md sections 2.1, 6.x; mockups/mobile-mockup.png_
  - _Dependencies: CR-H_
  - _Agent: product-owner-reviewer_

- [ ] RW-H. **Rework: Address Track H Review Findings**
  - Review findings from `code_review/CR-H.md` and/or `product_review/track-h.md`
  - Fix component integration issues
  - Improve form accessibility
  - Correct empty state patterns
  - Ensure proper composition
  - _Trigger: Only if CR-H or PR-H status is "Requires changes"_
  - _Dependencies: CR-H and/or PR-H (failed)_
  - _Agent: typescript-react-developer_

### Checkpoint Review 2
- [ ] CR2. **Comprehensive Review: Services and Architecture**
  - Review WebSocket implementation
  - Validate state management patterns
  - Check routing architecture
  - Verify error handling throughout
  - Ensure TypeScript strict compliance
  - _Dependencies: CR-F, CR-G, CR-H_
  - _Agent: typescript-react-code-reviewer_

### Track I: Feature Screens (Dependencies: CR2)
> Primary Agent: typescript-react-developer
> Consultants: ui-accessibility-specialist (for screen layouts)

- [ ] 18. **Implement Dashboard screen**
  - Create `src/components/features/dashboard/Dashboard.tsx`
  - Create `src/components/features/dashboard/AddProjectModal.tsx`
  - Implement project list display with empty state
  - Add project creation flow
  - Integrate with state management
  - Files: `src/components/features/dashboard/*.tsx`
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  - _Dependencies: CR2_
  - _Agent: typescript-react-developer_

- [ ] 19. **Implement Project Detail screen**
  - Create `src/components/features/project/ProjectDetail.tsx`
  - Create `src/components/features/project/ProjectTabs.tsx`
  - Implement segmented control navigation
  - Add connection status display
  - Create placeholder content for tabs
  - Files: `src/components/features/project/*.tsx`
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - _Dependencies: CR2_
  - _Agent: typescript-react-developer_

- [ ] CR-I. **Code Review: Feature Screens**
  - Review screen implementations
  - Validate navigation flows
  - Check state integration
  - Ensure mobile responsiveness
  - _Dependencies: Tasks 18-19_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-I. **Product Review: Track I Feature Screens**
  - Validate Dashboard meets all requirements 6.x
  - Verify Project Detail meets requirements 7.x
  - Check navigation flows match mockups
  - Ensure state integration works correctly
  - Validate mobile responsiveness
  - Review output saved to: `product_review/track-i.md`
  - _Spec References: requirements.md sections 6.x, 7.x; mockups/mobile-mockup.png_
  - _Dependencies: CR-I_
  - _Agent: product-owner-reviewer_

- [ ] RW-I. **Rework: Address Track I Review Findings**
  - Review findings from `code_review/CR-I.md` and/or `product_review/track-i.md`
  - Fix screen implementation issues
  - Correct navigation flows
  - Improve state integration
  - Ensure mobile responsiveness
  - _Trigger: Only if CR-I or PR-I status is "Requires changes"_
  - _Dependencies: CR-I and/or PR-I (failed)_
  - _Agent: typescript-react-developer_

### Track J: Testing Infrastructure (Dependencies: CR2)
> Primary Agent: frontend-test-engineer

- [ ] 20. **Set up testing framework**
  - Configure Vitest for unit testing
  - Install React Testing Library (latest versions)
  - Create example component tests
  - Create example service tests
  - Set up jest-websocket-mock
  - Add test scripts to package.json
  - Files: `vitest.config.ts`, `src/__tests__/setup.ts`
  - _Requirements: Testing strategy_
  - _Dependencies: CR2_
  - _Agent: frontend-test-engineer_

- [ ] 21. **Write critical component tests**
  - Test Button component for touch targets
  - Test WebSocket service connection/reconnection
  - Test state management hooks
  - Test error boundary behavior
  - Achieve minimum 80% coverage on critical paths
  - Files: `src/__tests__/components/*.test.tsx`, `src/__tests__/services/*.test.ts`
  - _Requirements: 2.4, 5.4, Testing strategy_
  - _Dependencies: Task 20_
  - _Agent: frontend-test-engineer_

- [ ] CR-J. **Code Review: Testing**
  - Review test coverage
  - Validate test patterns
  - Check mock implementations
  - Ensure tests are maintainable
  - _Dependencies: Tasks 20-21_
  - _Agent: typescript-react-code-reviewer_

- [ ] PR-J. **Product Review: Track J Testing**
  - Validate testing setup meets quality standards
  - Verify 80% coverage on critical paths
  - Check component test implementation
  - Ensure WebSocket service tests
  - Validate test patterns and maintainability
  - Review output saved to: `product_review/track-j.md`
  - _Spec References: requirements.md Testing Strategy; design.md Quality Standards_
  - _Dependencies: CR-J_
  - _Agent: product-owner-reviewer_

- [ ] RW-J. **Rework: Address Track J Review Findings**
  - Review findings from `code_review/CR-J.md` and/or `product_review/track-j.md`
  - Improve test coverage
  - Fix test patterns
  - Correct mock implementations
  - Ensure tests are maintainable
  - _Trigger: Only if CR-J or PR-J status is "Requires changes"_
  - _Dependencies: CR-J and/or PR-J (failed)_
  - _Agent: typescript-react-developer_

### Final Review Track
- [ ] CR3. **Final Comprehensive Review**
  - Review entire feature implementation
  - Validate mobile-first design principles
  - Check accessibility compliance (WCAG 2.1 AA)
  - Verify bundle size optimization (<500KB)
  - Security audit for XSS and input handling
  - Performance review for mobile devices
  - Ensure all linting and tests pass
  - Verify latest library versions are used
  - _Dependencies: All implementation tracks and code reviews_
  - _Agent: typescript-react-code-reviewer_

- [ ] RW-Final. **Final Rework: Address Comprehensive Review Findings**
  - Review findings from `code_review/CR3.md`
  - Address any remaining critical issues
  - Optimize bundle size if needed
  - Fix any security vulnerabilities
  - Improve performance bottlenecks
  - Update dependencies if outdated
  - _Trigger: Only if CR3 status is "Requires changes"_
  - _Dependencies: CR3 (failed)_
  - _Agent: typescript-react-developer_

## Execution Strategy

### Parallel Groups

1. **Group 1 (Immediate Start)**:
   - Track A: Foundation (Tasks 1-4)
   
2. **Group 2 (After CR-A)**:
   - Track B: Atoms (Tasks 5-6)
   - Track C: Molecules (Tasks 7-8)
   - Track D: State Management (Tasks 9-10)
   - Track E: Storage & Utils (Tasks 11-12)
   
3. **Group 3 (After CR1)**:
   - Track F: WebSocket Service (Tasks 13-14)
   - Track G: Routing (Tasks 15-16)
   - Track H: Organisms (Task 17)
   
4. **Group 4 (After CR2)**:
   - Track I: Screens (Tasks 18-19)
   - Track J: Testing (Tasks 20-21)
   
5. **Group 5 (After all tracks)**:
   - Final Review (CR3)

### Agent Utilization
- **Primary Development Agent**: typescript-react-developer (19 tasks + up to 10 conditional rework tasks)
- **Testing Agent**: frontend-test-engineer (2 tasks)
- **Code Review Agent**: typescript-react-code-reviewer (13 review tasks)
- **Product Review Agent**: product-owner-reviewer (10 review tasks)
- **Architecture Consultant**: frontend-architect (consultation on design decisions)
- **Accessibility Consultant**: ui-accessibility-specialist (consultation on UI/UX)
- **WebSocket Consultant**: go-websocket-specialist (consultation on protocol)

**Note**: Rework tasks (RW-A through RW-J) are only activated if the corresponding code review or product review fails. The typescript-react-developer will use the review outputs from `code_review/{task-id}.md` and `product_review/{track-letter}.md` to address all identified issues.

### Time Estimates
- Parallel execution time: ~4-5 days with integrated code reviews
- Sequential execution time: ~12-15 days
- Code review overhead: ~30-45 minutes per review

### Quality Gates
Each code review and product review must pass before dependent tracks can proceed. This ensures:
- Consistent code quality throughout development
- Specification compliance at each milestone
- Early detection of issues
- Adherence to latest version requirements
- Proper linting and type checking from the start
- Alignment with product requirements and design

## Success Criteria
- All TypeScript files compile with strict mode
- All linting rules pass (ESLint + Prettier)
- All components have minimum 44x44px touch targets
- WebSocket reconnection works reliably
- App works on iOS Safari 14+ and Chrome Android 90+
- Bundle size is under 500KB
- All tests pass with >80% coverage on critical paths
- All code reviews are approved
- All product reviews confirm specification compliance
- All dependencies use latest stable versions