-- RetailFlow M4: suppliers, purchases, purchase items, purchase receipt movements

ALTER TABLE stock_movements DROP CONSTRAINT ck_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_movements_type
    CHECK (movement_type IN ('OPENING_STOCK', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'PURCHASE_RECEIPT'));

CREATE TABLE suppliers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    name            VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(150),
    phone           VARCHAR(20),
    email           VARCHAR(320),
    address         VARCHAR(500),
    gstin           VARCHAR(15),
    notes           VARCHAR(1000),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_suppliers_id_tenant ON suppliers (id, tenant_id);
CREATE UNIQUE INDEX uk_suppliers_tenant_name_lower ON suppliers (tenant_id, LOWER(name));
CREATE INDEX idx_suppliers_tenant_id ON suppliers (tenant_id);

CREATE TABLE purchase_number_counters (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants (id),
    last_value      BIGINT NOT NULL
);

CREATE TABLE purchases (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants (id),
    supplier_id       UUID NOT NULL,
    purchase_number   VARCHAR(32) NOT NULL,
    purchase_date     DATE NOT NULL,
    status            VARCHAR(16) NOT NULL,
    subtotal          NUMERIC(14, 2) NOT NULL,
    tax_amount        NUMERIC(14, 2) NOT NULL,
    total_amount      NUMERIC(14, 2) NOT NULL,
    notes             VARCHAR(1000),
    received_at       TIMESTAMPTZ,
    created_by        UUID NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_purchases_supplier_same_tenant
        FOREIGN KEY (supplier_id, tenant_id) REFERENCES suppliers (id, tenant_id),
    CONSTRAINT uk_purchases_tenant_number UNIQUE (tenant_id, purchase_number),
    CONSTRAINT ck_purchases_status CHECK (status IN ('DRAFT', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT ck_purchases_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_purchases_tax CHECK (tax_amount >= 0),
    CONSTRAINT ck_purchases_total CHECK (total_amount >= 0)
);

CREATE UNIQUE INDEX uk_purchases_id_tenant ON purchases (id, tenant_id);
CREATE INDEX idx_purchases_tenant_id ON purchases (tenant_id);
CREATE INDEX idx_purchases_supplier_id ON purchases (supplier_id);
CREATE INDEX idx_purchases_status ON purchases (tenant_id, status);

CREATE TABLE purchase_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    purchase_id     UUID NOT NULL,
    product_id      UUID NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    sku             VARCHAR(64) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    quantity        NUMERIC(19, 3) NOT NULL,
    unit_cost       NUMERIC(12, 2) NOT NULL,
    gst_rate        NUMERIC(4, 2) NOT NULL,
    line_subtotal   NUMERIC(14, 2) NOT NULL,
    tax_amount      NUMERIC(14, 2) NOT NULL,
    line_total      NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_purchase_items_purchase_same_tenant
        FOREIGN KEY (purchase_id, tenant_id) REFERENCES purchases (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_items_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT ck_purchase_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_items_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_purchase_items_gst CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    CONSTRAINT ck_purchase_items_subtotal CHECK (line_subtotal >= 0),
    CONSTRAINT ck_purchase_items_tax CHECK (tax_amount >= 0),
    CONSTRAINT ck_purchase_items_total CHECK (line_total >= 0)
);

CREATE INDEX idx_purchase_items_tenant_id ON purchase_items (tenant_id);
CREATE INDEX idx_purchase_items_purchase_id ON purchase_items (purchase_id);

ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE suppliers FORCE ROW LEVEL SECURITY;
ALTER TABLE purchase_number_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_number_counters FORCE ROW LEVEL SECURITY;
ALTER TABLE purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchases FORCE ROW LEVEL SECURITY;
ALTER TABLE purchase_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_items FORCE ROW LEVEL SECURITY;

CREATE POLICY suppliers_isolation ON suppliers
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY purchase_counters_isolation ON purchase_number_counters
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY purchases_isolation ON purchases
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY purchase_items_isolation ON purchase_items
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );
