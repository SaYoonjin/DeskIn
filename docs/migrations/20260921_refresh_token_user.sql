-- 기존 LoginSession 기반 DB용 1회 마이그레이션. 애플리케이션을 중지하고 백업 후 적용한다.
-- 폐기된 세션, 만료·사용 완료 토큰을 제외하고 유효한 토큰만 User 직접 참조로 전환한다.
BEGIN;
ALTER TABLE refresh_tokens ADD COLUMN user_id bigint;
UPDATE refresh_tokens token
SET user_id = session.user_id
FROM login_sessions session
WHERE token.session_id = session.session_id
  AND session.revoked_at IS NULL
  AND session.expires_at > CURRENT_TIMESTAMP
  AND token.used_at IS NULL
  AND token.expires_at > CURRENT_TIMESTAMP;
DELETE FROM refresh_tokens WHERE user_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(user_id);
ALTER TABLE refresh_tokens RENAME COLUMN issued_at TO created_at;
ALTER TABLE refresh_tokens ADD COLUMN updated_at timestamptz;
UPDATE refresh_tokens SET updated_at = created_at;
ALTER TABLE refresh_tokens DROP COLUMN used_at;
ALTER TABLE refresh_tokens DROP COLUMN session_id;
DROP TABLE login_sessions;
COMMIT;
