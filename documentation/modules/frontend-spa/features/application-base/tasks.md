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
- [x] 1. **Initialize React SPA with Vite**
  - Create `frontend-spa/` directory
  - Initialize Vite project with React and TypeScript (latest versions)
  - Configure `tsconfig.json` with strict mode
  - Set up `vite.config.ts` with build optimizations
  - Create mobile-optimized `index.html` with viewport meta tags
  - Files: `frontend-spa/package.json`, `tsconfig.json`, `vite.config.ts`, `index.html`
  - _Requirements: 1.1, 1.2, 1.3, 1.5_
  - _Agent: typescript-react-developer_
- [x] 2. **Set up TailwindCSS and styling infrastructure**
  - Install and configure TailwindCSS (latest version)
  - Create `tailwind.config.js` with mobile-first design system
  - Set up `src/styles/globals.css` with Tailwind imports
  - Create `src/styles/themes.ts` with light/dark theme constants
  - Configure PostCSS for Tailwind
  - Files: `tailwind.config.js`, `postcss.config.js`, `src/styles/globals.css`, `src/styles/themes.ts`
  - _Requirements: 1.4, 11.1, 11.4_
  - _Agent: typescript-react-developer_
- [x] 3. **Set up linting and type checking**
  - Install ESLint with TypeScript plugin (latest versions)
  - Configure ESLint for React/TypeScript best practices
  - Set up Prettier for code formatting
  - Configure pre-commit hooks with Husky
  - Add npm scripts: `lint`, `lint:fix`, `typecheck`
  - Ensure VS Code integration works
  - Files: `.eslintrc.json`, `.prettierrc`, `.husky/pre-commit`, `package.json` scripts
  - _Requirements: Code quality and consistency_
  - _Agent: typescript-react-developer_
- [x] 4. **Copy TypeScript message types from test-client**
  - Copy message type definitions from `test-client/src/types/messages.ts`
  - Create `src/types/messages.ts` with all message interfaces
  - Create `src/types/models.ts` with Project and Server interfaces
  - Create `src/types/index.ts` as barrel export
  - Files: `src/types/messages.ts`, `src/types/models.ts`, `src/types/index.ts`
  - _Requirements: 5.3_
  - _Agent: typescript-react-developer_
- [x] CR-A. **Code Review: Foundation Setup**
  - Review project configuration and build setup
  - Verify all libraries use latest stable versions
  - Check linting and TypeScript configuration
  - Validate type definitions match server protocol
  - Ensure mobile viewport configuration is correct
  - _Dependencies: Tasks 1-4_
  - _Agent: typescript-react-code-reviewer_
- [x] PR-A. **Product Review: Track A Foundation**
  - Validate project setup meets all requirements in sections 1.x
  - Verify TypeScript strict mode configuration
  - Check mobile viewport and meta tags implementation
  - Ensure all type definitions match server protocol specs
  - Validate linting and formatting setup
  - Review output saved to: `product_review/track-a.md`
  - _Spec References: requirements.md sections 1.x, 5.3; design.md Project Setup_
  - _Dependencies: CR-A_
  - _Agent: product-owner-reviewer_
  - **STATUS: REQUIRES REVISION - Critical file activation needed**
- [x] RW-A. **Rework: Address Track A Review Findings**
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
- [x] 5. **Implement Button atom component**
  - Create `src/components/ui/atoms/Button.tsx` with mobile-optimized design
  - Implement variants (primary, secondary, ghost)
  - Ensure minimum 44px touch targets
  - Add loading and disabled states
  - Create barrel export in `src/components/ui/atoms/index.ts`
  - Files: `src/components/ui/atoms/Button.tsx`, `src/components/ui/atoms/index.ts`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] 6. **Implement Input and IconButton atoms**
  - Create `src/components/ui/atoms/Input.tsx` with mobile keyboard support
  - Create `src/components/ui/atoms/IconButton.tsx` with touch optimization
  - Add proper ARIA labels and inputMode attributes
  - Update barrel export
  - Files: `src/components/ui/atoms/Input.tsx`, `src/components/ui/atoms/IconButton.tsx`
  - _Requirements: 2.1, 2.3, 2.4, 2.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] CR-B. **Code Review: Atom Components**
  - Review component API consistency
  - Validate 44px touch targets
  - Check ARIA implementation
  - Ensure TypeScript types are strict
  - Test keyboard navigation
  - _Dependencies: Tasks 5-6_
  - _Agent: typescript-react-code-reviewer_
