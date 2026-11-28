-- 経路設計コンテキスト（routing）。Bolt 17（US-06 AC1〜AC3、R-INV-01・02・10、データモデル `routing`）。
-- 候補の算出までに使う表と列だけで作る。経路版の判断・承認・再設計の列と参照情報版の表は、使う Bolt（US-07・US-08）で足す。
-- 見積りの表への外部キーは張らない（スキーマの所有。ADR-001）。輸送要求・見積りの ID と業務番号は公開 API とイベントの写し。
CREATE SCHEMA IF NOT EXISTS routing;

CREATE TABLE routing.routing_case (
    id                           UUID                     NOT NULL,
    case_number                  VARCHAR(20)              NOT NULL,
    transport_request_id         UUID                     NOT NULL,
    transport_request_number     VARCHAR(20)              NOT NULL,
    transport_request_version_no INTEGER                  NOT NULL,
    quotation_id                 UUID                     NOT NULL,
    route_policy_via             VARCHAR(200),
    origin_unlocode              CHAR(5)                  NOT NULL,
    destination_unlocode         CHAR(5)                  NOT NULL,
    arrival_deadline             TIMESTAMP WITH TIME ZONE NOT NULL,
    cargo_category               VARCHAR(30)              NOT NULL,
    requested_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_route_version_no   INTEGER,
    version                      BIGINT                   NOT NULL,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_routing_case PRIMARY KEY (id),
    CONSTRAINT uk_routing_case_number UNIQUE (case_number),
    CONSTRAINT uk_routing_case_request_version UNIQUE (transport_request_id, transport_request_version_no),
    CONSTRAINT ck_routing_case_request_version_no CHECK (transport_request_version_no >= 1),
    CONSTRAINT ck_routing_case_route CHECK (origin_unlocode <> destination_unlocode)
);

CREATE TABLE routing.route_version (
    routing_case_id         UUID                     NOT NULL,
    route_version_no        INTEGER                  NOT NULL,
    status                  VARCHAR(30)              NOT NULL,
    candidates_evaluated_at TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_route_version PRIMARY KEY (routing_case_id, route_version_no),
    CONSTRAINT fk_route_version_case FOREIGN KEY (routing_case_id) REFERENCES routing.routing_case (id),
    CONSTRAINT ck_route_version_no CHECK (route_version_no >= 1),
    CONSTRAINT ck_route_version_status CHECK (status IN (
        'DRAFT', 'CANDIDATES_PRESENTED', 'EXPERT_REVIEW', 'CONFIRMED', 'REDESIGN_REQUIRED', 'SUPERSEDED')),
    CONSTRAINT ck_route_version_evaluated CHECK (status = 'DRAFT' OR candidates_evaluated_at IS NOT NULL)
);

-- 候補・区間・除外理由は、候補を再算出したら消して入れ直す（追記専用ではない）
CREATE TABLE routing.route_candidate (
    routing_case_id              UUID                     NOT NULL,
    route_version_no             INTEGER                  NOT NULL,
    candidate_no                 INTEGER                  NOT NULL,
    conforming                   BOOLEAN                  NOT NULL,
    estimated_arrival_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    min_connection_slack_minutes INTEGER,
    evaluated_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    oldest_info_acquired_at      TIMESTAMP WITH TIME ZONE,
    info_insufficient            BOOLEAN                  NOT NULL,
    CONSTRAINT pk_route_candidate PRIMARY KEY (routing_case_id, route_version_no, candidate_no),
    CONSTRAINT fk_route_candidate_version FOREIGN KEY (routing_case_id, route_version_no)
        REFERENCES routing.route_version (routing_case_id, route_version_no),
    CONSTRAINT ck_route_candidate_no CHECK (candidate_no BETWEEN 1 AND 20)
);

