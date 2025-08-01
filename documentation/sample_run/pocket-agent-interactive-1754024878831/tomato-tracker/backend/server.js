const express = require('express');
const cors = require('cors');
const path = require('path');
const multer = require('multer');
require('dotenv').config();

const db = require('./database');
const plantsRouter = require('./routes/plants');
const harvestsRouter = require('./routes/harvests');
const careLogsRouter = require('./routes/careLogs');
const statsRouter = require('./routes/stats');
const photosRouter = require('./routes/photos');

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

app.use('/api/plants', plantsRouter);
app.use('/api/harvests', harvestsRouter);
app.use('/api/care-logs', careLogsRouter);
app.use('/api/stats', statsRouter);
app.use('/api/photos', photosRouter);

app.get('/api/health', (req, res) => {
  res.json({ status: 'OK', message: 'Tomato Tracker API is running' });
});

db.initializeDatabase().then(() => {
  app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
  });
}).catch(err => {
  console.error('Failed to initialize database:', err);
  process.exit(1);
});