- [x] PR-B. **Product Review: Track B Atoms**
  - Validate all atom components meet requirements 2.x
  - Verify 44px minimum touch targets
  - Check mobile-first design implementation
  - Ensure accessibility compliance (ARIA labels)
  - Validate component API consistency
  - Review output saved to: `product_review/track-b.md`
  - _Spec References: requirements.md sections 2.x; design.md Component Architecture_
  - _Dependencies: CR-B_
  - _Agent: product-owner-reviewer_
- [x] RW-B. **Rework: Address Track B Review Findings**
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
- [x] 7. **Implement Card and StatusIndicator molecules**
  - Create `src/components/ui/molecules/Card.tsx` with press states
  - Create `src/components/ui/molecules/StatusIndicator.tsx` for connection status
  - Implement touch-friendly interactions
  - Create barrel export in `src/components/ui/molecules/index.ts`
  - Files: `src/components/ui/molecules/Card.tsx`, `src/components/ui/molecules/StatusIndicator.tsx`, `src/components/ui/molecules/index.ts`
  - _Requirements: 2.1, 2.2, 2.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] 8. **Implement SegmentedControl molecule**
  - Create `src/components/ui/molecules/SegmentedControl.tsx` for tab navigation
  - Implement generic type support for tab values
  - Add mobile-optimized styling and animations
  - Update barrel export
  - Files: `src/components/ui/molecules/SegmentedControl.tsx`
  - _Requirements: 2.1, 2.2, 2.6, 7.2_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] CR-C. **Code Review: Molecule Components**
  - Review component composition patterns
  - Validate mobile interactions
  - Check animation performance
  - Ensure proper TypeScript generics
  - _Dependencies: Tasks 7-8_
  - _Agent: typescript-react-code-reviewer_
  - **STATUS: REQUIRES CHANGES - Critical issues found in component architecture**
- [x] PR-C. **Product Review: Track C Molecules**
  - Validate molecule components meet requirements 2.x and 7.2
  - Verify SegmentedControl matches mockup navigation
  - Check Card component press states
  - Ensure StatusIndicator shows connection states correctly
  - Validate component composition patterns
  - Review output saved to: `product_review/track-c.md`
  - _Spec References: requirements.md sections 2.x, 7.2; mockups/mobile-mockup.png_
  - _Dependencies: CR-C_
  - _Agent: product-owner-reviewer_
- [x] RW-C. **Rework: Address Track C Review Findings**
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
- [x] 9. **Set up Jotai state management**
  - Install Jotai and jotai/utils (latest versions)
  - Create `src/store/atoms/projects.ts` with project atoms
  - Create `src/store/atoms/servers.ts` with server atoms
  - Create `src/store/atoms/ui.ts` with theme and loading atoms
  - Create `src/store/atoms/websocket.ts` for WebSocket state
  - Files: `src/store/atoms/*.ts`
  - _Requirements: 4.1, 4.2, 4.5_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] 10. **Implement custom state hooks**
  - Create `src/store/hooks/useProjects.ts` with project management logic
  - Create `src/store/hooks/useServers.ts` with server management logic
  - Create `src/store/hooks/useWebSocket.ts` for WebSocket integration
  - Implement localStorage persistence with atomWithStorage
  - Files: `src/store/hooks/*.ts`
  - _Requirements: 4.1, 4.3, 4.4, 8.1-8.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] 11. **Implement localStorage service**
  - Create `src/services/storage/LocalStorageService.ts`
  - Create `src/services/storage/hooks.ts` for React integration
  - Implement project and server persistence
  - Add error handling for storage quota
  - Files: `src/services/storage/*.ts`
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  - _Dependencies: CR-A_
- [x] CR-D. **Code Review: State Management**
  - Review Jotai atom patterns
  - Validate WebSocket service robustness
  - Check localStorage integration
  - Ensure error handling is comprehensive
  - Test reconnection scenarios
  - _Dependencies: Tasks 9-11_
  - _Agent: typescript-react-code-reviewer_
- [x] PR-D. **Product Review: Track D State & Services**
  - Validate state management meets requirements 4.x
  - Verify WebSocket service meets requirements 5.x
  - Check data persistence meets requirements 8.x
  - Ensure error handling meets requirements 10.x
  - Validate WebSocket reconnection behavior
  - Review output saved to: `product_review/track-d.md`
  - _Spec References: requirements.md sections 4.x, 5.x, 8.x, 10.x; design.md State Management & WebSocket_
  - _Dependencies: CR-D_
  - _Agent: product-owner-reviewer_
