-- RetailFlow M7: tenant user lifecycle (status on memberships) and owner audit trail

ALTER TABLE tenant_memberships
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE tenant_memberships
    ADD CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

CREATE UNIQUE INDEX uk_memberships_one_owner
    ON tenant_memberships (tenant_id)
    WHERE role = 'OWNER';

CREATE INDEX idx_memberships_tenant_status ON tenant_memberships (tenant_id, status);

CREATE TABLE user_management_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    actor_user_id   UUID NOT NULL REFERENCES users (id),
    target_user_id  UUID NOT NULL REFERENCES users (id),
    event_type      VARCHAR(32) NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_user_mgmt_event_type CHECK (event_type IN (
        'USER_CREATED',
        'USER_ROLE_CHANGED',
        'USER_DEACTIVATED',
        'USER_REACTIVATED',
        'USER_PASSWORD_RESET'
    ))
);

CREATE INDEX idx_user_mgmt_events_tenant ON user_management_events (tenant_id, created_at DESC);

ALTER TABLE user_management_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_management_events FORCE ROW LEVEL SECURITY;

CREATE POLICY user_management_events_isolation ON user_management_events
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );
