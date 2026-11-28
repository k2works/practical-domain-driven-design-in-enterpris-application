package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * 業務のイベントから荷主担当者へのメールまで（US-22 AC1・AC3・AC4、N-INV-02。Bolt 43-1。台帳 D-166）。本物の PostgreSQL と Mailpit で確かめる。
 * その企業の利用中の荷主担当者だけに届き、停止中の利用者・荷主担当者でない利用者・ほかの企業には届かない。同じイベントを 2 回受けても 1 通。
 */
@IdentityIntegrationTest
class ShipperNotificationIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    ShipperNotificationEventHandler handler;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private Company shipperCompany() {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A 社", CompanyKind.SHIPPER, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(company));
        return company;
    }

    private String addUser(Company company, Role role, UserStatus status) {
        UserId id = new UserId(UUID.randomUUID());
        String address = "shipper-notify-" + id.value() + "@example.com";
        transactionTemplate.executeWithoutResult(status2 -> users.add(
                User.of(id, company.id(), EmailAddress.of(address), "担当 太郎", "{noop}unused", status, Set.of(role))));
        return address;
    }

    private static BookingConfirmed confirmed(Company company) {
        return new BookingConfirmed(
                UUID.randomUUID(),
                1,
                "CTABCDEFGH2345",
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "TR-2026-0001",
                "RC-2026-0001",
                1,
                new UtcInstant(Instant.parse("2026-11-16T01:00:00Z")),
                1L,
                company.id(),
                null);
    }

    @Test
    void 本予約の確定を受けるとその企業の利用中の荷主担当者だけに導線つきのメールが届き同じイベントは1通だけ() {
        Company company = shipperCompany();
        Company other = shipperCompany();
        String active = addUser(company, Role.SHIPPER, UserStatus.ACTIVE);
        String suspended = addUser(company, Role.SHIPPER, UserStatus.SUSPENDED);
        String sales = addUser(company, Role.SALES, UserStatus.ACTIVE);
        String otherCompany = addUser(other, Role.SHIPPER, UserStatus.ACTIVE);
        BookingConfirmed event = confirmed(company);

        handler.on(event);
        handler.on(event);

        MailpitClient mailpit = new MailpitClient(mailpitContainer);
        List<ReceivedMail> mails = mailpit.awaitMessagesTo(active, 1, WAIT);
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】本予約が確定しました");
            assertThat(mail.text()).contains("TR-2026-0001").contains("/customer/bookings");
            assertThat(mail.text()).doesNotContain("料金").doesNotContain("円");
        });
        assertThat(mailpit.messagesTo(suspended)).isEmpty();
        assertThat(mailpit.messagesTo(sales)).isEmpty();
        assertThat(mailpit.messagesTo(otherCompany)).isEmpty();
    }
}
