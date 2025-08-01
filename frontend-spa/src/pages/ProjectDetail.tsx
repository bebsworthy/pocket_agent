import React from 'react';
import { useParams, Navigate } from 'react-router-dom';

export const ProjectDetail: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  if (!projectId) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="container mx-auto max-w-md p-4">
      <div className="mb-6">
        <button
          onClick={() => window.history.back()}
          className="mb-4 text-blue-600 hover:text-blue-800"
        >
          ← Back
        </button>
        <h1 className="text-2xl font-bold">Project Details</h1>
      </div>

      <div className="rounded-lg bg-white p-6 shadow">
        <h2 className="text-lg font-semibold">Project {projectId}</h2>
        <p className="text-gray-600">Project details will be displayed here.</p>
      </div>
    </div>
  );
};