CREATE TABLE routing.candidate_leg (
    routing_case_id    UUID                     NOT NULL,
    route_version_no   INTEGER                  NOT NULL,
    candidate_no       INTEGER                  NOT NULL,
    leg_no             INTEGER                  NOT NULL,
    voyage_number      VARCHAR(30)              NOT NULL,
    load_unlocode      CHAR(5)                  NOT NULL,
    discharge_unlocode CHAR(5)                  NOT NULL,
    departure_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    cargo_cutoff_at    TIMESTAMP WITH TIME ZONE,
    doc_cutoff_at      TIMESTAMP WITH TIME ZONE,
    info_version       VARCHAR(100)             NOT NULL,
    info_acquired_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    executed           BOOLEAN                  NOT NULL,
    CONSTRAINT pk_candidate_leg PRIMARY KEY (routing_case_id, route_version_no, candidate_no, leg_no),
    CONSTRAINT fk_candidate_leg_candidate FOREIGN KEY (routing_case_id, route_version_no, candidate_no)
        REFERENCES routing.route_candidate (routing_case_id, route_version_no, candidate_no),
    CONSTRAINT ck_candidate_leg_no CHECK (leg_no >= 1),
    CONSTRAINT ck_candidate_leg_dates CHECK (arrival_at > departure_at)
);

CREATE TABLE routing.exclusion_reason (
    routing_case_id  UUID                     NOT NULL,
    route_version_no INTEGER                  NOT NULL,
    candidate_no     INTEGER                  NOT NULL,
    reason_no        INTEGER                  NOT NULL,
    reason_code      VARCHAR(30)              NOT NULL,
    violated_at      TIMESTAMP WITH TIME ZONE,
    threshold        VARCHAR(100),
    info_version     VARCHAR(100),
    CONSTRAINT pk_exclusion_reason PRIMARY KEY (routing_case_id, route_version_no, candidate_no, reason_no),
    CONSTRAINT fk_exclusion_reason_candidate FOREIGN KEY (routing_case_id, route_version_no, candidate_no)
        REFERENCES routing.route_candidate (routing_case_id, route_version_no, candidate_no),
    CONSTRAINT ck_exclusion_reason_code CHECK (reason_code IN (
        'DEADLINE_EXCEEDED', 'CONNECTION_TOO_SHORT', 'CARGO_NOT_SUPPORTED', 'NOT_CONNECTABLE', 'INFO_INSUFFICIENT'))
);

-- 航海と寄港。外部原本の取込（US-14、W7）ができるまでは、開発環境の仮の航海データだけが入る
CREATE TABLE routing.voyage (
    voyage_number        VARCHAR(30)              NOT NULL,
    adopted_info_version VARCHAR(100)             NOT NULL,
    source_kind          VARCHAR(30)              NOT NULL,
    source_ref           VARCHAR(200)             NOT NULL,
    acquired_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version              BIGINT                   NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_voyage PRIMARY KEY (voyage_number),
    CONSTRAINT ck_voyage_source_kind CHECK (source_kind IN ('EXTERNAL_RECORD', 'FIELD_RECORD', 'INTERNAL_CHECK', 'MANUAL_ENTRY'))
);

CREATE TABLE routing.port_call (
    voyage_number VARCHAR(30)              NOT NULL,
    call_no       INTEGER                  NOT NULL,
    port_unlocode CHAR(5)                  NOT NULL,
    arrival_at    TIMESTAMP WITH TIME ZONE,
    departure_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_port_call PRIMARY KEY (voyage_number, call_no),
    CONSTRAINT fk_port_call_voyage FOREIGN KEY (voyage_number) REFERENCES routing.voyage (voyage_number),
    CONSTRAINT ck_port_call_no CHECK (call_no >= 1),
    CONSTRAINT ck_port_call_times CHECK (arrival_at IS NOT NULL OR departure_at IS NOT NULL)
);

-- 接続時間規則。業務責任者の値（DM-05）が届くまでは、開発環境の仮の値だけが入る
CREATE TABLE routing.connection_rule (
    id                     UUID                     NOT NULL,
    route_scope            VARCHAR(100)             NOT NULL,
    port_unlocode          CHAR(5)                  NOT NULL,
    min_connection_minutes INTEGER                  NOT NULL,
    valid_from             TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to               TIMESTAMP WITH TIME ZONE,
    version                BIGINT                   NOT NULL,
    CONSTRAINT pk_connection_rule PRIMARY KEY (id),
    CONSTRAINT ck_connection_rule_minutes CHECK (min_connection_minutes >= 0),
    CONSTRAINT ck_connection_rule_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);

