-- 審査記録（Bolt 5、US-02、Q-INV-04・14、データモデル `review_record`）。
-- 1 つの版に対する審査の判断の事実で、後から変えないため追記専用にする（2026-10-02 に承認）。
CREATE TABLE quotation.review_record (
    id                   UUID                     NOT NULL,
    transport_request_id UUID                     NOT NULL,
    version_no           INTEGER                  NOT NULL,
    decision             VARCHAR(30)              NOT NULL,
    reviewer_id          UUID                     NOT NULL,
    rationale            VARCHAR(4000)            NOT NULL,
    missing_items        VARCHAR(4000),
    decided_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_review_record PRIMARY KEY (id),
    CONSTRAINT fk_review_record_version
        FOREIGN KEY (transport_request_id, version_no)
        REFERENCES quotation.transport_request_version (transport_request_id, version_no),
    CONSTRAINT ck_review_record_decision CHECK (decision IN ('APPROVED', 'SENT_BACK'))
);

CREATE INDEX ix_review_record_request ON quotation.review_record (transport_request_id, decided_at);

COMMENT ON TABLE quotation.review_record IS 'append-only';
