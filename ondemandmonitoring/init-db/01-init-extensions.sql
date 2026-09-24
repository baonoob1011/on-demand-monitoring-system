-- Enable PostGIS and pgvector extensions automatically on DB init
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;