- [x] RW-D. **Rework: Address Track D Review Findings**
  - Review findings from `code_review/CR-D.md` and/or `product_review/track-d.md`
  - Fix state management patterns
  - Improve WebSocket error handling
  - Optimize localStorage usage
  - Enhance reconnection logic
  - _Trigger: Only if CR-D or PR-D status is "Requires changes"_
  - _Dependencies: CR-D and/or PR-D (failed)_
  - _Agent: typescript-react-developer_
### Track E: Storage & Utils (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: typescript-react-developer
- [x] 12. **Implement localStorage service**
  - Create `src/services/storage/LocalStorageService.ts` with type-safe operations
  - Implement error handling for storage quota and corruption
  - Add data migration utilities
  - Create hooks for localStorage integration with React
  - Files: `src/services/storage/*.ts`
  - _Requirements: 8.1-8.6_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] 13. **Create utility functions**
  - Create `src/utils/constants.ts` with app constants
  - Create `src/utils/helpers.ts` with common utilities
  - Create `src/utils/cn.ts` for className merging (TailwindCSS)
  - Add mobile-specific utilities (touch detection, viewport helpers)
  - Files: `src/utils/*.ts`
  - _Requirements: 9.1-9.6 (mobile optimizations)_
  - _Dependencies: CR-A_
  - _Agent: typescript-react-developer_
- [x] CR-E. **Code Review: Storage and Utilities**
  - Review localStorage error handling
  - Validate utility function implementations
  - Check mobile-specific optimizations
  - Ensure type safety across utilities
  - _Dependencies: Tasks 12-13_
  - _Agent: typescript-react-code-reviewer_
- [x] PR-E. **Product Review: Track E Storage & Utils**
  - Validate storage service meets requirements 8.x
  - Verify mobile optimizations meet requirements 9.x
  - Check utility functions support specification needs
  - Ensure error handling is comprehensive
  - Review output saved to: `product_review/track-e.md`
  - _Spec References: requirements.md sections 8.x, 9.x; design.md Storage & Utilities_
  - _Dependencies: CR-E_
  - _Agent: product-owner-reviewer_
- [x] RW-E. **Rework: Address Track E Review Findings**
  - Review findings from `code_review/CR-E.md` and/or `product_review/track-e.md`
  - Fix localStorage error handling
  - Improve mobile optimizations
  - Enhance utility functions
  - Address type safety issues
  - _Trigger: Only if CR-E or PR-E status is "Requires changes"_
  - _Dependencies: CR-E and/or PR-E (failed)_
  - _Agent: typescript-react-developer_
### Checkpoint Review 1
- [x] CR1. **Comprehensive Review: Foundation and Components**
  - Review overall project structure
  - Validate component library consistency
  - Check state management architecture
  - Ensure WebSocket integration works properly
  - Test mobile responsiveness across all components
  - _Dependencies: All previous CR/PR approvals_
  - _Agent: typescript-react-code-reviewer_
  - **STATUS: APPROVED PENDING ACTIVATION** - Foundation is architecturally complete and excellent quality, requires file activation
- [x] PR1. **Product Review: Foundation Complete**
  - Validate all foundation requirements are implemented
  - Verify mobile-first design across all components
  - Check accessibility compliance (WCAG 2.1 AA)
  - Ensure performance targets are met
  - Test on actual mobile devices
  - Review output saved to: `product_review/foundation-complete.md`
  - _Spec References: All requirements sections_
  - _Dependencies: CR1_
  - _Agent: product-owner-reviewer_

### Track F: WebSocket Service (Dependencies: CR1)
> Primary Agent: typescript-react-developer
> Consultants: go-websocket-specialist (for protocol guidance)

- [x] 14. **Implement WebSocket service core**
  - Create `src/services/websocket/WebSocketService.ts` with EventEmitter
  - Implement connection management with native WebSocket API
  - Add automatic reconnection with exponential backoff
  - Implement project join/leave tracking
  - Support both WS and WSS protocols
  - Files: `src/services/websocket/WebSocketService.ts`
  - _Requirements: 5.1, 5.2, 5.4, 5.5, 5.7_
  - _Dependencies: CR1_
  - _Agent: typescript-react-developer_

- [x] 15. **Create WebSocket React integration**
  - Create `src/services/websocket/hooks.ts` with useWebSocket hook
  - Create `src/services/websocket/WebSocketContext.tsx` for provider
  - Implement message type handlers
  - Add connection state management
  - Files: `src/services/websocket/hooks.ts`, `src/services/websocket/WebSocketContext.tsx`
  - _Requirements: 5.3, 5.6_
  - _Dependencies: Tasks 10, 14_
  - _Agent: typescript-react-developer_

