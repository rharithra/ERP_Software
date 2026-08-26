-- RetailFlow M2: tenant-owned categories and products with RLS

CREATE TABLE categories (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_categories_id_tenant ON categories (id, tenant_id);
CREATE UNIQUE INDEX uk_categories_tenant_name_lower ON categories (tenant_id, LOWER(name));
CREATE INDEX idx_categories_tenant_id ON categories (tenant_id);

CREATE TABLE products (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    category_id     UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    sku             VARCHAR(64) NOT NULL,
    barcode         VARCHAR(64),
    description     VARCHAR(1000),
    cost_price      NUMERIC(12, 2) NOT NULL,
    selling_price   NUMERIC(12, 2) NOT NULL,
    gst_rate        NUMERIC(4, 2) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_products_category_same_tenant
        FOREIGN KEY (category_id, tenant_id) REFERENCES categories (id, tenant_id),
    CONSTRAINT ck_products_cost_price CHECK (cost_price >= 0),
    CONSTRAINT ck_products_selling_price CHECK (selling_price >= 0),
    CONSTRAINT ck_products_gst_rate CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    CONSTRAINT ck_products_unit CHECK (unit IN ('PCS', 'KG', 'G', 'L', 'ML', 'BOX', 'PACK', 'BOTTLE'))
);

CREATE UNIQUE INDEX uk_products_tenant_sku_lower ON products (tenant_id, LOWER(sku));
CREATE UNIQUE INDEX uk_products_tenant_barcode ON products (tenant_id, barcode)
    WHERE barcode IS NOT NULL AND btrim(barcode) <> '';
CREATE INDEX idx_products_tenant_id ON products (tenant_id);
CREATE INDEX idx_products_category_id ON products (category_id);

ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories FORCE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;

CREATE POLICY categories_isolation ON categories
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

CREATE POLICY products_isolation ON products
    USING (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.bypass_rls', true) = 'on'
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );
