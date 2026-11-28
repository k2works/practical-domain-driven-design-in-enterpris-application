-- 却下の理由はアプリケーションがコードポイントで 1,000 文字までに守るが、H2 の VARCHAR は UTF-16 の単位で数えるため、補助文字を含む
-- 1,000 コードポイントの理由が VARCHAR(1000) に入らない（Bolt 39d2 の行 21。受信記録の注記の列と同じ事情。前例
-- V20261104145000__widen_received_record_classification_note.sql）。ローカル起動・デモ環境（H2）でも本番と同じ理由を受けるため、H2 だけ
-- 1,000 コードポイントの補助文字が入る VARCHAR(2000) に広げる（ADR-007）。PostgreSQL は文字で数えるので VARCHAR(1000) のまま
ALTER TABLE external_data.received_record_decision ALTER COLUMN reason SET DATA TYPE VARCHAR(2000);