-- 案件番号の採番。年ごとに 1 行（業務番号の採番と同じ形。D-10）。行を更新するため追記専用ではない
CREATE TABLE routing.routing_case_number_counter (
    number_year SMALLINT NOT NULL,
    last_no     INTEGER  NOT NULL,
    CONSTRAINT pk_routing_case_number_counter PRIMARY KEY (number_year),
    CONSTRAINT ck_routing_case_number_counter_last_no CHECK (last_no >= 0)
);

CREATE INDEX ix_routing_case_requested_at ON routing.routing_case (requested_at);

COMMENT ON TABLE routing.routing_case IS '経路設計案件';
COMMENT ON COLUMN routing.routing_case.id IS '経路設計案件 ID';
COMMENT ON COLUMN routing.routing_case.case_number IS '案件番号';
COMMENT ON COLUMN routing.routing_case.transport_request_id IS '輸送要求 ID（見積りの写し）';
COMMENT ON COLUMN routing.routing_case.transport_request_number IS '業務番号（見積りの写し）';
COMMENT ON COLUMN routing.routing_case.transport_request_version_no IS '輸送要求の版番号';
COMMENT ON COLUMN routing.routing_case.quotation_id IS '依頼元の見積り ID';
COMMENT ON COLUMN routing.routing_case.route_policy_via IS '経路方針（参考）の主な経由地（UN/LOCODE のカンマ区切り）';
COMMENT ON COLUMN routing.routing_case.origin_unlocode IS '出発地';
COMMENT ON COLUMN routing.routing_case.destination_unlocode IS '目的地';
COMMENT ON COLUMN routing.routing_case.arrival_deadline IS '希望到着期限';
COMMENT ON COLUMN routing.routing_case.cargo_category IS '貨物種別';
COMMENT ON COLUMN routing.routing_case.requested_at IS '詳細経路設計の依頼時刻';
COMMENT ON COLUMN routing.routing_case.confirmed_route_version_no IS '確定した経路版番号（未確定は NULL）';
COMMENT ON COLUMN routing.routing_case.version IS '楽観ロックの版';
COMMENT ON COLUMN routing.routing_case.created_at IS '作成時刻';
COMMENT ON COLUMN routing.routing_case.updated_at IS '更新時刻';

COMMENT ON TABLE routing.route_version IS '経路版';
COMMENT ON COLUMN routing.route_version.routing_case_id IS '経路設計案件 ID';
COMMENT ON COLUMN routing.route_version.route_version_no IS '経路版番号';
COMMENT ON COLUMN routing.route_version.status IS '状態';
COMMENT ON COLUMN routing.route_version.candidates_evaluated_at IS '候補の判定時刻';
COMMENT ON COLUMN routing.route_version.created_at IS '作成時刻';

COMMENT ON TABLE routing.route_candidate IS '経路候補';
COMMENT ON COLUMN routing.route_candidate.routing_case_id IS '経路設計案件 ID';
COMMENT ON COLUMN routing.route_candidate.route_version_no IS '経路版番号';
COMMENT ON COLUMN routing.route_candidate.candidate_no IS '候補番号';
COMMENT ON COLUMN routing.route_candidate.conforming IS '適合か';
COMMENT ON COLUMN routing.route_candidate.estimated_arrival_at IS '到着予定';
COMMENT ON COLUMN routing.route_candidate.min_connection_slack_minutes IS '接続余裕（分。直行は NULL）';
COMMENT ON COLUMN routing.route_candidate.evaluated_at IS '判定時刻';
COMMENT ON COLUMN routing.route_candidate.oldest_info_acquired_at IS '最も古い情報の取得時刻（情報鮮度）';
COMMENT ON COLUMN routing.route_candidate.info_insufficient IS '情報不足か';

