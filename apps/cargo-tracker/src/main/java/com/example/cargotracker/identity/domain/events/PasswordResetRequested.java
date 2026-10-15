package com.example.cargotracker.identity.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-23 password の再設定を依頼した。有効な利用者が A-05 で依頼したとき、依頼のトランザクションで出す（ADR-017。Bolt 33）。
 * identity のイベントハンドラーがコミットの後に別のトランザクションでトークンを発行してメールを送る。発行記録に JSON で残るので、
 * トークン・リンク・メールアドレスを載せない。
 *
 * @param requestId 再設定の依頼の ID（トークンと通知の契機を結ぶ）
 * @param userId 依頼された利用者
 * @param requestedAt 依頼の時刻
 */
@DomainEvent
public record PasswordResetRequested(UUID requestId, UserId userId, UtcInstant requestedAt) {}
