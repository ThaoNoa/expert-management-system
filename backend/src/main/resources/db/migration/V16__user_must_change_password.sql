-- =====================================================================
-- V16: Bắt đổi mật khẩu ở lần đăng nhập đầu
--   Tài khoản tạo tự động khi import hồ sơ chuyên gia, hoặc được quản trị đặt lại mật khẩu,
--   mang mật khẩu tạm -> must_change_password = true cho đến khi người dùng tự đổi.
-- =====================================================================
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
