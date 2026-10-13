package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * password を確かめた利用者。2 段階のログインの 1 段目の主体（Bolt 31c、ADR-012 の見直しの 4）。password の段だけで開く画面
 * （A-02 認証コードの入力、A-07 認証アプリの登録）が受ける、identity の中だけの値。ほかのコンテキストが受ける
 * {@code AuthenticatedActor} は 2 つの要素（password と TOTP または回復コード）がそろった主体だけで、この値とは分ける。
 *
 * @param userId 利用者
 * @param companyId 所属企業（監査記録に残す）
 * @param email ログインのメールアドレス（dev の A-02 の入力済みのコードと、登録の otpauth の URI のアカウント名に使う）
 */
@ValueObject
public record PasswordVerifiedUser(UserId userId, CompanyId companyId, EmailAddress email) {

    public PasswordVerifiedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(email, "email");
    }
}
