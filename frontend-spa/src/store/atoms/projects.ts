/**
 * Project state atoms using Jotai for atomic state management.
 * Provides persistent storage of projects using localStorage.
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { Project } from '../../types/models';

// Projects list with localStorage persistence and error handling
export const projectsAtom = atomWithStorage<Project[]>('projects', [], {
  getOnInit: true,
  serialize: JSON.stringify,
  deserialize: (str) => {
    try {
      const parsed = JSON.parse(str);
      // Validate that parsed data is an array
      if (!Array.isArray(parsed)) {
        console.warn('Projects data in localStorage is not an array, resetting to empty array');
        return [];
      }
      return parsed;
    } catch (error) {
      console.error('Failed to deserialize projects from localStorage:', error);
      return [];
    }
  }
});

// Selected project ID
export const selectedProjectIdAtom = atom<string | null>(null);

// Derived atom for selected project
export const selectedProjectAtom = atom(
  (get) => {
    const projects = get(projectsAtom);
    const selectedId = get(selectedProjectIdAtom);
    return projects.find(p => p.id === selectedId) || null;
  }
);

// Project loading state
export const projectsLoadingAtom = atom<boolean>(false);

// Derived atom for project count
export const projectCountAtom = atom(
  (get) => get(projectsAtom).length
);

// Derived atom to check if projects exist
export const hasProjectsAtom = atom(
  (get) => get(projectsAtom).length > 0
);

// Atom for tracking project operations (add, remove, update)
export const projectOperationAtom = atom<{
  type: 'add' | 'remove' | 'update' | null;
  projectId?: string;
}>({ type: null });

// Write-only atom for adding a project
export const addProjectAtom = atom(
  null,
  (get, set, newProject: Omit<Project, 'id'>) => {
    const projects = get(projectsAtom);
    const projectWithId: Project = {
      ...newProject,
      id: crypto.randomUUID(),
      createdAt: new Date().toISOString(),
      lastActive: new Date().toISOString()
    };
    set(projectsAtom, [...projects, projectWithId]);
    set(projectOperationAtom, { type: 'add', projectId: projectWithId.id });
    return projectWithId;
  }
);

// Write-only atom for removing a project
export const removeProjectAtom = atom(
  null,
  (get, set, projectId: string) => {
    const projects = get(projectsAtom);
    const filteredProjects = projects.filter(p => p.id !== projectId);
    set(projectsAtom, filteredProjects);
    set(projectOperationAtom, { type: 'remove', projectId });
    
    // Clear selection if the removed project was selected
    const selectedId = get(selectedProjectIdAtom);
    if (selectedId === projectId) {
      set(selectedProjectIdAtom, null);
    }
  }
);

// Write-only atom for updating a project
export const updateProjectAtom = atom(
  null,
  (get, set, updatedProject: Project) => {
    const projects = get(projectsAtom);
    const updatedProjects = projects.map(p => 
      p.id === updatedProject.id 
        ? { ...updatedProject, lastActive: new Date().toISOString() }
        : p
    );
    set(projectsAtom, updatedProjects);
    set(projectOperationAtom, { type: 'update', projectId: updatedProject.id });
  }
);

// Write-only atom for updating project last active time
export const updateProjectLastActiveAtom = atom(
  null,
  (get, set, projectId: string) => {
    const projects = get(projectsAtom);
    const updatedProjects = projects.map(p => 
      p.id === projectId 
        ? { ...p, lastActive: new Date().toISOString() }
        : p
    );
    set(projectsAtom, updatedProjects);
  }
);