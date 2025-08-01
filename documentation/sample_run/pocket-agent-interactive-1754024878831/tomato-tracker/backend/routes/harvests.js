const express = require('express');
const router = express.Router();
const { db } = require('../database');

router.get('/', (req, res) => {
  const { plant_id, start_date, end_date } = req.query;
  let query = `
    SELECT h.*, p.variety, p.location 
    FROM harvests h
    JOIN plants p ON h.plant_id = p.id
    WHERE 1=1
  `;
  const params = [];
  
  if (plant_id) {
    query += ' AND h.plant_id = ?';
    params.push(plant_id);
  }
  
  if (start_date) {
    query += ' AND h.harvest_date >= ?';
    params.push(start_date);
  }
  
  if (end_date) {
    query += ' AND h.harvest_date <= ?';
    params.push(end_date);
  }
  
  query += ' ORDER BY h.harvest_date DESC';
  
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
    SELECT * FROM harvests 
    WHERE plant_id = ? 
    ORDER BY harvest_date DESC
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
  const { plant_id, harvest_date, quantity, weight_kg, notes } = req.body;
  
  if (!plant_id || !harvest_date) {
    res.status(400).json({ error: 'Plant ID and harvest date are required' });
    return;
  }
  
  const query = `
    INSERT INTO harvests (plant_id, harvest_date, quantity, weight_kg, notes)
    VALUES (?, ?, ?, ?, ?)
  `;
  
  db.run(query, [plant_id, harvest_date, quantity, weight_kg, notes], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json({ id: this.lastID, ...req.body });
  });
});

router.put('/:id', (req, res) => {
  const { harvest_date, quantity, weight_kg, notes } = req.body;
  const query = `
    UPDATE harvests 
    SET harvest_date = ?, quantity = ?, weight_kg = ?, notes = ?
    WHERE id = ?
  `;
  
  db.run(query, [harvest_date, quantity, weight_kg, notes, req.params.id], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    if (this.changes === 0) {
      res.status(404).json({ error: 'Harvest not found' });
      return;
    }
    res.json({ id: req.params.id, ...req.body });
  });
});

router.delete('/:id', (req, res) => {
  db.run('DELETE FROM harvests WHERE id = ?', [req.params.id], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    if (this.changes === 0) {
      res.status(404).json({ error: 'Harvest not found' });
      return;
    }
    res.json({ message: 'Harvest deleted successfully' });
  });
});

module.exports = router;