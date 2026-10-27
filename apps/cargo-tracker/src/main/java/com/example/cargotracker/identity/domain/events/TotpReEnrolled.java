package com.example.cargotracker.identity.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-27 TOTP を再登録した。回復コードでログインして A-08 で認証アプリを登録し直したとき、資格の置き換えと回復コードの発行し直しと監査を書くのと
 * 同じトランザクションで出す（置き換えられなかったときは残らない。Bolt 38d、SEC-10）。identity のイベントハンドラーがコミットの後に別の
 * トランザクションで本人へ再登録のお知らせを送る。発行記録に JSON で残るので、メールアドレスを載せない。
 *
 * @param auditRecordId 再登録の監査記録の ID（通知の {@code event_id} と {@code target_ref}）
 * @param userId 登録し直した利用者
 * @param reEnrolledAt 再登録の時刻（監査の時刻と同じ）
 */
@DomainEvent
public record TotpReEnrolled(UUID auditRecordId, UserId userId, UtcInstant reEnrolledAt) {}
