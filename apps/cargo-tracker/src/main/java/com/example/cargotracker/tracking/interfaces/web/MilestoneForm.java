package com.example.cargotracker.tracking.interfaces.web;

/**
 * S-13 主要実績の登録の入力（Bolt 26c）。画面の入力は文字列のまま受け取り、形式の検証と変換は {@link MilestoneFormConverter}、
 * 業務の規則の検証（同じ出典・未来の発生時刻）はドメインに任せる。誤りがあっても入力値をそのまま画面に戻せるよう、変換前の文字列を持つ
 * （UI 設計の共通部品「フォーム項目」）。
 */
public class MilestoneForm {

    private String kind;
    private String location;
    private String occurredAt;
    private String sourceKind;
    private String sourceReference;
    private long expectedVersion;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    /** 画面を開いたときの追跡記録の版（隠し項目。違えば競合）。 */
    public long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
