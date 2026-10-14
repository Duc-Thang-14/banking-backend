-- 5. Bảng Beneficiaries (Danh bạ người thụ hưởng)
CREATE TABLE IF NOT EXISTS beneficiaries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    beneficiary_wallet_number VARCHAR(32) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_beneficiary UNIQUE (user_id, beneficiary_wallet_number)
);

CREATE INDEX IF NOT EXISTS idx_beneficiaries_user_id ON beneficiaries(user_id);
CREATE INDEX IF NOT EXISTS idx_beneficiaries_wallet_number ON beneficiaries(beneficiary_wallet_number);
