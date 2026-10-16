package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 利用者のリポジトリ（送信ポート）。Bolt 34 で役割の変更（版を条件にした更新）を足した。
 */
public interface UserRepository {

    /** 利用者と役割を追加する。メールアドレスが既にあれば {@link DuplicateEmailException}。 */
    void add(User user);

    Optional<User> findByEmail(EmailAddress email);

    Optional<User> findById(UserId id);

    /**
     * 利用者の password のハッシュを書き、版を上げる（Bolt 33a）。版を条件にしない（同じ利用者の同時の変更は、再設定のトークンの条件付きの
     * 使用で直列になる）。書けたら true（利用者がいなければ false）。
     */
    boolean changePassword(User user);

    /**
     * 利用者の役割を変え、版を上げる（Bolt 34。UC-16 の 4b）。期待する版を条件にした更新が 0 件なら何もせず false（ほかの更新が先に
     * 入った）。書けたら、外した役割の行を消し、足した役割の行を付与した操作者と時刻付きで入れて true。
     *
     * @param changed 役割を変えた利用者（{@link User#changeRoles}）
     * @param expectedVersion 期待する版
     * @param grantedBy 付与した操作者
     * @param at 付与の時刻
     */
    boolean changeRoles(User changed, long expectedVersion, UserId grantedBy, UtcInstant at);

    /** S-20 の一覧の行を、所属企業の名前・表示名の順に返す（Bolt 34）。`password_hash` を読まない。 */
    List<UserSummary> findSummaries();

    /**
     * S-20 の一覧の行のうち、その企業の利用者だけを、所属企業の名前・表示名の順に返す（Bolt 34b。管理範囲）。企業がなければ空。
     * `password_hash` を読まない。
     */
    List<UserSummary> findSummariesIn(Set<CompanyId> companies);

    /** S-20 の詳細を返す（Bolt 34）。`password_hash` を読まない。 */
    Optional<UserDetail> findDetail(UserId id);
}
