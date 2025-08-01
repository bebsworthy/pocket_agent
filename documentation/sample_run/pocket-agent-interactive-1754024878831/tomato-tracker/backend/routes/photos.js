const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { db } = require('../database');

// Ensure uploads directory exists
const uploadsDir = path.join(__dirname, '../uploads');
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadsDir);
  },
  filename: function (req, file, cb) {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'plant-' + req.params.plantId + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({ 
  storage: storage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB limit
  },
  fileFilter: function (req, file, cb) {
    const allowedTypes = /jpeg|jpg|png|gif/;
    const extname = allowedTypes.test(path.extname(file.originalname).toLowerCase());
    const mimetype = allowedTypes.test(file.mimetype);
    
    if (mimetype && extname) {
      return cb(null, true);
    } else {
      cb(new Error('Only image files are allowed'));
    }
  }
});

// Get all photos for a plant
router.get('/plant/:plantId', (req, res) => {
  const query = `
    SELECT * FROM photos 
    WHERE plant_id = ? 
    ORDER BY upload_date DESC
  `;
  
  db.all(query, [req.params.plantId], (err, rows) => {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json(rows);
  });
});

// Upload a photo for a plant
router.post('/plant/:plantId', upload.single('photo'), (req, res) => {
  if (!req.file) {
    res.status(400).json({ error: 'No file uploaded' });
    return;
  }
  
  const { caption } = req.body;
  const filePath = `/uploads/${req.file.filename}`;
  
  const query = `
    INSERT INTO photos (plant_id, file_path, caption)
    VALUES (?, ?, ?)
  `;
  
  db.run(query, [req.params.plantId, filePath, caption], function(err) {
    if (err) {
      // Delete uploaded file if database insert fails
      fs.unlinkSync(req.file.path);
      res.status(500).json({ error: err.message });
      return;
    }
    
    res.json({
      id: this.lastID,
      plant_id: req.params.plantId,
      file_path: filePath,
      caption: caption,
      upload_date: new Date().toISOString()
    });
  });
});

// Delete a photo
router.delete('/:id', (req, res) => {
  // First get the file path
  db.get('SELECT file_path FROM photos WHERE id = ?', [req.params.id], (err, row) => {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    
    if (!row) {
      res.status(404).json({ error: 'Photo not found' });
      return;
    }
    
    // Delete from database
    db.run('DELETE FROM photos WHERE id = ?', [req.params.id], function(err) {
      if (err) {
        res.status(500).json({ error: err.message });
        return;
      }
      
      // Delete file from filesystem
      const fullPath = path.join(__dirname, '..', row.file_path);
      if (fs.existsSync(fullPath)) {
        fs.unlinkSync(fullPath);
      }
      
      res.json({ message: 'Photo deleted successfully' });
    });
  });
});

module.exports = router;