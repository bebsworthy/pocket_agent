import { atom } from 'jotai';
import type { Project } from '../types/models';

// Projects list atom
export const projectsAtom = atom<Project[]>([]);

// Current selected project atom
export const currentProjectAtom = atom<Project | null>(null);

// Projects loading state
export const projectsLoadingAtom = atom<boolean>(false);

// Derived atom for project count
export const projectCountAtom = atom(get => get(projectsAtom).length);
