-- RetailFlow M6: sales pipeline CRM + payment ledger (same Sale/Invoice/Inventory engine)

ALTER TABLE sales DROP CONSTRAINT ck_sales_payment_status;
ALTER TABLE sales ADD CONSTRAINT ck_sales_payment_status CHECK (
    payment_status IS NULL OR payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')
);

ALTER TABLE sales DROP CONSTRAINT ck_sales_payment_method;
ALTER TABLE sales ADD CONSTRAINT ck_sales_payment_method CHECK (
    payment_method IS NULL OR payment_method IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER')
);

CREATE TABLE lead_number_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    last_value  BIGINT NOT NULL
);

CREATE TABLE quotation_number_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    last_value  BIGINT NOT NULL
);

CREATE TABLE sales_order_number_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    last_value  BIGINT NOT NULL
);

CREATE TABLE payment_number_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    last_value  BIGINT NOT NULL
);

CREATE TABLE leads (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES tenants (id),
    lead_number             VARCHAR(32) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    phone                   VARCHAR(20),
    email                   VARCHAR(320),
    company_name            VARCHAR(200),
    address                 VARCHAR(500),
    source                  VARCHAR(32) NOT NULL,
    requirement             VARCHAR(2000),
    expected_value          NUMERIC(14, 2),
    expected_close_date     DATE,
    assigned_to             UUID REFERENCES users (id),
    priority                VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status                  VARCHAR(16) NOT NULL,
    notes                   VARCHAR(2000),
    lost_reason             VARCHAR(1000),
    converted_customer_id   UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_leads_tenant_number UNIQUE (tenant_id, lead_number),
    CONSTRAINT uk_leads_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_leads_customer_same_tenant
        FOREIGN KEY (converted_customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_leads_source CHECK (source IN (
        'WALK_IN', 'PHONE', 'WEBSITE', 'REFERRAL', 'SOCIAL_MEDIA', 'ADVERTISEMENT', 'EXISTING_CUSTOMER', 'OTHER'
    )),
    CONSTRAINT ck_leads_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_leads_status CHECK (status IN (
        'NEW', 'CONTACTED', 'QUALIFIED', 'QUOTATION', 'NEGOTIATION', 'WON', 'LOST'
    )),
    CONSTRAINT ck_leads_expected_value CHECK (expected_value IS NULL OR expected_value >= 0)
);

CREATE INDEX idx_leads_tenant_id ON leads (tenant_id);
CREATE INDEX idx_leads_status ON leads (tenant_id, status);
CREATE INDEX idx_leads_assigned ON leads (tenant_id, assigned_to);

CREATE TABLE follow_ups (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    lead_id         UUID NOT NULL,
    customer_id     UUID,
    type            VARCHAR(16) NOT NULL,
    due_date        DATE NOT NULL,
    due_time        TIME,
    assigned_to     UUID REFERENCES users (id),
    status          VARCHAR(16) NOT NULL,
    notes           VARCHAR(2000),
    outcome         VARCHAR(2000),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_follow_ups_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_follow_ups_lead_same_tenant
        FOREIGN KEY (lead_id, tenant_id) REFERENCES leads (id, tenant_id),
    CONSTRAINT fk_follow_ups_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_follow_ups_type CHECK (type IN ('CALL', 'VISIT', 'WHATSAPP', 'EMAIL', 'MEETING', 'OTHER')),
    CONSTRAINT ck_follow_ups_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_follow_ups_tenant_id ON follow_ups (tenant_id);
CREATE INDEX idx_follow_ups_lead_id ON follow_ups (lead_id);
CREATE INDEX idx_follow_ups_due ON follow_ups (tenant_id, due_date, status);

CREATE TABLE quotations (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES tenants (id),
    quotation_number        VARCHAR(32) NOT NULL,
    lead_id                 UUID,
    customer_id             UUID NOT NULL,
    quotation_date          DATE NOT NULL,
    valid_until             DATE NOT NULL,
    status                  VARCHAR(16) NOT NULL,
    subtotal                NUMERIC(14, 2) NOT NULL,
    discount                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_total               NUMERIC(14, 2) NOT NULL,
    grand_total             NUMERIC(14, 2) NOT NULL,
    notes                   VARCHAR(2000),
    terms_and_conditions    VARCHAR(4000),
    created_by              UUID NOT NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_quotations_tenant_number UNIQUE (tenant_id, quotation_number),
    CONSTRAINT uk_quotations_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_quotations_lead_same_tenant
        FOREIGN KEY (lead_id, tenant_id) REFERENCES leads (id, tenant_id),
    CONSTRAINT fk_quotations_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_quotations_status CHECK (status IN (
        'DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED'
    )),
    CONSTRAINT ck_quotations_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_quotations_discount CHECK (discount >= 0 AND discount <= subtotal),
    CONSTRAINT ck_quotations_tax CHECK (tax_total >= 0),
    CONSTRAINT ck_quotations_total CHECK (grand_total >= 0)
);

CREATE INDEX idx_quotations_tenant_id ON quotations (tenant_id);
CREATE INDEX idx_quotations_lead_id ON quotations (lead_id);
CREATE INDEX idx_quotations_customer_id ON quotations (customer_id);

CREATE TABLE quotation_items (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES tenants (id),
    quotation_id            UUID NOT NULL,
    product_id              UUID NOT NULL,
    product_name_snapshot   VARCHAR(200) NOT NULL,
    sku_snapshot            VARCHAR(64) NOT NULL,
    unit_snapshot           VARCHAR(16) NOT NULL,
    quantity                NUMERIC(19, 3) NOT NULL,
    unit_price              NUMERIC(12, 2) NOT NULL,
    discount                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    gst_rate                NUMERIC(4, 2) NOT NULL,
    taxable_amount          NUMERIC(14, 2) NOT NULL,
    tax_amount              NUMERIC(14, 2) NOT NULL,
    line_total              NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_quotation_items_quotation_same_tenant
        FOREIGN KEY (quotation_id, tenant_id) REFERENCES quotations (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_quotation_items_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT ck_quotation_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_quotation_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_quotation_items_discount CHECK (discount >= 0),
    CONSTRAINT ck_quotation_items_gst CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    CONSTRAINT ck_quotation_items_taxable CHECK (taxable_amount >= 0),
    CONSTRAINT ck_quotation_items_tax CHECK (tax_amount >= 0),
    CONSTRAINT ck_quotation_items_total CHECK (line_total >= 0)
);

CREATE INDEX idx_quotation_items_quotation_id ON quotation_items (quotation_id);

CREATE TABLE sales_orders (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES tenants (id),
    order_number            VARCHAR(32) NOT NULL,
    quotation_id            UUID,
    lead_id                 UUID,
    customer_id             UUID NOT NULL,
    sale_id                 UUID,
    order_date              DATE NOT NULL,
    expected_delivery_date  DATE,
    status                  VARCHAR(16) NOT NULL,
    payment_status          VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    subtotal                NUMERIC(14, 2) NOT NULL,
    discount                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_total               NUMERIC(14, 2) NOT NULL,
    grand_total             NUMERIC(14, 2) NOT NULL,
    advance_paid            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    outstanding_amount      NUMERIC(14, 2) NOT NULL,
    notes                   VARCHAR(2000),
    created_by              UUID NOT NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_sales_orders_tenant_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uk_sales_orders_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_sales_orders_quotation_same_tenant
        FOREIGN KEY (quotation_id, tenant_id) REFERENCES quotations (id, tenant_id),
    CONSTRAINT fk_sales_orders_lead_same_tenant
        FOREIGN KEY (lead_id, tenant_id) REFERENCES leads (id, tenant_id),
    CONSTRAINT fk_sales_orders_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_sales_orders_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'PROCESSING', 'READY', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ck_sales_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')),
    CONSTRAINT ck_sales_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_sales_orders_discount CHECK (discount >= 0 AND discount <= subtotal),
    CONSTRAINT ck_sales_orders_tax CHECK (tax_total >= 0),
    CONSTRAINT ck_sales_orders_total CHECK (grand_total >= 0),
    CONSTRAINT ck_sales_orders_advance CHECK (advance_paid >= 0),
    CONSTRAINT ck_sales_orders_outstanding CHECK (outstanding_amount >= 0)
);

CREATE INDEX idx_sales_orders_tenant_id ON sales_orders (tenant_id);
CREATE INDEX idx_sales_orders_customer_id ON sales_orders (customer_id);

CREATE TABLE sales_order_items (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES tenants (id),
    sales_order_id          UUID NOT NULL,
    product_id              UUID NOT NULL,
    product_name_snapshot   VARCHAR(200) NOT NULL,
    sku_snapshot            VARCHAR(64) NOT NULL,
    unit_snapshot           VARCHAR(16) NOT NULL,
    quantity                NUMERIC(19, 3) NOT NULL,
    unit_price              NUMERIC(12, 2) NOT NULL,
    discount                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    gst_rate                NUMERIC(4, 2) NOT NULL,
    taxable_amount          NUMERIC(14, 2) NOT NULL,
    tax_amount              NUMERIC(14, 2) NOT NULL,
    line_total              NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_so_items_order_same_tenant
        FOREIGN KEY (sales_order_id, tenant_id) REFERENCES sales_orders (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_so_items_product_same_tenant
        FOREIGN KEY (product_id, tenant_id) REFERENCES products (id, tenant_id),
    CONSTRAINT ck_so_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_so_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_so_items_discount CHECK (discount >= 0),
    CONSTRAINT ck_so_items_gst CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    CONSTRAINT ck_so_items_taxable CHECK (taxable_amount >= 0),
    CONSTRAINT ck_so_items_tax CHECK (tax_amount >= 0),
    CONSTRAINT ck_so_items_total CHECK (line_total >= 0)
);

CREATE INDEX idx_so_items_order_id ON sales_order_items (sales_order_id);

ALTER TABLE sales ADD COLUMN sales_order_id UUID;
ALTER TABLE sales ADD CONSTRAINT fk_sales_order_same_tenant
    FOREIGN KEY (sales_order_id, tenant_id) REFERENCES sales_orders (id, tenant_id);
CREATE UNIQUE INDEX uk_sales_sales_order ON sales (sales_order_id) WHERE sales_order_id IS NOT NULL;

ALTER TABLE sales_orders ADD CONSTRAINT fk_sales_orders_sale_same_tenant
    FOREIGN KEY (sale_id, tenant_id) REFERENCES sales (id, tenant_id);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    payment_number      VARCHAR(32) NOT NULL,
    sale_id             UUID,
    sales_order_id      UUID,
    customer_id         UUID,
    amount              NUMERIC(14, 2) NOT NULL,
    payment_method      VARCHAR(16) NOT NULL,
    payment_date        DATE NOT NULL,
    reference_number    VARCHAR(64),
    notes               VARCHAR(1000),
    created_by          UUID NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_payments_tenant_number UNIQUE (tenant_id, payment_number),
    CONSTRAINT uk_payments_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payments_sale_same_tenant
        FOREIGN KEY (sale_id, tenant_id) REFERENCES sales (id, tenant_id),
    CONSTRAINT fk_payments_so_same_tenant
        FOREIGN KEY (sales_order_id, tenant_id) REFERENCES sales_orders (id, tenant_id),
    CONSTRAINT fk_payments_customer_same_tenant
        FOREIGN KEY (customer_id, tenant_id) REFERENCES customers (id, tenant_id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT ck_payments_has_target CHECK (sale_id IS NOT NULL OR sales_order_id IS NOT NULL)
);

CREATE INDEX idx_payments_tenant_id ON payments (tenant_id);
CREATE INDEX idx_payments_sale_id ON payments (sale_id);
CREATE INDEX idx_payments_so_id ON payments (sales_order_id);
CREATE INDEX idx_payments_customer_id ON payments (customer_id);

CREATE TABLE sales_pipeline_activities (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    lead_id         UUID NOT NULL,
    activity_type   VARCHAR(32) NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    reference_type  VARCHAR(32),
    reference_id    UUID,
    created_by      UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_activities_lead_same_tenant
        FOREIGN KEY (lead_id, tenant_id) REFERENCES leads (id, tenant_id),
    CONSTRAINT ck_activities_type CHECK (activity_type IN (
        'LEAD_CREATED',
        'FOLLOW_UP_CREATED',
        'FOLLOW_UP_COMPLETED',
        'QUOTATION_CREATED',
        'QUOTATION_SENT',
        'QUOTATION_ACCEPTED',
        'SALES_ORDER_CREATED',
        'SALE_CREATED',
        'PAYMENT_RECEIVED',
        'LEAD_WON',
        'LEAD_LOST',
        'LEAD_CONVERTED'
    ))
);

CREATE INDEX idx_activities_lead_id ON sales_pipeline_activities (lead_id, created_at);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    user_id         UUID REFERENCES users (id),
    type            VARCHAR(32) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    entity_type     VARCHAR(32),
    entity_id       UUID,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'FOLLOW_UP_DUE',
        'FOLLOW_UP_OVERDUE',
        'QUOTATION_EXPIRING',
        'QUOTATION_ACCEPTED',
        'PAYMENT_OUTSTANDING'
    ))
);

CREATE INDEX idx_notifications_tenant_user ON notifications (tenant_id, user_id, created_at DESC);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'lead_number_counters',
        'quotation_number_counters',
        'sales_order_number_counters',
        'payment_number_counters',
        'leads',
        'follow_ups',
        'quotations',
        'quotation_items',
        'sales_orders',
        'sales_order_items',
        'payments',
        'sales_pipeline_activities',
        'notifications'
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
