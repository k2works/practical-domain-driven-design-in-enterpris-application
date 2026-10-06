package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UserId;
import java.util.List;

/**
 * 監査記録のリポジトリ（送信ポート）。追記だけを持つ（IA-INV-07）。
 */
public interface AuditRecordRepository {

    void append(AuditRecord auditRecord);

    /** 操作者の監査記録を、発生時刻の古い順に返す。 */
    List<AuditRecord> findByActorUserId(UserId actor);
}
