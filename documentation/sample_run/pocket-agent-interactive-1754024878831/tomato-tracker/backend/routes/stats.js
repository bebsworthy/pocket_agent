const express = require('express');
const router = express.Router();
const { db } = require('../database');

router.get('/overview', (req, res) => {
  const queries = {
    totalPlants: `SELECT COUNT(*) as count FROM plants WHERE status = 'active'`,
    totalHarvests: `SELECT COUNT(*) as count, SUM(weight_kg) as total_weight FROM harvests`,
    recentHarvests: `
      SELECT h.*, p.variety 
      FROM harvests h
      JOIN plants p ON h.plant_id = p.id
      ORDER BY h.harvest_date DESC
      LIMIT 5
    `,
    topProducers: `
      SELECT p.*, COUNT(h.id) as harvest_count, SUM(h.weight_kg) as total_weight
      FROM plants p
      LEFT JOIN harvests h ON p.id = h.plant_id
      WHERE p.status = 'active'
      GROUP BY p.id
      ORDER BY total_weight DESC
      LIMIT 5
    `,
    monthlyHarvests: `
      SELECT 
        strftime('%Y-%m', harvest_date) as month,
        COUNT(*) as harvest_count,
        SUM(weight_kg) as total_weight,
        AVG(weight_kg) as avg_weight
      FROM harvests
      WHERE harvest_date >= date('now', '-12 months')
      GROUP BY month
      ORDER BY month DESC
    `
  };
  
  const results = {};
  let completed = 0;
  const totalQueries = Object.keys(queries).length;
  
  Object.entries(queries).forEach(([key, query]) => {
    db.all(query, [], (err, rows) => {
      if (err) {
        res.status(500).json({ error: err.message });
        return;
      }
      
      results[key] = rows.length === 1 && key.includes('total') ? rows[0] : rows;
      completed++;
      
      if (completed === totalQueries) {
        res.json(results);
      }
    });
  });
});

router.get('/plant/:plantId', (req, res) => {
  const plantId = req.params.plantId;
  
  const queries = {
    plantInfo: `SELECT * FROM plants WHERE id = ?`,
    harvestStats: `
      SELECT 
        COUNT(*) as total_harvests,
        SUM(weight_kg) as total_weight,
        AVG(weight_kg) as avg_weight,
        MAX(weight_kg) as max_weight,
        MIN(weight_kg) as min_weight
      FROM harvests
      WHERE plant_id = ?
    `,
    recentCare: `
      SELECT * FROM care_logs
      WHERE plant_id = ?
      ORDER BY care_date DESC
      LIMIT 10
    `,
    harvestHistory: `
      SELECT * FROM harvests
      WHERE plant_id = ?
      ORDER BY harvest_date DESC
    `
  };
  
  const results = {};
  let completed = 0;
  const totalQueries = Object.keys(queries).length;
  
  Object.entries(queries).forEach(([key, query]) => {
    const method = key === 'plantInfo' || key === 'harvestStats' ? 'get' : 'all';
    db[method](query, [plantId], (err, rows) => {
      if (err) {
        res.status(500).json({ error: err.message });
        return;
      }
      
      results[key] = rows;
      completed++;
      
      if (completed === totalQueries) {
        if (!results.plantInfo) {
          res.status(404).json({ error: 'Plant not found' });
          return;
        }
        res.json(results);
      }
    });
  });
});

module.exports = router;