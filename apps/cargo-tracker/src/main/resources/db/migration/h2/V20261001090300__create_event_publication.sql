-- Spring Modulith のイベント発行記録（spring-modulith-events-jdbc 2.1.1 の schemas/v2/schema-h2.sql に従う）
CREATE TABLE platform.event_publication
(
    id                     UUID NOT NULL,
    completion_date        TIMESTAMP(9) WITH TIME ZONE,
    event_type             VARCHAR(512) NOT NULL,
    listener_id            VARCHAR(512) NOT NULL,
    publication_date       TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    serialized_event       VARCHAR(4000) NOT NULL,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_by_listener_id_and_serialized_event_idx ON platform.event_publication (listener_id, serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON platform.event_publication (completion_date);
