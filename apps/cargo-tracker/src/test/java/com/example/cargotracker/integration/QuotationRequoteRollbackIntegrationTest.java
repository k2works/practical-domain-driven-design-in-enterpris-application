package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 再見積りの書く順（旧版の更新が先、新しい見積りの保存が後）で、新しい見積りの保存が失敗したら、旧版の更新ごと
 * ロールバックすることを PostgreSQL で確かめる（Q-INV-18。Bolt 11 レビュー R-14）。このテストはコミットするため、
 * 確かめるのはこのテストで提出した輸送要求に限る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuotationRequoteRollbackIntegrationTest {

    private static final UserId STAFF = new UserId(UUID.randomUUID());

    @Autowired
    TransportRequestCommandService transportRequestCommandService;

    @Autowired
    TransportRequestReviewService reviewService;

    @Autowired
    QuotationCommandService quotationCommandService;

    @Autowired
    StaffQuotationQueryService staffQuotationQueryService;

    @MockitoSpyBean
    QuotationRepository quotationRepository;

    @Test
    void 新しい見積りの保存が失敗すると旧版の置換もロールバックする() {
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) transportRequestCommandService.submit(new SubmitTransportRequestCommand(
                        new CompanyId(UUID.randomUUID()), STAFF, ShipmentTermsFixture.completeInput()));
        TransportRequestNumber number = submitted.number();
        reviewService.approve(new ApproveTransportRequestCommand(number, 1, STAFF, "根拠"));
        quotationCommandService.calculate(new CalculateQuotationCommand(number, QuotationFixture.completeInput()));
        quotationCommandService.present(new PresentQuotationCommand(number, 1, STAFF));
        doThrow(new DuplicateQuotationException(new TransportRequestId(UUID.randomUUID()), 2))
                .when(quotationRepository)
                .save(argThat(quotation -> quotation != null && quotation.quotationNo() == 2));
        RequoteQuotationCommand requote = new RequoteQuotationCommand(number, 1, QuotationFixture.completeInput());

        assertThatThrownBy(() -> quotationCommandService.requote(requote))
                .isInstanceOf(DuplicateQuotationException.class);

        assertThat(staffQuotationQueryService.find(number, 1)).hasValueSatisfying(old -> {
            assertThat(old.status()).isEqualTo(QuotationStatus.PRESENTED);
            assertThat(old.replacedBy()).isEmpty();
        });
        assertThat(staffQuotationQueryService.find(number, 2)).isEmpty();
    }
}
