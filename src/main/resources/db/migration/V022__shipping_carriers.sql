CREATE TABLE shipping_carriers (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    code VARCHAR(40) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_shipping_carriers_provider_code UNIQUE (provider, code)
);

CREATE INDEX idx_shipping_carriers_provider_active
    ON shipping_carriers(provider, active, sort_order, display_name);

-- These records are deliberately scoped to the non-production stub adapter. Production
-- SweetTracker codes must be populated from the contracted provider catalogue instead of
-- treating these stable local smoke-test identifiers as provider codes.
INSERT INTO shipping_carriers(
    id, provider, code, display_name, active, sort_order, created_at, updated_at
) VALUES
    ('52000000-0000-0000-0000-000000000001', 'stub', 'kr.cjlogistics', 'CJ대한통운 (로컬 샘플)', TRUE, 10, NOW(), NOW()),
    ('52000000-0000-0000-0000-000000000002', 'stub', 'kr.hanjin', '한진택배 (로컬 샘플)', TRUE, 20, NOW(), NOW()),
    ('52000000-0000-0000-0000-000000000003', 'stub', 'kr.lotte', '롯데택배 (로컬 샘플)', TRUE, 30, NOW(), NOW()),
    ('52000000-0000-0000-0000-000000000004', 'stub', 'kr.epost', '우체국택배 (로컬 샘플)', TRUE, 40, NOW(), NOW());
