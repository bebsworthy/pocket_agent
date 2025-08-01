/**
 * Project state atoms using Jotai for atomic state management.
 * Provides persistent storage of projects using localStorage.
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { Project } from '../../types/models';

// Custom storage implementation with error handling for projects
const projectsStorage = {
  getItem: (key: string, initialValue: Project[]): Project[] => {
    try {
      const item = localStorage.getItem(key);
      if (item === null) {
        return initialValue;
      }
      const parsed = JSON.parse(item);
      // Validate that parsed data is an array
      if (!Array.isArray(parsed)) {
        console.warn('Projects data in localStorage is not an array, resetting to empty array');
        return initialValue;
      }
      return parsed as Project[];
    } catch (error) {
      console.error('Failed to deserialize projects from localStorage:', error);
      return initialValue;
    }
  },
  setItem: (key: string, value: Project[]): void => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      console.error('Failed to serialize projects to localStorage:', error);
    }
  },
  removeItem: (key: string): void => {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.error('Failed to remove projects from localStorage:', error);
    }
  },
  subscribe: (key: string, callback: (value: Project[]) => void, initialValue: Project[]) => {
    if (typeof window === 'undefined' || typeof window.addEventListener === 'undefined') {
      return () => {};
    }
    const handler = (e: StorageEvent) => {
      if (e.storageArea === localStorage && e.key === key) {
        try {
          const newValue = e.newValue ? JSON.parse(e.newValue) : initialValue;
          if (Array.isArray(newValue)) {
            callback(newValue as Project[]);
          } else {
            callback(initialValue);
          }
        } catch {
          callback(initialValue);
        }
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  },
};

// Projects list with localStorage persistence and error handling
export const projectsAtom = atomWithStorage<Project[]>('projects', [], projectsStorage);

// Selected project ID
export const selectedProjectIdAtom = atom(null as string | null);

// Derived atom for selected project
export const selectedProjectAtom = atom(get => {
  const projects = get(projectsAtom);
  const selectedId = get(selectedProjectIdAtom);
  return projects.find((p: Project) => p.id === selectedId) || null;
});

// Project loading state
export const projectsLoadingAtom = atom(false);

// Derived atom for project count
export const projectCountAtom = atom(get => get(projectsAtom).length);

// Derived atom to check if projects exist
export const hasProjectsAtom = atom(get => get(projectsAtom).length > 0);

// Atom for tracking project operations (add, remove, update)
export const projectOperationAtom = atom<{
  type: 'add' | 'remove' | 'update' | null;
  projectId?: string;
}>({ type: null });

// Write-only atom for adding a project
export const addProjectAtom = atom(
  null,
  (get, set, newProject: Omit<Project, 'id' | 'createdAt' | 'lastActive'>) => {
    const projects = get(projectsAtom);
    const projectWithId: Project = {
      ...newProject,
      id: crypto.randomUUID(),
      createdAt: new Date().toISOString(),
      lastActive: new Date().toISOString(),
    };
    set(projectsAtom, [...projects, projectWithId]);
    set(projectOperationAtom, { type: 'add', projectId: projectWithId.id });
    return projectWithId;
  }
);

// Write-only atom for removing a project
export const removeProjectAtom = atom(null, (get, set, projectId: string) => {
  const projects = get(projectsAtom);
  const filteredProjects = projects.filter((p: Project) => p.id !== projectId);
  set(projectsAtom, filteredProjects);
  set(projectOperationAtom, { type: 'remove', projectId });

  // Clear selection if the removed project was selected
  const selectedId = get(selectedProjectIdAtom);
  if (selectedId === projectId) {
    set(selectedProjectIdAtom, null);
  }
});

// Write-only atom for updating a project
export const updateProjectAtom = atom(null, (get, set, updatedProject: Project) => {
  const projects = get(projectsAtom);
  const updatedProjects = projects.map((p: Project) =>
    p.id === updatedProject.id ? { ...updatedProject, lastActive: new Date().toISOString() } : p
  );
  set(projectsAtom, updatedProjects);
  set(projectOperationAtom, { type: 'update', projectId: updatedProject.id });
});

// Write-only atom for updating project last active time
export const updateProjectLastActiveAtom = atom(null, (get, set, projectId: string) => {
  const projects = get(projectsAtom);
  const updatedProjects = projects.map((p: Project) =>
    p.id === projectId ? { ...p, lastActive: new Date().toISOString() } : p
  );
  set(projectsAtom, updatedProjects);
});
