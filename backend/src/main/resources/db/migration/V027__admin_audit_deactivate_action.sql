-- Allow the DEACTIVATE administrative action in the audit trail.
ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action CHECK (action IN (
    'ACTIVATE', 'DEACTIVATE', 'DISABLE', 'ENABLE', 'RESET_PASSWORD',
    'BUILTIN_CONNECTION_CREATE', 'BUILTIN_CONNECTION_UPDATE', 'BUILTIN_CONNECTION_DELETE',
    'ALLOCATE', 'REVOKE', 'DELETE_USER'
));