COMMENT ON TABLE routing.candidate_leg IS '区間';
COMMENT ON COLUMN routing.candidate_leg.routing_case_id IS '経路設計案件 ID';
COMMENT ON COLUMN routing.candidate_leg.route_version_no IS '経路版番号';
COMMENT ON COLUMN routing.candidate_leg.candidate_no IS '候補番号';
COMMENT ON COLUMN routing.candidate_leg.leg_no IS '区間の番号';
COMMENT ON COLUMN routing.candidate_leg.voyage_number IS '航海番号';
COMMENT ON COLUMN routing.candidate_leg.load_unlocode IS '積地';
COMMENT ON COLUMN routing.candidate_leg.discharge_unlocode IS '揚地';
COMMENT ON COLUMN routing.candidate_leg.departure_at IS '出発予定';
COMMENT ON COLUMN routing.candidate_leg.arrival_at IS '到着予定';
COMMENT ON COLUMN routing.candidate_leg.cargo_cutoff_at IS '搬入締切';
COMMENT ON COLUMN routing.candidate_leg.doc_cutoff_at IS '書類締切';
COMMENT ON COLUMN routing.candidate_leg.info_version IS '航海の採用情報版';
COMMENT ON COLUMN routing.candidate_leg.info_acquired_at IS '航海の情報の取得時刻';
COMMENT ON COLUMN routing.candidate_leg.executed IS '実行済みか';

COMMENT ON TABLE routing.exclusion_reason IS '除外理由';
COMMENT ON COLUMN routing.exclusion_reason.routing_case_id IS '経路設計案件 ID';
COMMENT ON COLUMN routing.exclusion_reason.route_version_no IS '経路版番号';
COMMENT ON COLUMN routing.exclusion_reason.candidate_no IS '候補番号';
COMMENT ON COLUMN routing.exclusion_reason.reason_no IS '理由の番号';
COMMENT ON COLUMN routing.exclusion_reason.reason_code IS '理由の区分';
COMMENT ON COLUMN routing.exclusion_reason.violated_at IS '不適合となった時刻';
COMMENT ON COLUMN routing.exclusion_reason.threshold IS '閾値（期限超過は希望到着期限、接続不足は港と必要最小接続時間、接続できないは港）';
COMMENT ON COLUMN routing.exclusion_reason.info_version IS '参照情報版';

COMMENT ON TABLE routing.voyage IS '航海';
COMMENT ON COLUMN routing.voyage.voyage_number IS '航海番号';
COMMENT ON COLUMN routing.voyage.adopted_info_version IS '採用情報版';
COMMENT ON COLUMN routing.voyage.source_kind IS '出典の種類';
COMMENT ON COLUMN routing.voyage.source_ref IS '出典の参照';
COMMENT ON COLUMN routing.voyage.acquired_at IS '情報の取得時刻';
COMMENT ON COLUMN routing.voyage.version IS '楽観ロックの版';
COMMENT ON COLUMN routing.voyage.updated_at IS '更新時刻';

COMMENT ON TABLE routing.port_call IS '寄港';
COMMENT ON COLUMN routing.port_call.voyage_number IS '航海番号';
COMMENT ON COLUMN routing.port_call.call_no IS '寄港の順';
COMMENT ON COLUMN routing.port_call.port_unlocode IS '港';
COMMENT ON COLUMN routing.port_call.arrival_at IS '到着予定';
COMMENT ON COLUMN routing.port_call.departure_at IS '出発予定';

COMMENT ON TABLE routing.connection_rule IS '接続時間規則';
COMMENT ON COLUMN routing.connection_rule.id IS '規則 ID';
COMMENT ON COLUMN routing.connection_rule.route_scope IS '対象の航路（R0.1 は使わない）';
COMMENT ON COLUMN routing.connection_rule.port_unlocode IS '対象の港';
COMMENT ON COLUMN routing.connection_rule.min_connection_minutes IS '必要最小接続時間（分）';
COMMENT ON COLUMN routing.connection_rule.valid_from IS '適用の開始';
COMMENT ON COLUMN routing.connection_rule.valid_to IS '適用の終わり（含まない。NULL は終わりなし）';
COMMENT ON COLUMN routing.connection_rule.version IS '楽観ロックの版';

COMMENT ON TABLE routing.routing_case_number_counter IS '案件番号の採番';
COMMENT ON COLUMN routing.routing_case_number_counter.number_year IS '案件番号の年';
COMMENT ON COLUMN routing.routing_case_number_counter.last_no IS 'その年に最後に振った連番';
