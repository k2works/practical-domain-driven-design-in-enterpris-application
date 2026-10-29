-- 開発環境とデモ環境だけ: 経路設計案件の荷主企業を埋める（2 段目。Bolt 38g）。
-- V20261029100000 で足した列を、案件の輸送要求の荷主企業で埋める。見本の案件と、デモ環境で実行時に作った案件のどちらも埋める。
-- H2 と PostgreSQL の両方で通るよう相関副問合せで書く（UPDATE ... FROM は H2 で通らない）。
-- 次の V20261029101000 で NOT NULL にするので、埋め漏れがあればその移行の失敗で気づける。ステージング・本番には行がない。
UPDATE routing.routing_case c
   SET shipper_company_id = (
           SELECT r.shipper_company_id
             FROM quotation.transport_request r
            WHERE r.id = c.transport_request_id)
 WHERE c.shipper_company_id IS NULL;
