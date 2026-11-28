package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.Entity;
import java.util.Objects;
import java.util.UUID;

/**
 * 情報源。データ責任者が選定・承認した運送会社・港湾などの原本の提供元（BR-18）。リポジトリのポートが受け渡す record で、振る舞いを
 * 持たない（トランザクションスクリプト。ADR-002、Bolt 39b）。承認済みの情報源が 0 件ならパイロットを開始しない（ED-INV-01）。
 *
 * @param id 情報源 ID
 * @param provider 提供元
 * @param recordType 原本種別（取込ファイルの種別の名前。{@code VOYAGE}・{@code MILESTONE}）
 * @param approvalStatus 選定・承認状態
 */
@Entity
public record DataSource(UUID id, String provider, String recordType, ApprovalStatus approvalStatus) {

    public DataSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(approvalStatus, "approvalStatus");
    }

    /** 選定・承認状態。承認済み（{@link #APPROVED}）だけが取込とパイロットの開始に数えられる。 */
    public enum ApprovalStatus {
        /** 候補（選定中）。 */
        CANDIDATE,
        /** 承認済み。 */
        APPROVED,
        /** 停止中。 */
        SUSPENDED
    }
}
