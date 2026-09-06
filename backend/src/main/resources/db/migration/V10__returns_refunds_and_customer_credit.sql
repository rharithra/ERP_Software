-- RetailFlow M8: sale returns, refunds, customer credit (same Sale/Payment/Inventory engines)

ALTER TABLE stock_movements DROP CONSTRAINT ck_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_movements_type
    CHECK (movement_type IN (
        'OPENING_STOCK',
        'ADJUSTMENT_IN',
        'ADJUSTMENT_OUT',
        'PURCHASE_RECEIPT',
        'SALE',
        'SALE_RETURN'
    ));

ALTER TABLE sales DROP CONSTRAINT ck_sales_payment_status;
ALTER TABLE sales ADD CONSTRAINT ck_sales_payment_status CHECK (
    payment_status IS NULL OR payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'REFUND_DUE')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sale_items_id_tenant ON sale_items (id, tenant_id);

CREATE TABLE return_number_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    last_value  BIGINT NOT NULL
);

CREATE TABLE refund_number_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    last_value  BIGINT NOT NULL
);

CREATE TABLE sale_returns (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    return_number   VARCHAR(32) NOT NULL,
    sale_id         UUID NOT NULL,
    customer_id     UUID,
    status          VARCHAR(16) NOT NULL,
    return_date     DATE NOT NULL,
    reason          VARCHAR(32),
    notes           VARCHAR(2000),
    subtotal        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_by      UUID NOT NULL REFERENCES users (id),
    completed_at    TIMESTAMPTZ,
    completed_by    UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_sale_returns_tenant_number UNIQUE (tenant_id, return_number),
    CONSTRAINT uk_sale_returns_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_sale_returns_sale_same_tenant
        FOREIGN KEY (sale_id, tenant_id) REFERENCES sales (id, tenant_id),
    CONSTRAINT fk_sale_returns_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_sale_returns_status CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sale_returns_reason CHECK (reason IS NULL OR reason IN (
        'DAMAGED', 'DEFECTIVE', 'WRONG_PRODUCT', 'CUSTOMER_CHANGED_MIND', 'QUALITY_ISSUE', 'NOT_REQUIRED', 'OTHER'
    )),
    CONSTRAINT ck_sale_returns_amounts CHECK (
        subtotal >= 0 AND discount >= 0 AND tax_amount >= 0 AND total_amount >= 0
    )
);

CREATE INDEX idx_sale_returns_tenant_id ON sale_returns (tenant_id);
CREATE INDEX idx_sale_returns_sale_id ON sale_returns (tenant_id, sale_id);
CREATE INDEX idx_sale_returns_status ON sale_returns (tenant_id, status);
CREATE INDEX idx_sale_returns_date ON sale_returns (tenant_id, return_date);

CREATE TABLE sale_return_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    sale_return_id  UUID NOT NULL,
    sale_item_id    UUID NOT NULL,
    product_id      UUID NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    sku             VARCHAR(64) NOT NULL,
    barcode         VARCHAR(64),
    unit            VARCHAR(16) NOT NULL,
    quantity        NUMERIC(19, 3) NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    gst_rate        NUMERIC(4, 2) NOT NULL,
    discount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    taxable_amount  NUMERIC(14, 2) NOT NULL,
    tax_amount      NUMERIC(14, 2) NOT NULL,
    total_amount    NUMERIC(14, 2) NOT NULL,
    reason          VARCHAR(32),
    CONSTRAINT fk_sale_return_items_return_same_tenant
        FOREIGN KEY (sale_return_id, tenant_id) REFERENCES sale_returns (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_return_items_sale_item_same_tenant
        FOREIGN KEY (sale_item_id, tenant_id) REFERENCES sale_items (id, tenant_id),
    CONSTRAINT fk_sale_return_items_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT ck_sale_return_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sale_return_items_amounts CHECK (
        unit_price >= 0 AND discount >= 0 AND taxable_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0
    ),
    CONSTRAINT ck_sale_return_items_reason CHECK (reason IS NULL OR reason IN (
        'DAMAGED', 'DEFECTIVE', 'WRONG_PRODUCT', 'CUSTOMER_CHANGED_MIND', 'QUALITY_ISSUE', 'NOT_REQUIRED', 'OTHER'
    ))
);

