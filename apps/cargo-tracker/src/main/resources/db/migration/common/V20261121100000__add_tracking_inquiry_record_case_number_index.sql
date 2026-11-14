-- Bolt 42-1（W8 の持ち越し。人が 2026-11-14 に承認）。照会記録から引き継いだ有人案件の受付番号を引く索引（W9 の US-21・US-23 の集計と、
-- 有人案件の側から照会記録を引く照会）。新しい列・表はない。H2 と PostgreSQL の共通の構文（ADR-007）。
CREATE INDEX ix_inquiry_record_case_number ON tracking.inquiry_record (case_number);
