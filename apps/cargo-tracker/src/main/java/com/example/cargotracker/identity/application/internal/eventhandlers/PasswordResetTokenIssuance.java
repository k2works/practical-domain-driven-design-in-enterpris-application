package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 再設定のトークンの発行（Bolt 38b2c。再設定のハンドラー（Bolt 33）から、利用者の招待のハンドラーと共有するために抜き出した。IA-INV-12、
 * SEC-08）。利用者の行の占有で発行を直列にし（同時の 2 つの契機で 2 通送らない）、同じ契機の ID のトークンがあれば発行しない（再配信）。
 * 用途が繰り返しの抑えを数えるなら、前の発行から 1 分たっていなければ発行しない（抑えは占有の後に見る）。利用者が有効でなければ発行しない。
 * 発行するときは前の未使用のトークンを無効にする。発行しなかった理由は INFO のログに出す。リンクの値（平文）は返す値と送信の要求にだけあり、
 * 表・発行記録・ログに残らない。Bean にせず、各ハンドラーがコンストラクターの引数から作る（前例 {@code LinkMailDelivery}）。
 */
final class PasswordResetTokenIssuance {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetTokenIssuance.class);

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordResetTokenGenerator generator;
    private final PasswordResetTokenHasher hasher;
    private final Clock clock;

    PasswordResetTokenIssuance(
            PasswordResetTokenRepository tokens,
            UserRepository users,
            CompanyRepository companies,
            PasswordResetTokenGenerator generator,
            PasswordResetTokenHasher hasher,
            Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.companies = companies;
        this.generator = generator;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * トークンを発行し、リンクを返す。呼ぶ側のトランザクションの中で呼ぶ（占有はその終わりまで続く）。
     *
     * @param purpose 用途
     * @param requestId 契機の ID（再設定の依頼、利用者の登録）。トークンの {@code request_id} になる
     * @param userId 宛先の利用者
     * @param linkPrefix リンクの接頭辞（リンクはこれにトークンを続ける）
     * @return 発行したリンク。発行しなかったら空
     */
    Optional<IssuedLink> issue(PasswordResetTokenPurpose purpose, UUID requestId, UserId userId, String linkPrefix) {
        if (!tokens.lockForIssue(userId)) {
            return skipped(purpose, "利用者がいない", requestId);
        }
        if (tokens.existsByRequestId(requestId)) {
            return skipped(purpose, "同じ契機の再配信", requestId);
        }
        UtcInstant now = new UtcInstant(clock.instant());
        if (purpose.throttlesRepeatedIssue()
                && !PasswordResetToken.allowsIssue(tokens.findLastIssuedAt(userId, purpose), now)) {
            return skipped(purpose, "前の発行から 1 分たっていない", requestId);
        }
        Optional<User> user = users.findById(userId).filter(this::isActive);
        if (user.isEmpty()) {
            return skipped(purpose, "契機の後に利用者が無効になった", requestId);
        }
        tokens.invalidateUnused(userId, now);
        String token = generator.generate();
        PasswordResetToken issued = PasswordResetToken.issue(purpose, requestId, userId, hasher.hash(token), now);
        tokens.add(issued);
        return Optional.of(new IssuedLink(issued, user.get().email(), linkPrefix + token));
    }

    private static Optional<IssuedLink> skipped(PasswordResetTokenPurpose purpose, String reason, UUID requestId) {
        LOG.info("{} のトークンを発行しなかった（{}）: 契機 {}", purpose, reason, requestId);
        return Optional.empty();
    }

    private boolean isActive(User user) {
        Optional<Company> company = companies.findById(user.companyId());
        return company.isPresent()
                && user.authenticationRejection(company.get()).isEmpty();
    }

    /**
     * 発行したリンク。
     *
     * @param token 発行したトークン（ID と期限を送信の要求に使う）
     * @param email 宛先のメールアドレス
     * @param link リンク（平文のトークンを含む。送信の要求にだけ渡す）
     */
    record IssuedLink(PasswordResetToken token, EmailAddress email, String link) {

        /** リンクとアドレスを伏せる（ログに出しても平文を残さない）。 */
        @Override
        public String toString() {
            return "IssuedLink[token=" + token.id() + ", email=[メールアドレス], link=[リンク]]";
        }
    }
}
