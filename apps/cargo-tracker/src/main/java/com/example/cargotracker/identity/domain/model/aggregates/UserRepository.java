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
}
