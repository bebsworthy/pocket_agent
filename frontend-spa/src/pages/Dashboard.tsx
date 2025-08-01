import React from 'react';

export const Dashboard: React.FC = () => {
  return (
    <div className="container mx-auto max-w-md p-4">
      <div className="mb-6">
        <h1 className="mb-2 text-2xl font-bold">Pocket Agent</h1>
        <div className="flex items-center gap-2">
          <div className="h-3 w-3 rounded-full bg-gray-500" />
          <span className="text-sm capitalize text-gray-600">Ready</span>
        </div>
      </div>

      <div className="space-y-4">
        <div className="rounded-lg bg-white p-6 text-center shadow">
          <p className="text-gray-600">No projects available</p>
        </div>
      </div>
    </div>
  );
};
