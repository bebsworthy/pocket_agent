import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { format } from 'date-fns';
import DatePicker from 'react-datepicker';
import PhotoGallery from './PhotoGallery';
import 'react-datepicker/dist/react-datepicker.css';

function PlantDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [plant, setPlant] = useState(null);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showHarvestForm, setShowHarvestForm] = useState(false);
  const [showCareForm, setShowCareForm] = useState(false);
  
  const [harvestForm, setHarvestForm] = useState({
    harvest_date: new Date(),
    quantity: '',
    weight_kg: '',
    notes: ''
  });
  
  const [careForm, setCareForm] = useState({
    care_type: 'watering',
    care_date: new Date(),
    notes: ''
  });

  useEffect(() => {
    fetchPlantDetails();
  }, [id]);

  const fetchPlantDetails = async () => {
    try {
      const [plantRes, statsRes] = await Promise.all([
        axios.get(`/api/plants/${id}`),
        axios.get(`/api/stats/plant/${id}`)
      ]);
      setPlant(plantRes.data);
      setStats(statsRes.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching plant details:', error);
      setLoading(false);
    }
  };

  const handleAddHarvest = async (e) => {
    e.preventDefault();
    try {
      await axios.post('/api/harvests', {
        plant_id: id,
        ...harvestForm,
        harvest_date: harvestForm.harvest_date.toISOString().split('T')[0]
      });
      setShowHarvestForm(false);
      setHarvestForm({
        harvest_date: new Date(),
        quantity: '',
        weight_kg: '',
        notes: ''
      });
      fetchPlantDetails();
    } catch (error) {
      console.error('Error adding harvest:', error);
    }
  };

  const handleAddCare = async (e) => {
    e.preventDefault();
    try {
      await axios.post('/api/care-logs', {
        plant_id: id,
        ...careForm,
        care_date: careForm.care_date.toISOString().split('T')[0]
      });
      setShowCareForm(false);
      setCareForm({
        care_type: 'watering',
        care_date: new Date(),
        notes: ''
      });
      fetchPlantDetails();
    } catch (error) {
      console.error('Error adding care log:', error);
    }
  };

  if (loading) return <div>Loading...</div>;
  if (!plant) return <div>Plant not found</div>;

  return (
    <div>
      <button 
        onClick={() => navigate('/plants')}
        className="btn"
        style={{ marginBottom: '1rem', backgroundColor: '#95a5a6', color: 'white' }}
      >
        ← Back to Plants
      </button>

      <div className="card">
        <h2>{plant.variety}</h2>
        <div className="grid grid-3" style={{ marginTop: '1rem' }}>
          <div>
            <strong>Location:</strong> {plant.location || 'Not specified'}
          </div>
          <div>
            <strong>Planted:</strong> {format(new Date(plant.planting_date), 'MMM dd, yyyy')}
          </div>
          <div>
            <strong>Status:</strong> 
            <span style={{ 
              color: plant.status === 'active' ? '#27ae60' : '#e74c3c',
              fontWeight: '600'
            }}> {plant.status}</span>
          </div>
        </div>
        {plant.notes && (
          <div style={{ marginTop: '1rem' }}>
            <strong>Notes:</strong> {plant.notes}
          </div>
        )}
      </div>

      <div className="grid grid-4">
        <div className="card stat-card">
          <div className="stat-value">{stats.harvestStats?.total_harvests || 0}</div>
          <div className="stat-label">Total Harvests</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">
            {(stats.harvestStats?.total_weight || 0).toFixed(1)} kg
          </div>
          <div className="stat-label">Total Weight</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">
            {(stats.harvestStats?.avg_weight || 0).toFixed(1)} kg
          </div>
          <div className="stat-label">Avg Weight</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">
            {(stats.harvestStats?.max_weight || 0).toFixed(1)} kg
          </div>
          <div className="stat-label">Best Harvest</div>
        </div>
      </div>

      <PhotoGallery plantId={id} />

      <div className="grid grid-2">
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h3>Harvest History</h3>
            <button 
              onClick={() => setShowHarvestForm(!showHarvestForm)}
              className="btn btn-success"
            >
              + Add Harvest
            </button>
          </div>

          {showHarvestForm && (
            <form onSubmit={handleAddHarvest} style={{ marginBottom: '1rem', padding: '1rem', backgroundColor: '#f8f9fa', borderRadius: '4px' }}>
              <div className="grid grid-2" style={{ gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Date</label>
                  <DatePicker
                    selected={harvestForm.harvest_date}
                    onChange={(date) => setHarvestForm({...harvestForm, harvest_date: date})}
                    className="form-input"
                    dateFormat="MMMM d, yyyy"
                    maxDate={new Date()}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Weight (kg)</label>
                  <input
                    type="number"
                    step="0.1"
                    value={harvestForm.weight_kg}
                    onChange={(e) => setHarvestForm({...harvestForm, weight_kg: e.target.value})}
                    className="form-input"
                    required
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Quantity</label>
                <input
                  type="number"
                  value={harvestForm.quantity}
                  onChange={(e) => setHarvestForm({...harvestForm, quantity: e.target.value})}
                  className="form-input"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Notes</label>
                <textarea
                  value={harvestForm.notes}
                  onChange={(e) => setHarvestForm({...harvestForm, notes: e.target.value})}
                  className="form-textarea"
                  rows="2"
                />
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button type="submit" className="btn btn-success">Save</button>
                <button type="button" onClick={() => setShowHarvestForm(false)} className="btn">Cancel</button>
              </div>
            </form>
          )}

          {stats.harvestHistory?.length > 0 ? (
            <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
              {stats.harvestHistory.map((harvest) => (
                <div key={harvest.id} style={{ padding: '0.75rem', borderBottom: '1px solid #ecf0f1' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <strong>{format(new Date(harvest.harvest_date), 'MMM dd, yyyy')}</strong>
                    <span style={{ color: '#27ae60', fontWeight: '600' }}>{harvest.weight_kg} kg</span>
                  </div>
                  {harvest.quantity && <div>Quantity: {harvest.quantity}</div>}
                  {harvest.notes && <div style={{ fontSize: '0.875rem', color: '#7f8c8d' }}>{harvest.notes}</div>}
                </div>
              ))}
            </div>
          ) : (
            <p>No harvests recorded yet</p>
          )}
        </div>

        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h3>Care Log</h3>
            <button 
              onClick={() => setShowCareForm(!showCareForm)}
              className="btn btn-primary"
            >
              + Add Care
            </button>
          </div>

          {showCareForm && (
            <form onSubmit={handleAddCare} style={{ marginBottom: '1rem', padding: '1rem', backgroundColor: '#f8f9fa', borderRadius: '4px' }}>
              <div className="grid grid-2" style={{ gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Care Type</label>
                  <select
                    value={careForm.care_type}
                    onChange={(e) => setCareForm({...careForm, care_type: e.target.value})}
                    className="form-select"
                  >
                    <option value="watering">Watering</option>
                    <option value="fertilizing">Fertilizing</option>
                    <option value="pruning">Pruning</option>
                    <option value="pest_control">Pest Control</option>
                    <option value="disease_treatment">Disease Treatment</option>
                    <option value="other">Other</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Date</label>
                  <DatePicker
                    selected={careForm.care_date}
                    onChange={(date) => setCareForm({...careForm, care_date: date})}
                    className="form-input"
                    dateFormat="MMMM d, yyyy"
                    maxDate={new Date()}
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Notes</label>
                <textarea
                  value={careForm.notes}
                  onChange={(e) => setCareForm({...careForm, notes: e.target.value})}
                  className="form-textarea"
                  rows="2"
                />
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button type="submit" className="btn btn-primary">Save</button>
                <button type="button" onClick={() => setShowCareForm(false)} className="btn">Cancel</button>
              </div>
            </form>
          )}

          {stats.recentCare?.length > 0 ? (
            <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
              {stats.recentCare.map((care) => (
                <div key={care.id} style={{ padding: '0.75rem', borderBottom: '1px solid #ecf0f1' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <strong>{format(new Date(care.care_date), 'MMM dd, yyyy')}</strong>
                    <span style={{ 
                      backgroundColor: '#3498db', 
                      color: 'white', 
                      padding: '0.25rem 0.5rem', 
                      borderRadius: '4px',
                      fontSize: '0.875rem'
                    }}>
                      {care.care_type.replace('_', ' ')}
                    </span>
                  </div>
                  {care.notes && <div style={{ fontSize: '0.875rem', color: '#7f8c8d', marginTop: '0.25rem' }}>{care.notes}</div>}
                </div>
              ))}
            </div>
          ) : (
            <p>No care activities recorded yet</p>
          )}
        </div>
      </div>
    </div>
  );
}

export default PlantDetail;