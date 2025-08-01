import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';

function AddPlant() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    variety: '',
    planting_date: new Date(),
    location: '',
    notes: ''
  });

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleDateChange = (date) => {
    setFormData({
      ...formData,
      planting_date: date
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await axios.post('/api/plants', {
        ...formData,
        planting_date: formData.planting_date.toISOString().split('T')[0]
      });
      navigate('/plants');
    } catch (error) {
      console.error('Error adding plant:', error);
      alert('Error adding plant. Please try again.');
    }
  };

  return (
    <div>
      <h2 style={{ marginBottom: '2rem' }}>Add New Plant</h2>
      
      <div className="card" style={{ maxWidth: '600px' }}>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Tomato Variety *</label>
            <input
              type="text"
              name="variety"
              value={formData.variety}
              onChange={handleChange}
              className="form-input"
              placeholder="e.g., Cherry, Roma, Beefsteak"
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Planting Date *</label>
            <DatePicker
              selected={formData.planting_date}
              onChange={handleDateChange}
              className="form-input"
              dateFormat="MMMM d, yyyy"
              maxDate={new Date()}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Location</label>
            <input
              type="text"
              name="location"
              value={formData.location}
              onChange={handleChange}
              className="form-input"
              placeholder="e.g., Garden bed 1, Greenhouse, Container 3"
            />
          </div>

          <div className="form-group">
            <label className="form-label">Notes</label>
            <textarea
              name="notes"
              value={formData.notes}
              onChange={handleChange}
              className="form-textarea"
              placeholder="Any additional notes about this plant..."
            />
          </div>

          <div style={{ display: 'flex', gap: '1rem' }}>
            <button type="submit" className="btn btn-success">
              Add Plant
            </button>
            <button
              type="button"
              onClick={() => navigate('/plants')}
              className="btn"
              style={{ backgroundColor: '#95a5a6', color: 'white' }}
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default AddPlant;