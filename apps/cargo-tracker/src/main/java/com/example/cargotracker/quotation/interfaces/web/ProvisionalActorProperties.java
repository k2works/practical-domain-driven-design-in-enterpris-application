package com.example.cargotracker.quotation.interfaces.web;

import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 認証を入れるまでの仮の主体（Bolt 1 の AI の仮定）。荷主企業と提出者、審査の判断者（営業担当者。Bolt 5）を設定の固定値にする。
 * US-18 の Bolt で認証の主体に置き換え、このクラスを消す。
 *
 * @param shipperCompanyId 荷主企業 ID
 * @param userId 提出者の利用者 ID
 * @param staffUserId 審査の判断者（仮の営業担当者）の利用者 ID
 */
@ConfigurationProperties("cargotracker.provisional-actor")
public record ProvisionalActorProperties(UUID shipperCompanyId, UUID userId, UUID staffUserId) {

    public ProvisionalActorProperties {
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(staffUserId, "staffUserId");
    }
}
