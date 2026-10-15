package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.application.internal.commandservices.LoginAttemptService;
import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetCompletionService;
import com.example.cargotracker.identity.application.internal.commandservices.PasswordResetRequestService;
import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerificationService;
import com.example.cargotracker.identity.application.internal.commandservices.SessionRevalidationService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpEnrollmentService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpVerificationService;
import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.eventhandlers.PasswordResetMailEventHandler;
import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationPasswordResetMails;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.TotpEnrollmentQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CommonPasswordList;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.rules.PasswordPolicy;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.identity.interfaces.web.PasswordResetController;
import com.example.cargotracker.notification.interfaces.api.PasswordResetMail;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * アクセス・監査コンテキストの組み立て。アプリケーションサービスに送信アダプターをつなぐ。
 */
@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    /** 設定の名前（環境変数は CARGOTRACKER_PUBLICBASEURL）。 */
    static final String PUBLIC_BASE_URL = "cargotracker.public-base-url";

    /**
     * 公開の base URL（Bolt 33、SEC-08）を確かめ、末尾の / を除いて返す。メールのリンクは request の Host ヘッダーではなくこの設定から
     * 組み立てる。ステージング・本番では https で localhost でない値を求め、既定の値のまま出さないように起動を止める。
     *
     * @throws IllegalStateException 値がない・絶対の http(s) の URL でない・ステージングと本番で https でないか localhost のとき
     */
    static String publicBaseUrl(Environment environment) {
        String value = environment.getProperty(PUBLIC_BASE_URL, "").strip();
        URI uri = absoluteHttpUrl(value);
        boolean production = environment.acceptsProfiles(Profiles.of("staging | prod"));
        if (production && (!"https".equalsIgnoreCase(uri.getScheme()) || isLoopback(uri.getHost()))) {
            throw new IllegalStateException("ステージング・本番の " + PUBLIC_BASE_URL + " は https でループバックでない URL にする");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** query と fragment のない、http(s) の絶対の URL として読む。 */
    private static URI absoluteHttpUrl(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(PUBLIC_BASE_URL + " が URL でない", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = scheme.equals("http") || scheme.equals("https");
        boolean extra = uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null;
        if (!http || uri.getHost() == null || extra) {
            throw new IllegalStateException(PUBLIC_BASE_URL + " は userinfo・query・fragment のない http(s) の絶対の URL にする");
        }
        return uri;
    }

    private static boolean isLoopback(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return name.equals("localhost") || name.startsWith("127.") || name.equals("[::1]") || name.equals("::1");
    }

    /** TOTP の秘密の暗号化の鍵の設定の名前（版ごとの Base64 の鍵。環境変数は CARGOTRACKER_TOTPSECRETKEYS_V1）。 */
    static final String TOTP_SECRET_KEYS = "cargotracker.totp-secret-keys";

    static Map<String, String> totpSecretKeys(Environment environment) {
        return Binder.get(environment)
                .bind(TOTP_SECRET_KEYS, Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
    }

    /**
     * TOTP の秘密の暗号化（SEC-16、Bolt 31）。鍵は環境変数で渡し、本番は Secrets Manager から注入する（W10）。鍵がない・
     * 形が違うと起動を失敗させる。
     */
    @Bean
    AesGcmSecretCipher totpSecretCipher(Environment environment) {
        return AesGcmSecretCipher.fromBase64Keys(totpSecretKeys(environment));
    }

    @Bean
    TotpVerificationService totpVerificationService(
            TotpCredentialRepository credentials, TotpCodeGenerator generator, Clock clock) {
        return new TotpVerificationService(credentials, generator, clock);
    }

    /** ログインの試行（失敗を数えてロックし、監査記録と同じトランザクションで書く。Bolt 32）。 */
    @Bean
    LoginAttemptService loginAttemptService(
            LoginFailureRepository failures,
            AuditRecordRepository auditRecords,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        return new LoginAttemptService(failures, auditRecords, new LockPolicy(), clock, transactionManager);
    }

    /** request ごとの利用者の確かめ（利用者・役割・企業を 1 回で読み、取消しを監査記録に書く。Bolt 32c）。 */
    @Bean
    SessionRevalidationService sessionRevalidationService(
            UserStandingRepository standings,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new SessionRevalidationService(standings, auditRecords, clock, transactionTemplate);
    }

    @Bean
    RecoveryCodeVerificationService recoveryCodeVerificationService(
            RecoveryCodeRepository codes,
            AuditRecordRepository auditRecords,
            RecoveryCodeHasher hasher,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new RecoveryCodeVerificationService(codes, auditRecords, hasher, clock, transactionTemplate);
    }

    @Bean
    @SuppressWarnings("java:S107") // 登録のサービスに要るポートをそろえて渡す
    TotpEnrollmentService totpEnrollmentService(
            TotpCredentialRepository credentials,
            RecoveryCodeRepository recoveryCodes,
            AuditRecordRepository auditRecords,
            TotpSecretIssuer issuer,
            RecoveryCodeGenerator recoveryCodeGenerator,
            RecoveryCodeHasher hasher,
            TotpCodeGenerator generator,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new TotpEnrollmentService(
                credentials,
                recoveryCodes,
                auditRecords,
                issuer,
                recoveryCodeGenerator,
                hasher,
                generator,
                clock,
                transactionTemplate);
    }

    @Bean
    TotpEnrollmentQueryService totpEnrollmentQueryService(TotpCredentialRepository credentials) {
        return new TotpEnrollmentQueryService(credentials);
    }

    @Bean
    KpiObservationEventHandler kpiObservationEventHandler(KpiObservationRepository repository) {
        return new KpiObservationEventHandler(repository);
    }

    /** password の再設定（リンクを開く、A-06 で設定する。Bolt 33a、US-18 AC8）。方針はこの中で組む（AT-07。LockPolicy と同じ）。 */
    @Bean
    @SuppressWarnings("java:S107") // 再設定に要るポートをそろえて渡す
    PasswordResetCompletionService passwordResetCompletionService(
            PasswordResetTokenRepository tokens,
            PasswordResetTokenHasher tokenHasher,
            UserRepository users,
            CompanyRepository companies,
            PasswordHasher passwordHasher,
            CommonPasswordList commonPasswords,
            UserSessionRevoker sessions,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new PasswordResetCompletionService(
                tokens,
                tokenHasher,
                users,
                companies,
                passwordHasher,
                commonPasswords,
                sessions,
                auditRecords,
                new PasswordPolicy(),
                clock,
                transactionTemplate);
    }

    /** password の再設定の依頼（判定・監査・DE-23。Bolt 33、US-18 AC8）。 */
    @Bean
    PasswordResetRequestService passwordResetRequestService(
            UserRepository users,
            CompanyRepository companies,
            AuditRecordRepository auditRecords,
            ApplicationEventPublisher events,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new PasswordResetRequestService(users, companies, auditRecords, events, clock, transactionTemplate);
    }

    /**
     * DE-23 を受けて再設定のトークンを発行しメールを送る（Bolt 33、ADR-017）。リンクは公開の base URL（{@link #publicBaseUrl}）から組み立てる。
     */
    @Bean
    @SuppressWarnings("java:S107") // 発行と送信に要るポートと設定をそろえて渡す
    PasswordResetMailEventHandler passwordResetMailEventHandler(
            PasswordResetTokenRepository tokens,
            UserRepository users,
            CompanyRepository companies,
            PasswordResetTokenGenerator generator,
            PasswordResetTokenHasher hasher,
            NotificationPasswordResetMails notificationPasswordResetMails,
            Environment environment,
            Clock clock) {
        return new PasswordResetMailEventHandler(
                tokens,
                users,
                companies,
                generator,
                hasher,
                notificationPasswordResetMails,
                publicBaseUrl(environment) + PasswordResetController.URL + "/",
                clock);
    }

    /** 通知の公開 API（password の再設定のメール）を呼ぶ腐敗防止層（ADR-017。Bolt 33）。 */
    @Bean
    NotificationPasswordResetMails notificationPasswordResetMails(PasswordResetMail passwordResetMail) {
        return new NotificationPasswordResetMails(passwordResetMail);
    }

    @Bean
    KpiObservationQueryService kpiObservationQueryService(KpiObservationRepository repository) {
        return new KpiObservationQueryService(repository);
    }
}
