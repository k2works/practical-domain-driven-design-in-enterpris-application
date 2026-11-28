package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 認証アプリを登録する利用者。Bolt 33b で、Bolt 31c の議題 4 を受けて作った。初回の登録（A-07。password の段）と再登録（A-08。2 つの要素が
 * そろった主体）の両方から登録のサービスに渡す、ログインの段に中立な値。画面が主体から詰め替える。
 *
 * @param userId 利用者
 * @param companyId 所属企業（監査記録に残す）
 * @param email ログインのメールアドレス（otpauth の URI のアカウント名に使う）
 */
@ValueObject
public record EnrollingUser(UserId userId, CompanyId companyId, EmailAddress email) {

    public EnrollingUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(email, "email");
    }

    /** password を確かめた利用者から作る（A-07）。 */
    public static EnrollingUser from(PasswordVerifiedUser user) {
        return new EnrollingUser(user.userId(), user.companyId(), user.email());
    }
}
