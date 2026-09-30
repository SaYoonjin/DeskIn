-- pr6_deploy 마이그레이션의 롤백. 웹훅 중복 판별을 다시 paymentKey+status+금액으로 되돌리고 paymentKey를 100자로 줄인다.
-- 주의: 거래 키만 다른 부분 취소 이벤트는 이전 제약에서 중복이므로 가장 먼저 저장된 행만 남기고 삭제한다.
-- 주의: payment_key가 100자를 넘는 행이 있으면 ALTER가 실패하고 전체가 롤백된다. 해당 행을 먼저 정리한 뒤 다시 실행한다.
BEGIN;
DELETE FROM webhook_events a
USING webhook_events b
WHERE a.id > b.id
  AND a.payment_key = b.payment_key
  AND a.status = b.status
  AND a.total_amount = b.total_amount;
ALTER TABLE webhook_events DROP CONSTRAINT uk_webhook_events_payment_key_status_transaction;
ALTER TABLE webhook_events DROP COLUMN last_transaction_key;
ALTER TABLE webhook_events ALTER COLUMN payment_key TYPE varchar(100);
ALTER TABLE webhook_events ADD CONSTRAINT uk_webhook_events_payment_key_status_amount
    UNIQUE (payment_key, status, total_amount);
COMMIT;
