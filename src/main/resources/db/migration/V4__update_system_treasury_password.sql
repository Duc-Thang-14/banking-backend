-- Cập nhật mật khẩu chuẩn Password123@ cho tài khoản Kho tiền Hệ thống
UPDATE users
SET password_hash = '$2a$10$1Sg.rfOeLZ0RRd.SyWM2ZuJuvtkRMOhm/fL55XYPAgVUXl7tz/c/2'
WHERE username = 'SYSTEM_TREASURY';
