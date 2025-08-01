const express = require('express');
const router = express.Router();
const { db } = require('../database');

router.get('/', (req, res) => {
  const query = `
    SELECT p.*, 
           COUNT(DISTINCT h.id) as harvest_count,
           SUM(h.weight_kg) as total_weight_kg
    FROM plants p
    LEFT JOIN harvests h ON p.id = h.plant_id
    GROUP BY p.id
    ORDER BY p.created_at DESC
  `;
  
  db.all(query, [], (err, rows) => {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json(rows);
  });
});

router.get('/:id', (req, res) => {
  const query = `SELECT * FROM plants WHERE id = ?`;
  
  db.get(query, [req.params.id], (err, row) => {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    if (!row) {
      res.status(404).json({ error: 'Plant not found' });
      return;
    }
    res.json(row);
  });
});

router.post('/', (req, res) => {
  const { variety, planting_date, location, status, notes } = req.body;
  
  if (!variety || !planting_date) {
    res.status(400).json({ error: 'Variety and planting date are required' });
    return;
  }
  
  const query = `
    INSERT INTO plants (variety, planting_date, location, status, notes)
    VALUES (?, ?, ?, ?, ?)
  `;
  
  db.run(query, [variety, planting_date, location, status || 'active', notes], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    res.json({ id: this.lastID, ...req.body });
  });
});

router.put('/:id', (req, res) => {
  const { variety, planting_date, location, status, notes } = req.body;
  const query = `
    UPDATE plants 
    SET variety = ?, planting_date = ?, location = ?, status = ?, notes = ?,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
  `;
  
  db.run(query, [variety, planting_date, location, status, notes, req.params.id], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    if (this.changes === 0) {
      res.status(404).json({ error: 'Plant not found' });
      return;
    }
    res.json({ id: req.params.id, ...req.body });
  });
});

router.delete('/:id', (req, res) => {
  db.run('DELETE FROM plants WHERE id = ?', [req.params.id], function(err) {
    if (err) {
      res.status(500).json({ error: err.message });
      return;
    }
    if (this.changes === 0) {
      res.status(404).json({ error: 'Plant not found' });
      return;
    }
    res.json({ message: 'Plant deleted successfully' });
  });
});

module.exports = router;