-- export_logs.tenant_id 允许 NULL（super_admin 平台级导出无具体小区）
ALTER TABLE export_logs ALTER COLUMN tenant_id DROP NOT NULL;
