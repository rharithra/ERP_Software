-- RetailFlow M5: customers, sales, invoices, SALE stock movements

ALTER TABLE stock_movements DROP CONSTRAINT ck_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_movements_type
    CHECK (movement_type IN (
        'OPENING_STOCK',
        'ADJUSTMENT_IN',
        'ADJUSTMENT_OUT',
        'PURCHASE_RECEIPT',
        'SALE'
    ));

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    name            VARCHAR(200) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(320),
    address         VARCHAR(500),
    gstin           VARCHAR(15),
    notes           VARCHAR(1000),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_customers_id_tenant ON customers (id, tenant_id);
CREATE INDEX idx_customers_tenant_id ON customers (tenant_id);
CREATE UNIQUE INDEX uk_customers_tenant_phone
    ON customers (tenant_id, phone)
    WHERE phone IS NOT NULL AND btrim(phone) <> '';

CREATE TABLE sale_number_counters (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants (id),
    last_value      BIGINT NOT NULL
);

CREATE TABLE invoice_number_counters (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants (id),
    last_value      BIGINT NOT NULL
);

CREATE TABLE sales (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    customer_id         UUID,
    sale_number         VARCHAR(32) NOT NULL,
    invoice_number      VARCHAR(32),
    sale_date           DATE NOT NULL,
    status              VARCHAR(16) NOT NULL,
    subtotal            NUMERIC(14, 2) NOT NULL,
    discount            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(14, 2) NOT NULL,
    grand_total         NUMERIC(14, 2) NOT NULL,
    payment_method      VARCHAR(16),
    payment_status      VARCHAR(16),
    notes               VARCHAR(1000),
    customer_name       VARCHAR(200) NOT NULL,
    customer_phone      VARCHAR(20),
    customer_gstin      VARCHAR(15),
    company_name        VARCHAR(200),
    company_gstin       VARCHAR(15),
    company_address     VARCHAR(500),
    company_phone       VARCHAR(20),
    completed_at        TIMESTAMPTZ,
    created_by          UUID NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sales_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT uk_sales_tenant_number UNIQUE (tenant_id, sale_number),
    CONSTRAINT ck_sales_status CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sales_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_sales_discount CHECK (discount >= 0),
    CONSTRAINT ck_sales_tax CHECK (tax_total >= 0),
    CONSTRAINT ck_sales_total CHECK (grand_total >= 0),
    CONSTRAINT ck_sales_discount_lte_subtotal CHECK (discount <= subtotal),
    CONSTRAINT ck_sales_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'UPI', 'CARD', 'OTHER')
    ),
    CONSTRAINT ck_sales_payment_status CHECK (
        payment_status IS NULL OR payment_status IN ('PAID')
    )
);

CREATE UNIQUE INDEX uk_sales_id_tenant ON sales (id, tenant_id);
CREATE UNIQUE INDEX uk_sales_tenant_invoice
    ON sales (tenant_id, invoice_number)
    WHERE invoice_number IS NOT NULL;
CREATE INDEX idx_sales_tenant_id ON sales (tenant_id);
CREATE INDEX idx_sales_customer_id ON sales (customer_id);
CREATE INDEX idx_sales_status ON sales (tenant_id, status);
CREATE INDEX idx_sales_date ON sales (tenant_id, sale_date);

CREATE TABLE sale_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    sale_id         UUID NOT NULL,
    product_id      UUID NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    sku             VARCHAR(64) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    quantity        NUMERIC(19, 3) NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    gst_rate        NUMERIC(4, 2) NOT NULL,
    discount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    taxable_amount  NUMERIC(14, 2) NOT NULL,
    tax_amount      NUMERIC(14, 2) NOT NULL,
    line_total      NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_sale_items_sale_same_tenant
        FOREIGN KEY (sale_id, tenant_id) REFERENCES sales (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_items_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT ck_sale_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sale_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_sale_items_discount CHECK (discount >= 0),
    CONSTRAINT ck_sale_items_gst CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    CONSTRAINT ck_sale_items_taxable CHECK (taxable_amount >= 0),
    CONSTRAINT ck_sale_items_tax CHECK (tax_amount >= 0),
    CONSTRAINT ck_sale_items_total CHECK (line_total >= 0)
);

CREATE INDEX idx_sale_items_tenant_id ON sale_items (tenant_id);
CREATE INDEX idx_sale_items_sale_id ON sale_items (sale_id);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;
ALTER TABLE sale_number_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_number_counters FORCE ROW LEVEL SECURITY;
ALTER TABLE invoice_number_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_number_counters FORCE ROW LEVEL SECURITY;
ALTER TABLE sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales FORCE ROW LEVEL SECURITY;
ALTER TABLE sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_items FORCE ROW LEVEL SECURITY;

CREATE POLICY customers_isolation ON customers
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY sale_counters_isolation ON sale_number_counters
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY invoice_counters_isolation ON invoice_number_counters
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY sales_isolation ON sales
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY sale_items_isolation ON sale_items
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );
