-- Final Clean Slate Migration
-- Combines: Device-Only Identity + Auto-Update Trigger
-- Use this script to completely reset the table and apply all best practices.

-- 1. Drop existing table
DROP TABLE IF EXISTS user_devices;

-- 2. Create table (Device-Centric)
CREATE TABLE user_devices (
    device_id text NOT NULL PRIMARY KEY, -- Android ID
    device_name text NOT NULL,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

-- 3. Enable RLS
ALTER TABLE user_devices ENABLE ROW LEVEL SECURITY;

-- 4. Create Policies
-- Allow any authenticated user (Anonymous Token) to operate on the table.
-- Security relies on the uniqueness of device_id (PK).
CREATE POLICY "Public access for authenticated devices"
ON user_devices
FOR ALL -- Covers SELECT, INSERT, UPDATE, DELETE
TO authenticated
USING (true)
WITH CHECK (true);

-- 5. Auto-Update Timestamp Logic

-- Create Function (if not exists, or replace)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create Trigger
-- Fires before every UPDATE to ensure updated_at is fresh.
DROP TRIGGER IF EXISTS update_user_devices_updated_at ON user_devices;

CREATE TRIGGER update_user_devices_updated_at
BEFORE UPDATE ON user_devices
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();