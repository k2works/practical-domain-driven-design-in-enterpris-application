package com.example.cargotracker.shared.domain;

/**
 * 出典の種類。実績や採用値の根拠の出どころ（ドメインモデルの共有カーネル。Bolt 26b）。値は経路設計の航海の出典の種類の CHECK
 * （{@code ck_voyage_source_kind}）と同じ。
 */
public enum SourceKind {
    /** 外部原本（運送会社・港湾などの原本。取込は W7）。 */
    EXTERNAL_RECORD,
    /** 現場記録。 */
    FIELD_RECORD,
    /** 社内確認。 */
    INTERNAL_CHECK,
    /** 手動入力。 */
    MANUAL_ENTRY
}
