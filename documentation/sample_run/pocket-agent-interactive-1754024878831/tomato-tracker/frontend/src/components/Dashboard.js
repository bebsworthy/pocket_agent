import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import axios from 'axios';
import { format } from 'date-fns';

function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      const response = await axios.get('/api/stats/overview');
      setStats(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching stats:', error);
      setLoading(false);
    }
  };

  if (loading) return <div>Loading...</div>;
  if (!stats) return <div>Error loading dashboard</div>;

  return (
    <div>
      <h2 style={{ marginBottom: '2rem' }}>Dashboard</h2>
      
      <div className="grid grid-4" style={{ marginBottom: '2rem' }}>
        <div className="card stat-card">
          <div className="stat-value">{stats.totalPlants?.count || 0}</div>
          <div className="stat-label">Active Plants</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">{stats.totalHarvests?.count || 0}</div>
          <div className="stat-label">Total Harvests</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">
            {(stats.totalHarvests?.total_weight || 0).toFixed(1)} kg
          </div>
          <div className="stat-label">Total Weight</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">
            {stats.totalHarvests?.count > 0 
              ? (stats.totalHarvests.total_weight / stats.totalHarvests.count).toFixed(1)
              : 0} kg
          </div>
          <div className="stat-label">Avg per Harvest</div>
        </div>
      </div>

      <div className="grid grid-2">
        <div className="card">
          <h3 style={{ marginBottom: '1rem' }}>Monthly Harvest Trend</h3>
          {stats.monthlyHarvests?.length > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <LineChart data={stats.monthlyHarvests.reverse()}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip />
                <Line 
                  type="monotone" 
                  dataKey="total_weight" 
                  stroke="#27ae60" 
                  name="Weight (kg)"
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <p>No harvest data available</p>
          )}
        </div>

        <div className="card">
          <h3 style={{ marginBottom: '1rem' }}>Top Producers</h3>
          {stats.topProducers?.length > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={stats.topProducers}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="variety" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="total_weight" fill="#e74c3c" name="Weight (kg)" />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p>No production data available</p>
          )}
        </div>
      </div>

      <div className="card">
        <h3 style={{ marginBottom: '1rem' }}>Recent Harvests</h3>
        {stats.recentHarvests?.length > 0 ? (
          <table style={{ width: '100%' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #ecf0f1', textAlign: 'left' }}>
                <th style={{ padding: '0.5rem' }}>Date</th>
                <th style={{ padding: '0.5rem' }}>Variety</th>
                <th style={{ padding: '0.5rem' }}>Quantity</th>
                <th style={{ padding: '0.5rem' }}>Weight</th>
              </tr>
            </thead>
            <tbody>
              {stats.recentHarvests.map((harvest) => (
                <tr key={harvest.id} style={{ borderBottom: '1px solid #ecf0f1' }}>
                  <td style={{ padding: '0.5rem' }}>
                    {format(new Date(harvest.harvest_date), 'MMM dd, yyyy')}
                  </td>
                  <td style={{ padding: '0.5rem' }}>{harvest.variety}</td>
                  <td style={{ padding: '0.5rem' }}>{harvest.quantity || '-'}</td>
                  <td style={{ padding: '0.5rem' }}>{harvest.weight_kg} kg</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p>No recent harvests</p>
        )}
      </div>
    </div>
  );
}

export default Dashboard;