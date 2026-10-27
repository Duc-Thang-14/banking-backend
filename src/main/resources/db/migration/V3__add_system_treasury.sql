-- 6. Khởi tạo tài khoản Quản trị & Kho tiền Hệ Thống (System Treasury & Clearing Pool)
INSERT INTO users (username, email, password_hash, full_name, status)
VALUES ('SYSTEM_TREASURY', 'treasury@bank.internal', '$2a$10$wKxN74b9Z1g80gW7L7iFveU18y6eG49YlO9fQ8iZ3Fv1kK5bA1zKi', 'Hệ Thống Kho Tiền & Bù Trừ Trung Ương', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

INSERT INTO wallets (wallet_number, user_id, wallet_type, balance, currency, status, version)
SELECT 'SYS_TREASURY_VND', id, 'SYSTEM', 1000000000000.0000, 'VND', 'ACTIVE', 0
FROM users WHERE username = 'SYSTEM_TREASURY'
ON CONFLICT (wallet_number) DO NOTHING;