- [x] CR-F. **Code Review: WebSocket Service**
  - Review reconnection logic
  - Validate message handling
  - Check error scenarios
  - Ensure proper cleanup on unmount
  - _Dependencies: Tasks 14-15_
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

- [x] 16. **Set up React Router and app structure**
  - Install react-router-dom (latest version)
  - Create `src/Router.tsx` with route definitions
  - Create `src/App.tsx` with theme provider and error boundary
  - Create `src/main.tsx` as entry point
  - Implement lazy loading for routes
  - Files: `src/Router.tsx`, `src/App.tsx`, `src/main.tsx`
  - _Requirements: 3.1, 3.2, 3.4, 3.5_
  - _Dependencies: CR1_
  - _Agent: typescript-react-developer_

- [x] 17. **Implement error boundary and theme system**
  - Create `src/components/ErrorBoundary.tsx` for error handling
  - Implement theme switching logic in App.tsx
  - Create `src/components/LoadingScreen.tsx`
  - Set up dark mode class toggling
  - Files: `src/components/ErrorBoundary.tsx`, `src/components/LoadingScreen.tsx`
  - _Requirements: 10.5, 11.2, 11.3, 11.5_
  - _Dependencies: Tasks 10, 16_
  - _Agent: typescript-react-developer_

- [x] CR-G. **Code Review: Routing and App Shell**
  - Review route structure
  - Validate error boundary implementation
  - Check theme switching logic
  - Ensure proper lazy loading
  - _Dependencies: Tasks 16-17_
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

- [x] 18. **Implement organism components**
  - Create `src/components/ui/organisms/ProjectCard.tsx`
  - Create `src/components/ui/organisms/ServerForm.tsx`
  - Create `src/components/ui/organisms/EmptyState.tsx`
  - Create barrel export
  - Integrate with molecules and atoms
  - Files: `src/components/ui/organisms/*.tsx`
  - _Requirements: 2.1, 6.2, 6.7_
  - _Dependencies: CR-B, CR-C_
  - _Agent: typescript-react-developer_

- [x] CR-H. **Code Review: Organism Components**
  - Review component integration
  - Validate form accessibility
  - Check empty state patterns
  - Ensure proper composition
  - _Dependencies: Task 18_
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

## Execution Order
1. **Group 1 (Immediate Start)**:
   - Track A: Foundation (Tasks 1-4)
   
2. **Group 2 (After CR-A)**:
   - CR-A, PR-A, (RW-A if needed)
   
3. **Group 3 (After PR-A approval)**:
   - Track B: Atoms (Tasks 5-6)
   - Track C: Molecules (Tasks 7-8) 
   - Track D: State & Services (Tasks 9-11)
   - Track E: Storage & Utils (Tasks 12-13)
   
4. **Group 4 (Individual Track Reviews)**:
   - CR-B, PR-B, (RW-B if needed)
   - CR-C, PR-C, (RW-C if needed)  
   - CR-D, PR-D, (RW-D if needed)
   - CR-E, PR-E, (RW-E if needed)
   
5. **Group 5 (After CR1 approval)**:
   - Track F: WebSocket Service (Tasks 14-15)
   - Track G: Routing & App Shell (Tasks 16-17)
   - Track H: Organism Components (Task 18)
   
6. **Group 6 (Group 3 Reviews)**:
   - CR-F, PR-F, (RW-F if needed)
   - CR-G, PR-G, (RW-G if needed)
   - CR-H, PR-H, (RW-H if needed)
   
7. **Group 7 (Second Checkpoint)**:
   - CR2 (after all Group 3 track approvals)
## Success Criteria
- All TypeScript code compiles without errors in strict mode
- All components are mobile-optimized with 44px+ touch targets
- WebSocket service handles reconnection and errors gracefully
- Application loads in <3 seconds on 3G networks
- Bundle size is <500KB initial load
- All components have proper ARIA labels and accessibility
- Code coverage >80% (when tests are added later)
- Passes all ESLint rules with no warnings
## Notes
- This foundation will be used by all other features in the frontend-spa module
- Mobile-first design is critical - test on actual mobile devices during development  
- WebSocket integration must be rock-solid as it's the core communication mechanism
- Component library should be expandable - other features will add more components
- State management patterns established here will be used throughout the application