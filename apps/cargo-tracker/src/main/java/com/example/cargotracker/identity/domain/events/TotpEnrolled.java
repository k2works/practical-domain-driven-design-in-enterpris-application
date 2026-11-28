package com.example.cargotracker.identity.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-28 TOTP を登録した。A-07 で認証アプリを登録したとき、資格と回復コードと監査を書くのと同じトランザクションで出す（招待の後の最初の登録も、
 * 管理者の解除の後の登録も見分けない。Bolt 38d、SEC-10）。identity のイベントハンドラーがコミットの後に別のトランザクションで本人へ登録の
 * お知らせを送る（password を知る第三者が本人より先に登録した窓に、本人が気づくため）。発行記録に JSON で残るので、メールアドレスを載せない。
 *
 * @param auditRecordId 登録の監査記録の ID（通知の {@code event_id} と {@code target_ref}）
 * @param userId 登録した利用者
 * @param enrolledAt 登録の時刻（監査の時刻と同じ）
 */
@DomainEvent
public record TotpEnrolled(UUID auditRecordId, UserId userId, UtcInstant enrolledAt) {}
