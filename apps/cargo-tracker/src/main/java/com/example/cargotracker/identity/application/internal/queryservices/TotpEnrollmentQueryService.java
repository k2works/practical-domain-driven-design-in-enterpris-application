package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * TOTP の登録の問い合わせ（Bolt 31b）。画面の層だけが呼ぶ（A-02 の画面が未登録の利用者を A-07 へ送る）。認証の入口からは呼ばない
 * （層の規則 D-5 の認証の例外は commandservices に限る）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpEnrollmentQueryService {

    private final TotpCredentialRepository credentials;

    public TotpEnrollmentQueryService(TotpCredentialRepository credentials) {
        this.credentials = credentials;
    }

    /** TOTP を登録しているか。 */
    public boolean isEnrolled(UserId userId) {
        return credentials.findByUserId(userId).isPresent();
    }
}
