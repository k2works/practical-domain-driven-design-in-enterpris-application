package com.example.cargotracker.shared.domain;

/**
 * 運用の警告のログの印（共有カーネル。W10-1 の Bolt 57-1。ADR-023、W10-1 計画 CP-2）。アプリは宛先を持たず、ログの行にこの印を
 * 入れるだけにする。CloudWatch のメトリクスフィルターが印を数えて SNS のメールへ送る（{@code ops/terraform/modules/alerts}）。
 * {@code code} を変えるときは Terraform の {@code log_alerts} のキーも変える（{@code OperationsAlertTest} が一致を確かめる）。
 * 印を入れる行には、個人情報とトークンを書かない。
 */
public enum OperationsAlert {
    /** OBS-03 警告: 発行から 5 分以上の未完了のイベント配信がある。 */
    EVENT_BACKLOG_WARNING("event-backlog-warning"),
    /** OBS-03 重大: 未完了のイベント配信が 10 件以上か 30 分以上。 */
    EVENT_BACKLOG_CRITICAL("event-backlog-critical"),
    /** 処理できないイベントを捨てた（再配信しても直らない）。 */
    POISON_EVENT("poison-event"),
    /** 主要実績を実績にせず拒否した。 */
    MILESTONE_REJECTED("milestone-rejected"),
    /** FRS-04: 当日の外部原本の取込がない。 */
    INGESTION_MISSING("ingestion-missing");

    private final String code;

    OperationsAlert(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String marker() {
        return "[ALERT:" + code + "]";
    }
}
