package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.acceptance.InMemoryRecoveryCodeRepository;
import com.example.cargotracker.identity.acceptance.InMemoryTotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 初回の TOTP の登録（Bolt 31b、US-18 AC7、SEC-09、IA-INV-10・11）。登録済みなら拒み、最初のコードで確かめてから資格と回復コード
 * 10 個を保存し、監査記録を書き、回復コードの平文を一度だけ返す。登録の途中の暗号文は持ち主が同じ限り使い続ける。
 */
class TotpEnrollmentServiceTest {

    /** 今の刻みは 100（epoch 3000 秒 / 30）。 */
    private static final Instant NOW = Instant.ofEpochSecond(3_000);

    /** 偽の秘密の発行。暗号文は持ち主の ID を含み、別の持ち主では表示の値を作らない（AAD の代わり）。 */
    private static final class FakeIssuer implements TotpSecretIssuer {
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
            throw new IllegalArgumentException("別の利用者の暗号文");
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
    };
    private final FakeIssuer issuer = new FakeIssuer();
    private final RecoveryCodeGenerator recoveryCodeGenerator = () -> RECOVERY_CODES;
    private final TotpEnrollmentService service = new TotpEnrollmentService(
            credentials,
            recoveryCodes,
            auditRecords,
            issuer,
            recoveryCodeGenerator,
            HASHER,
            GENERATOR,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final AuthenticatedActor actor = actor();

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                EnumSet.of(Role.SHIPPER),
                "荷主 太郎",
                "荷主 A");
    }

    private EncryptedSecret started(Optional<EncryptedSecret> pending) {
        EnrollmentStart start = service.begin(actor, "shipper@example.com", pending);
        assertThat(start).isInstanceOf(EnrollmentStart.Started.class);
        return ((EnrollmentStart.Started) start).secret();
    }

    @Test
    void 登録を始めると持ち主に結び付いた秘密と表示の値を返す() {
        EnrollmentStart start = service.begin(actor, "shipper@example.com", Optional.empty());

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

        assertThat(service.begin(actor, "shipper@example.com", Optional.empty()))
                .isInstanceOf(EnrollmentStart.AlreadyEnrolled.class);
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
            assertThat(saved).anyMatch(code -> code.matches(plain, HASHER));
        }
        assertThat(audit).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.TOTP_ENROLLED);
            assertThat(entry.actorUserId()).isEqualTo(actor.userId());
            assertThat(entry.actorCompanyId()).isEqualTo(actor.companyId());
        });
    }

    @Test
    void 別の利用者の暗号文や壊れた暗号文では保存せず使えない秘密として返す() {
        EncryptedSecret othersSecret = issuer.issue(new UserId(UUID.randomUUID()));

        assertThat(service.complete(actor, othersSecret, "000100")).isInstanceOf(EnrollmentResult.SecretUnusable.class);
        assertThat(credentials.findByUserId(actor.userId())).isEmpty();
    }
}
