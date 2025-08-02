# Project Creation State Atoms

This document explains how to use the project creation state atoms (`projectCreationAtoms.ts`) that manage the project creation workflow.

## Overview

The project creation atoms follow the established Jotai patterns in this codebase:
- Main state atom with localStorage persistence
- Derived atoms for performance optimization  
- Write-only atoms for actions
- Comprehensive error handling

## Key Atoms

### Main State
- `createProjectStateAtom` - Main state with localStorage persistence
- `createProjectFormDataAtom` - Form data with change tracking
- `createProjectErrorsAtom` - Validation errors
- `createProjectIsVisibleAtom` - Modal visibility state

### Derived Atoms (Read-only)
- `createProjectHasErrorsAtom` - Boolean if any errors exist
- `createProjectIsValidAtom` - Boolean if form is valid and ready to submit
- `createProjectHasUnsavedChangesAtom` - Boolean if user has unsaved changes

### Action Atoms (Write-only)
- `showCreateProjectModalAtom` - Show the creation modal
- `hideCreateProjectModalAtom` - Hide modal (preserves unsaved changes)
- `updateCreateProjectFieldAtom` - Update a single form field
- `setCreateProjectFieldErrorAtom` - Set field-specific error
- `resetCreateProjectFormAtom` - Reset entire form state
- `startCreateProjectSubmissionAtom` - Begin form submission
- `completeCreateProjectSubmissionAtom` - Handle successful submission
- `failCreateProjectSubmissionAtom` - Handle submission failure

## Usage Example

```typescript
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
  createProjectFormDataAtom,
  createProjectIsVisibleAtom,
  updateCreateProjectFieldAtom,
  showCreateProjectModalAtom,
  completeCreateProjectSubmissionAtom
} from '../atoms';

function ProjectCreationModal() {
  const formData = useAtomValue(createProjectFormDataAtom);
  const isVisible = useAtomValue(createProjectIsVisibleAtom);
  const updateField = useSetAtom(updateCreateProjectFieldAtom);
  const showModal = useSetAtom(showCreateProjectModalAtom);
  const completeSubmission = useSetAtom(completeCreateProjectSubmissionAtom);

  const handleFieldChange = (field: string, value: string) => {
    updateField(field, value);
  };

  const handleSubmit = async () => {
    // ... submit logic
    completeSubmission(); // Resets state and hides modal
  };

  return (
    // ... modal JSX
  );
}
```

## Persistence Strategy

- Form data and `hasUnsavedChanges` are persisted to localStorage
- Modal visibility, errors, and submission state are NOT persisted (session-only)
- Storage includes error handling and validation for corrupted data
- Cross-tab synchronization via storage events

## Error Handling

The atoms include comprehensive error handling:
- localStorage failures gracefully fall back to defaults
- Invalid stored data is detected and reset
- Field-level error management with auto-clearing on user input
- Batch error setting for server validation responses

## Performance Optimizations

- Derived atoms prevent unnecessary re-renders
- Form field updates only trigger related atom updates
- localStorage writes are only triggered by actual changes
- Storage subscription includes proper cleanup