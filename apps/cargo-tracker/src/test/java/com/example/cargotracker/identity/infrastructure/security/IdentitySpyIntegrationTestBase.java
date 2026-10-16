package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * spy（{@code @MockitoSpyBean}）を使う identity の統合テストの共通の基底（Bolt 32d）。Spring のコンテキストは bean の差し替えの組
 * （spy はフィールド名と置き場所を含む）と {@code @Import} の組で区別されるので、spy を使うクラスは同じフィールドの組と共通の時計を
 * このクラスから継ぎ、コンテキストを 1 つにする。spy は既定で各テストの後に元に戻る（{@code MockReset.AFTER}）。時計の時刻は
 * 継ぐクラスが各テストの前に置く。
 */
@IdentityIntegrationTest
abstract class IdentitySpyIntegrationTestBase {

    @MockitoSpyBean
    protected UserStandingRepository standings;

    @MockitoSpyBean
    protected UserRepository users;

    @MockitoSpyBean
    protected CompanyRepository companies;

    @MockitoSpyBean
    protected AuditRecordRepository auditRecords;

    @MockitoSpyBean
    protected TotpCodeGenerator totpCodes;

    @MockitoSpyBean
    protected LoginFailureRepository loginFailures;

    @MockitoSpyBean
    protected UserAssignmentRepository assignments;
}
