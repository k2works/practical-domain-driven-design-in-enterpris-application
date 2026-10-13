package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.LoginAttemptService;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** 入口の単体テストのための、ロックの有無を決めて呼び出しを覚える偽のログインの試行のサービス（Bolt 32）。 */
class FakeLoginAttemptService extends LoginAttemptService {

    private final boolean locked;
    final List<String> calls = new ArrayList<>();

    FakeLoginAttemptService(boolean locked) {
        super(null, null, null, Clock.systemUTC(), null);
        this.locked = locked;
    }

    @Override
    public boolean isLocked(UserId userId) {
        return locked;
    }

    @Override
    public void recordFailure(UserId userId, CompanyId companyId, AuthenticationRejection reason) {
        calls.add("failure:" + reason);
    }

    @Override
    public void recordLockedAttempt(UserId userId, CompanyId companyId) {
        calls.add("locked");
    }

    @Override
    public void recordSuccess(UserId userId, CompanyId companyId) {
        calls.add("success");
    }
}
