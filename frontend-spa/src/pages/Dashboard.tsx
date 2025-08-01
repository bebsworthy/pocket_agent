import React from 'react';

export const Dashboard: React.FC = () => {
  return (
    <div className="container mx-auto p-4 max-w-md">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">
          Pocket Agent
        </h1>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-gray-500" />
          <span className="text-sm text-gray-600 capitalize">
            Ready
          </span>
        </div>
      </div>

      <div className="space-y-4">
        <div className="bg-white rounded-lg shadow p-6 text-center">
          <p className="text-gray-600">
            No projects available
          </p>
        </div>
      </div>
    </div>
  );
};