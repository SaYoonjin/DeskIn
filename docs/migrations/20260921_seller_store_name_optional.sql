-- 기존 PostgreSQL DB에서 가입 직후 가게 미설정 상태를 허용한다.
ALTER TABLE sellers ALTER COLUMN store_name DROP NOT NULL;
