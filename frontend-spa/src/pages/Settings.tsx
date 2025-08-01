import React from 'react';

export const Settings: React.FC = () => {
  return (
    <div className="container mx-auto max-w-md p-4">
      <div className="mb-6">
        <button
          onClick={() => window.history.back()}
          className="mb-4 text-blue-600 hover:text-blue-800"
        >
          ← Back
        </button>
        <h1 className="text-2xl font-bold">Settings</h1>
      </div>

      <div className="rounded-lg bg-white p-6 shadow">
        <h2 className="text-lg font-semibold">Application Settings</h2>
        <p className="text-gray-600">Settings interface will be implemented here.</p>
      </div>
    </div>
  );
};
