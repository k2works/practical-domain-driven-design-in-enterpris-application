package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

/**
 * 監査記録のリポジトリ（送信ポート）。追記と照会だけを持ち、更新と削除はしない（IA-INV-07）。
 */
public interface AuditRecordRepository {

    void append(AuditRecord auditRecord);

    /** 操作者の監査記録を、発生時刻の古い順に返す。 */
    List<AuditRecord> findByActorUserId(UserId actor);

    /**
     * 対象の利用者の今の停止の記録（最新の監査記録 {@code USER_SUSPENDED}）を、停止した人の今の表示名とともに返す（Bolt 38c2。S-20 の
     * 詳細）。停止の記録がなければ空。停止と再開のうち最新が再開なら空（前の停止を今の停止として返さない）。最新の停止の行に理由か操作者が
     * なければ空（DB を直接書き換えた行。前の停止には戻らない）。停止した人の利用者の行がなければ、表示名を空にして記録は返す。
     */
    Optional<SuspensionRecord> findLatestUserSuspension(UserId target);
}
