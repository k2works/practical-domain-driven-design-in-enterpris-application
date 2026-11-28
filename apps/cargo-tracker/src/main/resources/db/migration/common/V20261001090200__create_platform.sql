-- 基盤（platform）。フレームワークの表だけを置き、業務の表を置かない。
-- 表の DDL は DB ごとに違うため db/migration/{vendor} に置く（ADR-007）。
CREATE SCHEMA IF NOT EXISTS platform;
