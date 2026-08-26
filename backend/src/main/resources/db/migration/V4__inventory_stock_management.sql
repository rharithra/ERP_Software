-- RetailFlow M3: single-location inventory balances and stock movement ledger

CREATE UNIQUE INDEX uk_products_id_tenant ON products (id, tenant_id);

CREATE TABLE inventory_balances (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    product_id          UUID NOT NULL,
    quantity            NUMERIC(19, 3) NOT NULL DEFAULT 0,
    reorder_level       NUMERIC(19, 3) NOT NULL DEFAULT 0,
    opening_recorded    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT uk_inventory_tenant_product UNIQUE (tenant_id, product_id),
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_reorder_level CHECK (reorder_level >= 0)
);

CREATE UNIQUE INDEX uk_inventory_id_tenant ON inventory_balances (id, tenant_id);
CREATE INDEX idx_inventory_tenant_id ON inventory_balances (tenant_id);
CREATE INDEX idx_inventory_product_id ON inventory_balances (product_id);

CREATE TABLE stock_movements (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    product_id          UUID NOT NULL,
    movement_type       VARCHAR(32) NOT NULL,
    quantity            NUMERIC(19, 3) NOT NULL,
    quantity_before     NUMERIC(19, 3) NOT NULL,
    quantity_after      NUMERIC(19, 3) NOT NULL,
    reference_type      VARCHAR(32),
    reference_id        UUID,
    reason              VARCHAR(64),
    notes               VARCHAR(500),
    created_by          UUID NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_movements_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT ck_movements_type CHECK (movement_type IN ('OPENING_STOCK', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')),
    CONSTRAINT ck_movements_quantity CHECK (quantity > 0),
    CONSTRAINT ck_movements_quantity_before CHECK (quantity_before >= 0),
    CONSTRAINT ck_movements_quantity_after CHECK (quantity_after >= 0)
);

CREATE INDEX idx_movements_tenant_id ON stock_movements (tenant_id);
CREATE INDEX idx_movements_product_created ON stock_movements (product_id, created_at DESC);

ALTER TABLE inventory_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_balances FORCE ROW LEVEL SECURITY;
ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements FORCE ROW LEVEL SECURITY;

CREATE POLICY inventory_balances_isolation ON inventory_balances
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY stock_movements_isolation ON stock_movements
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );
