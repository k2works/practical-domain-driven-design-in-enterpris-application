package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.acceptance.InMemoryRecoveryCodeRepository;
import com.example.cargotracker.identity.acceptance.InMemoryTotpCredentialRepository;
import com.example.cargotracker.identity.domain.events.TotpEnrolled;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.EnrollingUser;
import com.example.cargotracker.identity.domain.model.valueobjects.NormalizedRecoveryCode;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 初回の TOTP の登録（Bolt 31b、US-18 AC7、SEC-09、IA-INV-10・11）。登録済みなら拒み、最初のコードで確かめてから資格と回復コード
 * 10 個を保存し、監査記録を書き、回復コードの平文を一度だけ返す。登録の途中の暗号文は持ち主が同じ限り使い続ける。
 */
class TotpEnrollmentServiceTest {

    /** 今の刻みは 100（epoch 3000 秒 / 30）。 */
    private static final Instant NOW = Instant.ofEpochSecond(3_000);

    /** 偽の秘密の発行。暗号文は持ち主の ID を含み、別の持ち主では表示の値を作らない（AAD の代わり）。 */
    private static class FakeIssuer implements TotpSecretIssuer {
        private final AtomicInteger issued = new AtomicInteger();

        @Override
        public EncryptedSecret issue(UserId owner) {
            return new EncryptedSecret("v1:" + owner.value() + ":" + issued.incrementAndGet());
        }

        @Override
        public TotpProvisioning provisioning(UserId owner, EncryptedSecret secret, String accountName) {
            requireOwner(owner, secret);
            return new TotpProvisioning("otpauth://" + accountName, "KEY " + secret.value(), "<svg/>");
        }
    }

    private static void requireOwner(UserId owner, EncryptedSecret secret) {
        if (!secret.value().contains(owner.value().toString())) {
            throw new UnreadableCredentialException("別の利用者の暗号文");
        }
    }

    /** 偽の生成器。持ち主の違う暗号文は復号できず、コードは刻みの番号を 6 桁にしたもの。 */
    private static final TotpCodeGenerator GENERATOR = (owner, secret, step) -> {
        requireOwner(owner, secret);
        return String.format("%06d", step % 1_000_000);
    };

    private static final RecoveryCodeHasher HASHER = new RecoveryCodeHasher() {
        @Override
        public String hash(String code) {
            return "hashed:" + code;
        }

        @Override
        public boolean matches(String code, String hash) {
            return hash.equals("hashed:" + code);
        }
    };

    private static final List<String> RECOVERY_CODES = IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
            .mapToObj(i -> String.format("aaaa-%04d", i))
            .toList();

    /** 生成器が返す値（Bolt 33f から値）。 */
    private static final List<NormalizedRecoveryCode> GENERATED =
            RECOVERY_CODES.stream().map(NormalizedRecoveryCode::new).toList();

    private final InMemoryTotpCredentialRepository credentials = new InMemoryTotpCredentialRepository();
    private final InMemoryRecoveryCodeRepository recoveryCodes = new InMemoryRecoveryCodeRepository();
    private final List<AuditRecord> audit = new ArrayList<>();
    private final AuditRecordRepository auditRecords = new AuditRecordRepository() {
        @Override
        public void append(AuditRecord auditRecord) {
            audit.add(auditRecord);
        }

        @Override
        public List<AuditRecord> findByActorUserId(UserId actor) {
            return audit.stream().filter(r -> actor.equals(r.actorUserId())).toList();
        }

        @Override
        public Optional<SuspensionRecord> findLatestUserSuspension(UserId target) {
            return Optional.empty();
        }
    };
    private final FakeIssuer issuer = new FakeIssuer();
    /** 出したイベント（Bolt 38d の DE-28）。 */
    private final List<Object> published = new ArrayList<>();
    /** 出した時点でトランザクションの中にいたか（Bolt 38d の開発レビュー M3。監査と同じトランザクションで出す）。 */
    private final List<Boolean> publishedInTransaction = new ArrayList<>();

    private void publish(Object event) {
        published.add(event);
        publishedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private final RecoveryCodeGenerator recoveryCodeGenerator = () -> GENERATED;
    /** トランザクションを持たない単体テストのための、何もしないトランザクションマネージャー。 */
    private static final TransactionTemplate NO_TRANSACTION =
            new TransactionTemplate(new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    // 何もしない
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    // 何もしない
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                    // 何もしない
                }
            });

    private final TotpEnrollmentService service = new TotpEnrollmentService(
            credentials,
            recoveryCodes,
            auditRecords,
            issuer,
            recoveryCodeGenerator,
            HASHER,
            GENERATOR,
            this::publish,
            Clock.fixed(NOW, ZoneOffset.UTC),
            NO_TRANSACTION);

    private final EnrollingUser actor = new EnrollingUser(
            new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), EmailAddress.of("shipper@example.com"));

    private EncryptedSecret started(Optional<EncryptedSecret> pending) {
        EnrollmentStart start = service.begin(actor, pending);
        assertThat(start).isInstanceOf(EnrollmentStart.Started.class);
        return ((EnrollmentStart.Started) start).secret();
    }

    @Test
    void 登録を始めると持ち主に結び付いた秘密と表示の値を返す() {
        EnrollmentStart start = service.begin(actor, Optional.empty());

        assertThat(start).isInstanceOfSatisfying(EnrollmentStart.Started.class, started -> {
            assertThat(started.secret().value()).contains(actor.userId().value().toString());
            assertThat(started.provisioning().otpauthUri()).isEqualTo("otpauth://shipper@example.com");
        });
    }

    @Test
    void 登録の途中の暗号文は持ち主が同じ限り使い続け別の利用者の暗号文なら新しく発行する() {
        EncryptedSecret first = started(Optional.empty());

        assertThat(started(Optional.of(first))).as("再読み込み・2 つのタブで秘密を変えない").isEqualTo(first);

        EncryptedSecret othersPending = issuer.issue(new UserId(UUID.randomUUID()));
        EncryptedSecret renewed = started(Optional.of(othersPending));
        assertThat(renewed).isNotEqualTo(othersPending);
        assertThat(renewed.value()).contains(actor.userId().value().toString());
    }

    @Test
    void 登録済みの利用者は始められず完了もできない() {
        credentials.add(TotpCredential.enroll(
                actor.userId(), new EncryptedSecret("v1:" + actor.userId().value() + ":0"), new UtcInstant(NOW)));

        assertThat(service.begin(actor, Optional.empty())).isInstanceOf(EnrollmentStart.AlreadyEnrolled.class);
        assertThat(service.complete(actor, issuer.issue(actor.userId()), "000100"))
                .isInstanceOf(EnrollmentResult.AlreadyEnrolled.class);
    }

    @Test
    void 誤ったコードでは資格も回復コードも保存しない() {
        EncryptedSecret secret = started(Optional.empty());

        assertThat(service.complete(actor, secret, "000098")).isInstanceOf(EnrollmentResult.CodeRejected.class);

        assertThat(credentials.findByUserId(actor.userId())).isEmpty();
        assertThat(recoveryCodes.findUnusedByUserId(actor.userId())).isEmpty();
        assertThat(audit).isEmpty();
    }

    @Test
    void 正しいコードで資格と回復コード10個と監査記録を保存し回復コードの平文を一度だけ返す() {
        EncryptedSecret secret = started(Optional.empty());

        EnrollmentResult result = service.complete(actor, secret, "000100");

        assertThat(result)
                .isInstanceOfSatisfying(
                        EnrollmentResult.Enrolled.class,
                        enrolled -> assertThat(enrolled.recoveryCodes()).containsExactlyElementsOf(RECOVERY_CODES));
        TotpCredential credential = credentials.findByUserId(actor.userId()).orElseThrow();
        assertThat(credential.secret()).isEqualTo(secret);
        assertThat(credential.lastUsedStep())
                .as("登録のコードは使ったことにしない（A-02 でもう一度使える。確認ポイント 2）")
                .isZero();
        List<RecoveryCode> saved = recoveryCodes.findUnusedByUserId(actor.userId());
        assertThat(saved)
                .hasSize(RecoveryCode.CODES_PER_USER)
                .as("平文ではなくハッシュを保存する")
                .noneMatch(code -> code.codeHash().startsWith("aaaa"));
        for (String plain : RECOVERY_CODES) {
            assertThat(saved)
                    .anyMatch(code -> code.matches(RecoveryCode.normalize(plain).orElseThrow(), HASHER));
        }
        assertThat(audit).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.TOTP_ENROLLED);
            assertThat(entry.actorUserId()).isEqualTo(actor.userId());
            assertThat(entry.actorCompanyId()).isEqualTo(actor.companyId());
        });
        // Bolt 38d の行 3・14: 監査記録の ID と監査の時刻で DE-28 を出す
        assertThat(published)
                .containsExactly(new TotpEnrolled(audit.getFirst().id(), actor.userId(), new UtcInstant(NOW)));
        assertThat(publishedInTransaction).as("監査と同じトランザクションの中で出す").containsExactly(true);
    }

    @Test
    void 登録しなければ登録のイベントを出さない() {
        // Bolt 38d の行 4c（AlreadyEnrolled・SecretUnusable・CodeRejected）
        EncryptedSecret secret = started(Optional.empty());
        assertThat(service.complete(actor, secret, "000098")).isInstanceOf(EnrollmentResult.CodeRejected.class);
        assertThat(service.complete(actor, issuer.issue(new UserId(UUID.randomUUID())), "000100"))
                .isInstanceOf(EnrollmentResult.SecretUnusable.class);
        credentials.add(TotpCredential.enroll(actor.userId(), secret, new UtcInstant(NOW)));
        assertThat(service.complete(actor, secret, "000100")).isInstanceOf(EnrollmentResult.AlreadyEnrolled.class);

        assertThat(published).isEmpty();
    }

    @Test
    void 別の利用者の暗号文や壊れた暗号文では保存せず使えない秘密として返す() {
        EncryptedSecret othersSecret = issuer.issue(new UserId(UUID.randomUUID()));

        assertThat(service.complete(actor, othersSecret, "000100")).isInstanceOf(EnrollmentResult.SecretUnusable.class);
        assertThat(credentials.findByUserId(actor.userId())).isEmpty();
    }

    @Test
    void 同時の登録で後になり資格の追加が主キーの違反になったら何も保存せず登録済みを返す() {
        // 先の登録がまだコミットしていない状態（読むと見えないが、追加は主キーの違反になる）
        InMemoryTotpCredentialRepository racing = new InMemoryTotpCredentialRepository() {
            @Override
            public Optional<TotpCredential> findByUserId(UserId userId) {
                return Optional.empty();
            }
        };
        racing.add(TotpCredential.enroll(
                actor.userId(), new EncryptedSecret("v1:" + actor.userId().value() + ":0"), new UtcInstant(NOW)));
        TotpEnrollmentService racingService = new TotpEnrollmentService(
                racing,
                recoveryCodes,
                auditRecords,
                issuer,
                recoveryCodeGenerator,
                HASHER,
                GENERATOR,
                this::publish,
                Clock.fixed(NOW, ZoneOffset.UTC),
                NO_TRANSACTION);

        assertThat(racingService.complete(actor, issuer.issue(actor.userId()), "000100"))
                .isInstanceOf(EnrollmentResult.AlreadyEnrolled.class);
        assertThat(recoveryCodes.findUnusedByUserId(actor.userId())).isEmpty();
        assertThat(audit).isEmpty();
    }

    @Test
    void 登録の結果の文字列に回復コードの平文を出さない() {
        assertThat(new EnrollmentResult.Enrolled(RECOVERY_CODES).toString()).doesNotContain("aaaa");
    }

    @Test
    void 読めない要素でない実装の誤りは使えない秘密に見せず投げる() {
        // 確認ポイント 5（Bolt 31d）
        TotpCodeGenerator broken = (owner, secret, step) -> {
            throw new IllegalArgumentException("値の検証の誤り");
        };
        TotpEnrollmentService brokenService = new TotpEnrollmentService(
                credentials,
                recoveryCodes,
                auditRecords,
                issuer,
                recoveryCodeGenerator,
                HASHER,
                broken,
                this::publish,
                Clock.fixed(NOW, ZoneOffset.UTC),
                NO_TRANSACTION);
        EncryptedSecret secret = started(Optional.empty());

        assertThatThrownBy(() -> brokenService.complete(actor, secret, "000100"))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 登録の途中の暗号文で読めない要素でない実装の誤りが起きたら新しく発行し直さず投げる() {
        // 開始の処理の catch も読めない要素だけに狭めたことを固定する（Bolt 31d の開発レビュー）
        TotpSecretIssuer broken = new FakeIssuer() {
            @Override
            public TotpProvisioning provisioning(UserId owner, EncryptedSecret secret, String accountName) {
                throw new IllegalArgumentException("値の検証の誤り");
            }
        };
        TotpEnrollmentService brokenService = new TotpEnrollmentService(
                credentials,
                recoveryCodes,
                auditRecords,
                broken,
                recoveryCodeGenerator,
                HASHER,
                GENERATOR,
                this::publish,
                Clock.fixed(NOW, ZoneOffset.UTC),
                NO_TRANSACTION);
        Optional<EncryptedSecret> pending = Optional.of(issuer.issue(actor.userId()));

        assertThatThrownBy(() -> brokenService.begin(actor, pending))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
