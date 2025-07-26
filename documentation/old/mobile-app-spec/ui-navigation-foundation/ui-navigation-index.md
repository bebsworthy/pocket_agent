# UI Navigation & Foundation Feature Documentation Index

This feature has been split into multiple focused documents for better readability and navigation. Each document covers a specific aspect of the UI Navigation & Foundation implementation.

## Documentation Structure

### 1. [Overview](./ui-navigation-overview.feat.md)
- Feature introduction and purpose
- Architecture overview
- Technology stack
- Key components listing
- Implementation overview

### 2. [Navigation Framework](./ui-navigation-navigation.feat.md)
- Complete navigation structure implementation
- Screen destinations and routing
- Bottom navigation implementation
- Deep linking support
- Navigation state management
- Back stack handling

### 3. [Theme System](./ui-navigation-theme.feat.md)
- Material Design 3 theme implementation
- Custom color schemes
- Typography system
- Dark/light mode switching
- Dynamic color support (Android 12+)
- Theme persistence with DataStore

### 4. [Base UI Components](./ui-navigation-components.feat.md)
- Reusable UI components library
- Button components with loading states
- Status indicators and chips
- Cards and list items
- Loading and error states
- Accessibility components
- Keyboard navigation helpers

### 5. [Screen Scaffolding](./ui-navigation-scaffolding.feat.md)
- Screen layout patterns
- Base screen scaffold
- Stateful screen wrapper
- Screen implementations
- Welcome screen
- Projects list screen
- Project detail screens
- Wrapper installation dialogs

### 6. [State Management](./ui-navigation-state.feat.md)
- ViewModel architecture
- UI state handling
- Error handling and recovery
- Retry policies
- Deep link handling
- Repository interfaces
- Navigation management

### 7. [Testing](./ui-navigation-testing.feat.md)
- Comprehensive testing checklist
- Unit test examples
- Integration test scenarios
- Navigation flow tests
- Theme persistence tests
- Accessibility testing
- Performance testing guidelines

### 8. [Implementation Notes](./ui-navigation-implementation.feat.md)
- Critical implementation details
- Performance considerations
- Package structure
- Future extensions
- Android-specific optimizations
- Gesture navigation support

## Quick Navigation

- **Starting Point**: Begin with the [Overview](./ui-navigation-overview.feat.md) to understand the feature's purpose and architecture
- **Core Implementation**: The [Navigation Framework](./ui-navigation-navigation.feat.md) contains the main navigation implementation
- **Visual Design**: The [Theme System](./ui-navigation-theme.feat.md) covers all styling and theming aspects
- **Building Blocks**: The [Base UI Components](./ui-navigation-components.feat.md) provides reusable UI elements
- **Screen Structure**: The [Screen Scaffolding](./ui-navigation-scaffolding.feat.md) shows how screens are structured
- **Data Flow**: The [State Management](./ui-navigation-state.feat.md) explains state handling and data flow
- **Quality Assurance**: The [Testing](./ui-navigation-testing.feat.md) provides comprehensive testing strategies
- **Best Practices**: The [Implementation Notes](./ui-navigation-implementation.feat.md) contains important implementation details

## Key Components Summary

### Navigation
- **AppNavigation**: Central navigation graph for the entire app
- **ProjectNavigation**: Nested navigation for project-level screens
- **DeepLinkHandler**: Manages navigation from external sources

### Theme
- **PocketAgentTheme**: Material Design 3 theme with custom styling
- **ThemePreferencesDataStore**: Persists theme preferences
- **Dynamic Color Support**: Adaptive theming on Android 12+

### Components
- **BaseScreen**: Common screen scaffold with app bar
- **PrimaryActionButton**: Main action button with loading states
- **StatusChip**: Connection status indicators
- **ProjectCard**: Project list item component
- **ErrorBoundary**: Error handling wrapper

### State Management
- **BaseViewModel**: Common ViewModel functionality
- **UiState**: Sealed class for UI state representation
- **NavigationManager**: Centralized navigation event handling

## Implementation Priority

1. **Phase 1**: Core navigation and theme system
   - AppNavigation setup
   - PocketAgentTheme implementation
   - Basic screen scaffolding

2. **Phase 2**: Base components and screens
   - Common UI components
   - Welcome and projects list screens
   - State management foundation

3. **Phase 3**: Advanced features
   - Deep linking
   - Theme persistence
   - Accessibility enhancements

4. **Phase 4**: Polish and optimization
   - Performance optimizations
   - Animation refinements
   - Comprehensive testing

## Related Documentation

- [Frontend Technical Specification](../frontend.spec.md)
- [Project Specification](../project.specs.md)
- [Screen Design Feature](../screen-design.feat.md)
- [Background Services](../background-services/background-services-index.md)
- [Communication Layer](../communication-layer/communication-layer-index.md)