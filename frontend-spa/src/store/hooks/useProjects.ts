import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import type { Project } from '../../types/models';

// Import existing atoms from atoms/projects.ts
import {
  projectsAtom,
  selectedProjectIdAtom,
  selectedProjectAtom,
  projectCountAtom,
  projectsLoadingAtom,
  hasProjectsAtom,
  addProjectAtom,
  removeProjectAtom,
  updateProjectAtom,
  updateProjectLastActiveAtom
} from '../atoms/projects';

// Custom hook for project management
export function useProjects() {
  const projects = useAtomValue(projectsAtom);
  const [selectedProjectId, setSelectedProjectId] = useAtom(selectedProjectIdAtom);
  const selectedProject = useAtomValue(selectedProjectAtom);
  const projectCount = useAtomValue(projectCountAtom);
  const hasProjects = useAtomValue(hasProjectsAtom);
  const [isLoading, setIsLoading] = useAtom(projectsLoadingAtom);

  // Action setters
  const addProjectAction = useSetAtom(addProjectAtom);
  const removeProjectAction = useSetAtom(removeProjectAtom);
  const updateProjectAction = useSetAtom(updateProjectAtom);
  const updateLastActiveAction = useSetAtom(updateProjectLastActiveAtom);

  const addProject = (projectData: Omit<Project, 'id' | 'createdAt' | 'lastActive'>) => {
    return addProjectAction(projectData);
  };

  const updateProject = (id: string, updates: Partial<Omit<Project, 'id' | 'createdAt'>>) => {
    const currentProject = projects.find(p => p.id === id);
    if (currentProject) {
      const updatedProject: Project = {
        ...currentProject,
        ...updates,
        lastActive: new Date().toISOString()
      };
      updateProjectAction(updatedProject);
    }
  };

  const removeProject = (id: string) => {
    removeProjectAction(id);
  };

  const selectProject = (id: string | null) => {
    if (id === null || projects.some(p => p.id === id)) {
      setSelectedProjectId(id);
      
      // Update last active time for the selected project
      if (id) {
        updateLastActiveAction(id);
      }
    }
  };

  const getProject = (id: string): Project | undefined => {
    return projects.find(p => p.id === id);
  };

  const getProjectsByServer = (serverId: string): Project[] => {
    return projects.filter(p => p.serverId === serverId);
  };

  const setLoading = (loading: boolean) => {
    setIsLoading(loading);
  };

  return {
    // State
    projects,
    selectedProject,
    selectedProjectId,
    projectCount,
    hasProjects,
    isLoading,
    
    // Actions
    addProject,
    updateProject,
    removeProject,
    selectProject,
    getProject,
    getProjectsByServer,
    setLoading
  };
}

// Additional hook for project operations without state subscriptions
export function useProjectActions() {
  const setSelectedProjectId = useSetAtom(selectedProjectIdAtom);
  const addProjectAction = useSetAtom(addProjectAtom);
  const removeProjectAction = useSetAtom(removeProjectAtom);
  const updateProjectAction = useSetAtom(updateProjectAtom);
  const updateLastActiveAction = useSetAtom(updateProjectLastActiveAtom);

  const addProject = (projectData: Omit<Project, 'id' | 'createdAt' | 'lastActive'>) => {
    return addProjectAction(projectData);
  };

  const updateProject = (projectToUpdate: Project) => {
    updateProjectAction(projectToUpdate);
  };

  const removeProject = (id: string) => {
    removeProjectAction(id);
  };

  const selectProject = (id: string | null) => {
    setSelectedProjectId(id);
    
    // Update last active time for the selected project
    if (id) {
      updateLastActiveAction(id);
    }
  };

  return {
    addProject,
    updateProject,
    removeProject,
    selectProject
  };
}