-- RetailFlow M5.1: tenant business type and sales experience (entry workflow, not a second sales engine)

ALTER TABLE tenants
    ADD COLUMN business_type VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN sales_mode VARCHAR(32) NOT NULL DEFAULT 'HYBRID';

ALTER TABLE tenants
    ADD CONSTRAINT ck_tenants_business_type CHECK (business_type IN (
        'GROCERY_SUPERMARKET',
        'ELECTRONICS_COMPUTER',
        'MOBILE_ACCESSORIES',
        'APPLIANCES_WATER_PURIFIER',
        'FURNITURE',
        'HARDWARE_BUILDING_MATERIALS',
        'OTHER'
    ));

ALTER TABLE tenants
    ADD CONSTRAINT ck_tenants_sales_mode CHECK (sales_mode IN (
        'QUICK_SALE',
        'PIPELINE',
        'HYBRID'
    ));
