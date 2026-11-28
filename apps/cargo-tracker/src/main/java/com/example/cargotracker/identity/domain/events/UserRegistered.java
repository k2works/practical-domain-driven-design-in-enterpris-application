package com.example.cargotracker.identity.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-25 利用者を登録した。システム管理者が利用者を登録したとき、登録のトランザクションで出す（拒否と重複のロールバックでは残らない。
 * Bolt 38b2c、ADR-017）。identity のイベントハンドラーがコミットの後に別のトランザクションで招待のトークンを発行してメールを送る。発行記録に
 * JSON で残るので、メールアドレス・操作者・役割を載せない。監査記録は登録のサービスが同期に書く。
 *
 * @param registrationId 登録の ID（招待のトークンの {@code request_id} と通知の {@code event_id} を結ぶ）
 * @param userId 登録した利用者
 * @param registeredAt 登録の時刻
 */
@DomainEvent
public record UserRegistered(UUID registrationId, UserId userId, UtcInstant registeredAt) {}
