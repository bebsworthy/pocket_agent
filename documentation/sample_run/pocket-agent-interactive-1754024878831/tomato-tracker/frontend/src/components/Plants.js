import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { format } from 'date-fns';

function Plants() {
  const [plants, setPlants] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchPlants();
  }, []);

  const fetchPlants = async () => {
    try {
      const response = await axios.get('/api/plants');
      setPlants(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching plants:', error);
      setLoading(false);
    }
  };

  const deletePlant = async (id) => {
    if (window.confirm('Are you sure you want to delete this plant?')) {
      try {
        await axios.delete(`/api/plants/${id}`);
        fetchPlants();
      } catch (error) {
        console.error('Error deleting plant:', error);
      }
    }
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h2>My Plants</h2>
        <Link to="/add-plant" className="btn btn-primary">+ Add New Plant</Link>
      </div>

      {plants.length === 0 ? (
        <div className="card">
          <p>No plants found. Start by adding your first tomato plant!</p>
        </div>
      ) : (
        <div className="grid grid-2">
          {plants.map((plant) => (
            <Link to={`/plants/${plant.id}`} key={plant.id} style={{ textDecoration: 'none' }}>
              <div className="card plant-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                  <div style={{ flex: 1 }}>
                    <div className="plant-variety">{plant.variety}</div>
                    <div className="plant-info">
                      <div>📍 {plant.location || 'No location'}</div>
                      <div>📅 Planted: {format(new Date(plant.planting_date), 'MMM dd, yyyy')}</div>
                      <div>
                        🌱 Status: 
                        <span style={{ 
                          color: plant.status === 'active' ? '#27ae60' : '#e74c3c',
                          fontWeight: '600'
                        }}> {plant.status}</span>
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={(e) => {
                      e.preventDefault();
                      deletePlant(plant.id);
                    }}
                    className="btn btn-danger"
                    style={{ padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}
                  >
                    Delete
                  </button>
                </div>
                
                <div className="harvest-stats">
                  <div className="harvest-stat">
                    <div className="harvest-stat-value">{plant.harvest_count || 0}</div>
                    <div className="harvest-stat-label">Harvests</div>
                  </div>
                  <div className="harvest-stat">
                    <div className="harvest-stat-value">
                      {(plant.total_weight_kg || 0).toFixed(1)} kg
                    </div>
                    <div className="harvest-stat-label">Total Weight</div>
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export default Plants;