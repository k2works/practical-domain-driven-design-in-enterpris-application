-- 受信記録の注記は取込のサービスがコードポイントで 1,000 文字に切るが、H2 の VARCHAR は UTF-16 の単位で数えるため、補助文字を含む注記が
-- VARCHAR(1000) に入らず取込全体が巻き戻る（Bolt 39b4 の行 9 の Red で確かめた）。ローカル起動・デモ環境（H2）でも本番と同じ注記を受けるため、
-- H2 だけ 1,000 コードポイントの補助文字が入る VARCHAR(2000) に広げる（ADR-007）。PostgreSQL は文字で数えるので VARCHAR(1000) のまま
ALTER TABLE external_data.received_record ALTER COLUMN classification_note SET DATA TYPE VARCHAR(2000);
