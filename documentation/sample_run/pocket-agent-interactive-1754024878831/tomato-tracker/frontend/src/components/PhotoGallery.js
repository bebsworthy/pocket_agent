import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { format } from 'date-fns';

function PhotoGallery({ plantId }) {
  const [photos, setPhotos] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [caption, setCaption] = useState('');
  const [selectedFile, setSelectedFile] = useState(null);
  const [showUploadForm, setShowUploadForm] = useState(false);

  useEffect(() => {
    fetchPhotos();
  }, [plantId]);

  const fetchPhotos = async () => {
    try {
      const response = await axios.get(`/api/photos/plant/${plantId}`);
      setPhotos(response.data);
    } catch (error) {
      console.error('Error fetching photos:', error);
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file && file.size > 5 * 1024 * 1024) {
      alert('File size must be less than 5MB');
      return;
    }
    setSelectedFile(file);
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!selectedFile) return;

    const formData = new FormData();
    formData.append('photo', selectedFile);
    formData.append('caption', caption);

    setUploading(true);
    try {
      await axios.post(`/api/photos/plant/${plantId}`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      
      setSelectedFile(null);
      setCaption('');
      setShowUploadForm(false);
      fetchPhotos();
    } catch (error) {
      console.error('Error uploading photo:', error);
      alert('Error uploading photo. Please try again.');
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (photoId) => {
    if (window.confirm('Are you sure you want to delete this photo?')) {
      try {
        await axios.delete(`/api/photos/${photoId}`);
        fetchPhotos();
      } catch (error) {
        console.error('Error deleting photo:', error);
      }
    }
  };

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h3>Photo Gallery</h3>
        <button 
          onClick={() => setShowUploadForm(!showUploadForm)}
          className="btn btn-primary"
        >
          + Add Photo
        </button>
      </div>

      {showUploadForm && (
        <form onSubmit={handleUpload} style={{ 
          marginBottom: '1rem', 
          padding: '1rem', 
          backgroundColor: '#f8f9fa', 
          borderRadius: '4px' 
        }}>
          <div className="form-group">
            <label className="form-label">Select Photo</label>
            <input
              type="file"
              accept="image/*"
              onChange={handleFileSelect}
              className="form-input"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Caption</label>
            <input
              type="text"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              className="form-input"
              placeholder="Add a caption..."
            />
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={!selectedFile || uploading}
            >
              {uploading ? 'Uploading...' : 'Upload'}
            </button>
            <button 
              type="button" 
              onClick={() => {
                setShowUploadForm(false);
                setSelectedFile(null);
                setCaption('');
              }} 
              className="btn"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {photos.length === 0 ? (
        <p>No photos yet. Add your first photo to document plant growth!</p>
      ) : (
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', 
          gap: '1rem' 
        }}>
          {photos.map((photo) => (
            <div key={photo.id} style={{ 
              position: 'relative',
              backgroundColor: '#f8f9fa',
              borderRadius: '4px',
              overflow: 'hidden'
            }}>
              <img
                src={photo.file_path}
                alt={photo.caption || 'Plant photo'}
                style={{ 
                  width: '100%', 
                  height: '200px', 
                  objectFit: 'cover',
                  cursor: 'pointer'
                }}
                onClick={() => window.open(photo.file_path, '_blank')}
              />
              <div style={{ padding: '0.5rem' }}>
                {photo.caption && (
                  <p style={{ margin: '0 0 0.5rem 0', fontSize: '0.875rem' }}>
                    {photo.caption}
                  </p>
                )}
                <div style={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'center',
                  fontSize: '0.75rem',
                  color: '#7f8c8d'
                }}>
                  <span>{format(new Date(photo.upload_date), 'MMM dd, yyyy')}</span>
                  <button
                    onClick={() => handleDelete(photo.id)}
                    className="btn btn-danger"
                    style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default PhotoGallery;