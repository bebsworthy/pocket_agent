import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { format } from 'date-fns';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';

function Harvests() {
  const [harvests, setHarvests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState({
    start_date: null,
    end_date: null,
    plant_id: ''
  });
  const [plants, setPlants] = useState([]);

  useEffect(() => {
    fetchHarvests();
    fetchPlants();
  }, []);

  const fetchHarvests = async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.start_date) queryParams.append('start_date', params.start_date);
      if (params.end_date) queryParams.append('end_date', params.end_date);
      if (params.plant_id) queryParams.append('plant_id', params.plant_id);
      
      const response = await axios.get(`/api/harvests?${queryParams}`);
      setHarvests(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching harvests:', error);
      setLoading(false);
    }
  };

  const fetchPlants = async () => {
    try {
      const response = await axios.get('/api/plants');
      setPlants(response.data);
    } catch (error) {
      console.error('Error fetching plants:', error);
    }
  };

  const handleFilterChange = () => {
    const params = {};
    if (filters.start_date) {
      params.start_date = filters.start_date.toISOString().split('T')[0];
    }
    if (filters.end_date) {
      params.end_date = filters.end_date.toISOString().split('T')[0];
    }
    if (filters.plant_id) {
      params.plant_id = filters.plant_id;
    }
    fetchHarvests(params);
  };

  const clearFilters = () => {
    setFilters({
      start_date: null,
      end_date: null,
      plant_id: ''
    });
    fetchHarvests();
  };

  const deleteHarvest = async (id) => {
    if (window.confirm('Are you sure you want to delete this harvest?')) {
      try {
        await axios.delete(`/api/harvests/${id}`);
        fetchHarvests();
      } catch (error) {
        console.error('Error deleting harvest:', error);
      }
    }
  };

  if (loading) return <div>Loading...</div>;

  const totalWeight = harvests.reduce((sum, h) => sum + (h.weight_kg || 0), 0);
  const totalQuantity = harvests.reduce((sum, h) => sum + (h.quantity || 0), 0);

  return (
    <div>
      <h2 style={{ marginBottom: '2rem' }}>Harvest History</h2>

      <div className="card" style={{ marginBottom: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Filters</h3>
        <div className="grid grid-3" style={{ gap: '1rem', alignItems: 'end' }}>
          <div className="form-group">
            <label className="form-label">Start Date</label>
            <DatePicker
              selected={filters.start_date}
              onChange={(date) => setFilters({...filters, start_date: date})}
              className="form-input"
              dateFormat="MMMM d, yyyy"
              placeholderText="Select start date"
              isClearable
            />
          </div>
          <div className="form-group">
            <label className="form-label">End Date</label>
            <DatePicker
              selected={filters.end_date}
              onChange={(date) => setFilters({...filters, end_date: date})}
              className="form-input"
              dateFormat="MMMM d, yyyy"
              placeholderText="Select end date"
              isClearable
            />
          </div>
          <div className="form-group">
            <label className="form-label">Plant</label>
            <select
              value={filters.plant_id}
              onChange={(e) => setFilters({...filters, plant_id: e.target.value})}
              className="form-select"
            >
              <option value="">All Plants</option>
              {plants.map((plant) => (
                <option key={plant.id} value={plant.id}>
                  {plant.variety} - {plant.location || 'No location'}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div style={{ marginTop: '1rem', display: 'flex', gap: '1rem' }}>
          <button onClick={handleFilterChange} className="btn btn-primary">
            Apply Filters
          </button>
          <button onClick={clearFilters} className="btn">
            Clear Filters
          </button>
        </div>
      </div>

      <div className="grid grid-3" style={{ marginBottom: '2rem' }}>
        <div className="card stat-card">
          <div className="stat-value">{harvests.length}</div>
          <div className="stat-label">Total Harvests</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">{totalWeight.toFixed(1)} kg</div>
          <div className="stat-label">Total Weight</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">{totalQuantity}</div>
          <div className="stat-label">Total Quantity</div>
        </div>
      </div>

      <div className="card">
        {harvests.length === 0 ? (
          <p>No harvests found matching your criteria.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', minWidth: '600px' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #ecf0f1', textAlign: 'left' }}>
                  <th style={{ padding: '0.75rem' }}>Date</th>
                  <th style={{ padding: '0.75rem' }}>Plant</th>
                  <th style={{ padding: '0.75rem' }}>Location</th>
                  <th style={{ padding: '0.75rem' }}>Quantity</th>
                  <th style={{ padding: '0.75rem' }}>Weight</th>
                  <th style={{ padding: '0.75rem' }}>Notes</th>
                  <th style={{ padding: '0.75rem' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {harvests.map((harvest) => (
                  <tr key={harvest.id} style={{ borderBottom: '1px solid #ecf0f1' }}>
                    <td style={{ padding: '0.75rem' }}>
                      {format(new Date(harvest.harvest_date), 'MMM dd, yyyy')}
                    </td>
                    <td style={{ padding: '0.75rem' }}>{harvest.variety}</td>
                    <td style={{ padding: '0.75rem' }}>{harvest.location || '-'}</td>
                    <td style={{ padding: '0.75rem' }}>{harvest.quantity || '-'}</td>
                    <td style={{ padding: '0.75rem', fontWeight: '600', color: '#27ae60' }}>
                      {harvest.weight_kg} kg
                    </td>
                    <td style={{ padding: '0.75rem' }}>{harvest.notes || '-'}</td>
                    <td style={{ padding: '0.75rem' }}>
                      <button
                        onClick={() => deleteHarvest(harvest.id)}
                        className="btn btn-danger"
                        style={{ padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

export default Harvests;