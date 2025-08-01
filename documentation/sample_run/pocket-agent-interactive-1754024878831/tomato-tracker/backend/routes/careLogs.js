const express = require('express');
const router = express.Router();
const { db } = require('../database');

router.get('/', (req, res) => {
  const { plant_id, care_type } = req.query;
  let query = `
    SELECT c.*, p.variety, p.location 
    FROM care_logs c
    JOIN plants p ON c.plant_id = p.id
    WHERE 1=1
  `;
  const params = [];
  
  if (plant_id) {
    query += ' AND c.plant_id = ?';
    params.push(plant_id);
  }
  
  if (care_type) {
    query += ' AND c.care_type = ?';
    params.push(care_type);
  }
  
  query += ' ORDER BY c.care_date DESC';
  
  db.all(query, params, (err, rows) => {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json(rows);
  });
});

router.get('/plant/:plantId', (req, res) => {
  const query = `
    SELECT * FROM care_logs 
    WHERE plant_id = ? 
    ORDER BY care_date DESC
  `;
  
  db.all(query, [req.params.plantId], (err, rows) => {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json(rows);
  });
});

router.post('/', (req, res) => {
  const { plant_id, care_type, care_date, notes } = req.body;
  
  if (!plant_id || !care_type || !care_date) {
    res.status(400).json({ error: 'Plant ID, care type, and care date are required' });
    return;
  }
  
  const query = `
    INSERT INTO care_logs (plant_id, care_type, care_date, notes)
    VALUES (?, ?, ?, ?)
  `;
  
  db.run(query, [plant_id, care_type, care_date, notes], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json({ id: this.lastID, ...req.body });
  });
});

router.delete('/:id', (req, res) => {
  db.run('DELETE FROM care_logs WHERE id = ?', [req.params.id], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    if (this.changes === 0) {
      res.status(404).json({ error: 'Care log not found' });
      return;
    }
    res.json({ message: 'Care log deleted successfully' });
  });
});

module.exports = router;