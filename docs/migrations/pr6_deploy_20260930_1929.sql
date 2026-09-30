-- pr6 배포용 마이그레이션(롤백: pr6_rollback 파일). 기존 PostgreSQL DB용. 웹훅 중복 판별에 개별 거래 키(lastTransactionKey)를 사용하고 paymentKey를 200자로 확장한다.
-- 기존 행은 거래 키를 알 수 없으므로 빈 문자열로 채운다.
BEGIN;
ALTER TABLE webhook_events ALTER COLUMN payment_key TYPE varchar(200);
ALTER TABLE webhook_events ADD COLUMN last_transaction_key varchar(64) NOT NULL DEFAULT '';
ALTER TABLE webhook_events ALTER COLUMN last_transaction_key DROP DEFAULT;
ALTER TABLE webhook_events DROP CONSTRAINT uk_webhook_events_payment_key_status_amount;
ALTER TABLE webhook_events ADD CONSTRAINT uk_webhook_events_payment_key_status_transaction
    UNIQUE (payment_key, status, last_transaction_key);
COMMIT;
