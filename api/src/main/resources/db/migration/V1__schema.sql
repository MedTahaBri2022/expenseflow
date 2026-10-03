CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('EMPLOYEE', 'MANAGER', 'FINANCE')),
    -- The person who approves this user's claims. NULL: the finance team does.
    manager_id    BIGINT REFERENCES app_user (id),
    CONSTRAINT ck_not_own_manager CHECK (manager_id IS NULL OR manager_id <> id)
);

CREATE INDEX idx_user_manager ON app_user (manager_id);

CREATE TABLE expense_claim (
    id           BIGSERIAL PRIMARY KEY,
    owner_id     BIGINT        NOT NULL REFERENCES app_user (id),
    title        VARCHAR(120)  NOT NULL,
    status       VARCHAR(20)   NOT NULL CHECK (status IN
                     ('DRAFT', 'SUBMITTED', 'PENDING_FINANCE', 'APPROVED', 'REJECTED', 'PAID')),
    -- Sum of the items, maintained by the application with the items.
    total        NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    created_at   TIMESTAMPTZ   NOT NULL,
    submitted_at TIMESTAMPTZ,
    -- Optimistic locking: two concurrent decisions cannot both win.
    version      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_claim_owner_status ON expense_claim (owner_id, status);
CREATE INDEX idx_claim_status_created ON expense_claim (status, created_at DESC);

CREATE TABLE expense_item (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT         NOT NULL REFERENCES expense_claim (id) ON DELETE CASCADE,
    spent_on    DATE           NOT NULL,
    category    VARCHAR(20)    NOT NULL CHECK (category IN ('MEALS', 'TRAVEL', 'LODGING', 'SUPPLIES', 'OTHER')),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    description VARCHAR(200)   NOT NULL
);

CREATE INDEX idx_item_claim ON expense_item (claim_id);

-- Audit trail: one row per change of status, never updated or deleted.
CREATE TABLE claim_event (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT       NOT NULL REFERENCES expense_claim (id) ON DELETE CASCADE,
    actor_id    BIGINT       NOT NULL REFERENCES app_user (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    comment     VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_event_claim ON claim_event (claim_id, occurred_at);
