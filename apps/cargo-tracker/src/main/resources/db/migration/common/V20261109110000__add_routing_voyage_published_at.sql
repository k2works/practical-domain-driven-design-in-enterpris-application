-- Bolt 39e の開始準備の決定 (4)。同じ航海の DE-12 の順（【業務の判断】(r)）を比べるため、採用値を公開した時刻を航海に足す。
-- 取込を経ない仮の航海は NULL で、どの DE-12 より古いとみなす。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE routing.voyage ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN routing.voyage.published_at IS '採用値を公開した時刻（取込を経ない仮の航海は NULL）';
