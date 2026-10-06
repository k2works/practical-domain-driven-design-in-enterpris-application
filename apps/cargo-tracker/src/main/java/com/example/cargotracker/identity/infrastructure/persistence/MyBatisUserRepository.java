package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 利用者のリポジトリの MyBatis 実装。
 */
@Repository
public class MyBatisUserRepository implements UserRepository {

    @Override
    public void add(User user) {
        // Red の骨組み
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return Optional.empty();
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.empty();
    }
}
