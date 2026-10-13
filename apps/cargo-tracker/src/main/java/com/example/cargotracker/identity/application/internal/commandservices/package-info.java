/**
 * コマンドサービス。アクセス・監査コンテキストのコマンドを受け付けるユースケース（Bolt 31 の TOTP の確かめから）。
 * Spring Security の認証の入口（{@code identity.infrastructure.security}）からも呼ばれる（層の規則 D-5 の認証の例外。ADR-012）。
 */
package com.example.cargotracker.identity.application.internal.commandservices;
