package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;

/**
 * 利用者のリポジトリ（送信ポート）。利用者の更新は利用者の管理（US-16）とロック（W5）で足す。
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
}
