package com.example.cargotracker.identity.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-26 管理者が TOTP の登録を解除した。システム管理者が別経路の本人確認の後に認証アプリの登録を解除したとき、資格と回復コードを消して監査を
 * 書くのと同じトランザクションで出す（拒否・0 件・日時の誤り・監査の失敗のロールバックでは残らない。Bolt 38d、US-18 AC9、SEC-10）。identity の
 * イベントハンドラーがコミットの後に別のトランザクションで本人へ解除のお知らせを送る。発行記録に JSON で残るので、メールアドレス・操作者・本人
 * 確認の手段を載せない。
 *
 * @param auditRecordId 解除の監査記録の ID（通知の {@code event_id} と {@code target_ref}。同じ解除の 2 回目の配信を見分ける）
 * @param userId 解除された利用者
 * @param resetAt 解除の時刻（監査の時刻と同じ。お知らせの本文に示す）
 */
@DomainEvent
public record TotpResetByAdmin(UUID auditRecordId, UserId userId, UtcInstant resetAt) {}