CREATE INDEX idx_sale_return_items_tenant_id ON sale_return_items (tenant_id);
CREATE INDEX idx_sale_return_items_return_id ON sale_return_items (sale_return_id);
CREATE INDEX idx_sale_return_items_sale_item ON sale_return_items (sale_item_id);

CREATE TABLE refunds (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    refund_number       VARCHAR(32) NOT NULL,
    sale_id             UUID NOT NULL,
    sale_return_id      UUID,
    customer_id         UUID,
    amount              NUMERIC(14, 2) NOT NULL,
    payment_method      VARCHAR(32) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    reference_number    VARCHAR(64),
    notes               VARCHAR(2000),
    created_by          UUID NOT NULL REFERENCES users (id),
    completed_at        TIMESTAMPTZ,
    completed_by        UUID REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_refunds_tenant_number UNIQUE (tenant_id, refund_number),
    CONSTRAINT uk_refunds_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_refunds_sale_same_tenant
        FOREIGN KEY (sale_id, tenant_id) REFERENCES sales (id, tenant_id),
    CONSTRAINT fk_refunds_return_same_tenant
        FOREIGN KEY (sale_return_id, tenant_id) REFERENCES sale_returns (id, tenant_id),
    CONSTRAINT fk_refunds_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_refunds_method CHECK (payment_method IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER'))
);

CREATE INDEX idx_refunds_tenant_id ON refunds (tenant_id);
CREATE INDEX idx_refunds_sale_id ON refunds (tenant_id, sale_id);
CREATE INDEX idx_refunds_status ON refunds (tenant_id, status);

CREATE TABLE customer_credit_transactions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    customer_id         UUID NOT NULL,
    transaction_type    VARCHAR(32) NOT NULL,
    amount              NUMERIC(14, 2) NOT NULL,
    sale_id             UUID,
    source_sale_id      UUID,
    sale_return_id      UUID,
    refund_id           UUID,
    reference           VARCHAR(200),
    created_by          UUID NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_credit_tx_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT fk_credit_tx_sale_same_tenant
        FOREIGN KEY (sale_id, tenant_id) REFERENCES sales (id, tenant_id),
    CONSTRAINT fk_credit_tx_source_sale_same_tenant
        FOREIGN KEY (source_sale_id, tenant_id) REFERENCES sales (id, tenant_id),
    CONSTRAINT fk_credit_tx_return_same_tenant
        FOREIGN KEY (sale_return_id, tenant_id) REFERENCES sale_returns (id, tenant_id),
    CONSTRAINT fk_credit_tx_refund_same_tenant
        FOREIGN KEY (refund_id, tenant_id) REFERENCES refunds (id, tenant_id),
    CONSTRAINT ck_credit_tx_type CHECK (transaction_type IN (
        'CREDIT_CREATED', 'CREDIT_APPLIED', 'CREDIT_REFUNDED', 'ADJUSTMENT'
    )),
    CONSTRAINT ck_credit_tx_amount CHECK (amount > 0)
);

CREATE INDEX idx_credit_tx_tenant_customer ON customer_credit_transactions (tenant_id, customer_id, created_at);
CREATE INDEX idx_credit_tx_sale ON customer_credit_transactions (tenant_id, sale_id);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'return_number_counters',
        'refund_number_counters',
        'sale_returns',
        'sale_return_items',
        'refunds',
        'customer_credit_transactions'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I
                USING (
                    current_setting(''app.bypass_rls'', true) = ''on''
                    OR tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid
                )
                WITH CHECK (
                    current_setting(''app.bypass_rls'', true) = ''on''
                    OR tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid
                )',
            t || '_isolation',
            t
        );
    END LOOP;
END $$;
