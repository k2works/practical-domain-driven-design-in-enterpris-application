-- 寄港 60 の航海の DE-12（VoyageAdoptedValueUpdated）は直列化すると 4000 文字を超え、Spring Modulith の H2 の定義のままでは
-- イベント発行記録に入らず取込全体が巻き戻る。ローカル起動・デモ環境（H2）でも本番と同じ取込を受けるため、H2 だけ広げる
-- （Bolt 39b3b。「人に諮ること」3 の決定、ADR-007・ADR-013）。PostgreSQL は TEXT のまま。索引（listener_id, serialized_event）は H2 が作り直す
ALTER TABLE platform.event_publication ALTER COLUMN serialized_event SET DATA TYPE VARCHAR(1000000);
