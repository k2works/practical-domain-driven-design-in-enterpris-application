package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.RecordInquiryCommand;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordLookup;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 電話・メールで受けた照会を記録し、必要なら有人案件へ引き継ぐ（US-11 AC2、IR-INV-02、KPI-02。Bolt 40-15。台帳 D-162）。引継ぎが不要でも
 * 記録する。引き継ぐときは同じトランザクションで有人案件を起票し（種類 顧客照会、優先度 通常）、照会記録に受付番号を残す。未終結の同じ内容の
 * 案件があれば新しく起票せず、その受付番号に引き継ぐ。カスタマーサポートだけが記録できる。追跡番号の荷主企業は追跡記録から決め、担当企業の範囲で確かめる
 * （範囲の外とない追跡番号は同じ結果）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class InquiryRecordingService {

    private final TrackingRecordScope scope;
    private final InquiryRecordRepository inquiries;
    private final ServiceCaseCommandService cases;
    private final Clock clock;

    public InquiryRecordingService(
            TrackingRecordScope scope,
            InquiryRecordRepository inquiries,
            ServiceCaseCommandService cases,
            Clock clock) {
        this.scope = scope;
        this.inquiries = inquiries;
        this.cases = cases;
        this.clock = clock;
    }

    /** 照会を記録する。引き継ぐ指定があれば有人案件を起票（または既存に引き継ぎ）、受付番号と期限を返す。 */
    @Transactional
    public RecordInquiryOutcome record(AuthenticatedActor actor, RecordInquiryCommand command) {
        if (!actor.hasRole(Role.CUSTOMER_SUPPORT)) {
            return new RecordInquiryOutcome.NotPermitted();
        }
        TrackingRecord trackingRecord;
        switch (scope.find(command.trackingNumber(), actor)) {
            case TrackingRecordLookup.Found(TrackingRecord found) -> trackingRecord = found;
            case TrackingRecordLookup.NotFound _, TrackingRecordLookup.OutOfScope _ -> {
                return new RecordInquiryOutcome.TrackingNotFound();
            }
        }
        UtcInstant receivedAt = command.receivedAt().orElseGet(() -> new UtcInstant(clock.instant()));
        Optional<CaseNumber> caseNumber = Optional.empty();
        boolean existing = false;
        Optional<UtcInstant> answerDueAt = Optional.empty();
        Optional<UtcInstant> nextUpdateAt = Optional.empty();
        if (command.handOff()) {
            OpenServiceCaseOutcome opened =
                    cases.open(actor, new OpenServiceCaseCommand(draft(command, trackingRecord, receivedAt)));
            switch (opened) {
                case OpenServiceCaseOutcome.Opened(
                        CaseNumber number,
                        UtcInstant _,
                        UtcInstant answerDue,
                        UtcInstant nextUpdate,
                        Optional<UtcInstant> _) -> {
                    caseNumber = Optional.of(number);
                    answerDueAt = Optional.of(answerDue);
                    nextUpdateAt = Optional.of(nextUpdate);
                }
                case OpenServiceCaseOutcome.Duplicate(CaseNumber duplicate) -> {
                    caseNumber = Optional.of(duplicate);
                    existing = true;
                }
                case OpenServiceCaseOutcome.Incomplete(var missing) -> {
                    return new RecordInquiryOutcome.Incomplete(missing);
                }
                case OpenServiceCaseOutcome.CalendarNotRegistered _ -> {
                    return new RecordInquiryOutcome.CalendarNotRegistered();
                }
                case OpenServiceCaseOutcome.NotPermitted _ -> {
                    return new RecordInquiryOutcome.NotPermitted();
                }
            }
        }
        UUID id = UUID.randomUUID();
        inquiries.save(new InquiryRecord(
                id,
                command.channel(),
                trackingRecord.shipperCompanyId(),
                Optional.of(command.trackingNumber()),
                command.inquirerKind(),
                Optional.empty(),
                receivedAt,
                caseNumber,
                Optional.of(actor.userId())));
        return new RecordInquiryOutcome.Recorded(id, caseNumber, existing, answerDueAt, nextUpdateAt);
    }

    private static ServiceCaseDraft draft(
            RecordInquiryCommand command, TrackingRecord trackingRecord, UtcInstant receivedAt) {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY,
                        ServicePriority.NORMAL,
                        command.content(),
                        command.department())
                .withShipper(trackingRecord.shipperCompanyId())
                .withBooking(trackingRecord.bookingId())
                .withTracking(command.trackingNumber());
        Optional<String> reference =
                command.sourceReference().map(String::strip).filter(text -> !text.isEmpty());
        if (reference.isPresent()) {
            draft = draft.withSources(List.of(new Source(SourceKind.INTERNAL_CHECK, reference.get(), receivedAt)));
        }
        return draft;
    }
}
