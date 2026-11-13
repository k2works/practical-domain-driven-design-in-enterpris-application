package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentServiceCaseUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import com.example.cargotracker.tracking.domain.model.entities.CaseProgress;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.infrastructure.persistence.ServiceCaseChildRows.AnswerRow;
import com.example.cargotracker.tracking.infrastructure.persistence.ServiceCaseChildRows.ProgressRow;
import com.example.cargotracker.tracking.infrastructure.persistence.ServiceCaseChildRows.SourceRow;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 有人案件のリポジトリの MyBatis 実装（Bolt 40-6）。更新は版が一致する行だけ（楽観ロック）。進捗と回答は追記専用で、表にない番号の行だけを足し、
 * 既存の回答には顧客の回答（未設定のときだけ）を書く。出典は起票のときだけ書く。呼び手のトランザクションの中で動く。
 */
@Repository
public class MyBatisServiceCaseRepository implements ServiceCaseRepository {

    private final ServiceCaseMapper mapper;
    private final Clock clock;

    public MyBatisServiceCaseRepository(ServiceCaseMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void save(ServiceCase serviceCase, UserId actor) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        mapper.insertCase(toRow(serviceCase, actor, now, offset(serviceCase.openedAt())));
        int sourceNo = 1;
        for (Source source : serviceCase.sources()) {
            mapper.insertSource(new SourceRow(
                    serviceCase.number().value(),
                    sourceNo++,
                    source.kind().name(),
                    source.reference(),
                    offset(source.acquiredAt())));
        }
        appendChildren(serviceCase, 0, 0);
    }

    @Override
    public void update(ServiceCase serviceCase, UserId actor) {
        String number = serviceCase.number().value();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (mapper.updateCase(toRow(serviceCase, actor, now, now)) == 0) {
            throw new ConcurrentServiceCaseUpdateException(serviceCase.number(), serviceCase.aggregateVersion());
        }
        appendChildren(serviceCase, mapper.countProgress(number), mapper.countAnswers(number));
        for (CaseAnswer answer : serviceCase.answers()) {
            if (answer.customerResponse().isPresent()) {
                mapper.updateCustomerResponse(
                        number,
                        answer.answerNo(),
                        answer.customerResponse().orElseThrow(),
                        offset(answer.customerRespondedAt().orElseThrow()));
            }
        }
    }

    @Override
    public Optional<ServiceCase> findByNumber(CaseNumber number) {
        return mapper.selectCase(number.value()).map(row -> toAggregate(row, number));
    }

    /** 表にある数より後の番号の進捗と回答を足す（追記専用）。 */
    private void appendChildren(ServiceCase serviceCase, int existingProgress, int existingAnswers) {
        String number = serviceCase.number().value();
        for (CaseProgress progress : serviceCase.progress()) {
            if (progress.progressNo() > existingProgress) {
                mapper.insertProgress(new ProgressRow(
                        number,
                        progress.progressNo(),
                        progress.content(),
                        progress.nextUpdateAt()
                                .map(MyBatisServiceCaseRepository::offset)
                                .orElse(null),
                        progress.updatedBy().value(),
                        offset(progress.updatedAt())));
            }
        }
        for (CaseAnswer answer : serviceCase.answers()) {
            if (answer.answerNo() > existingAnswers) {
                mapper.insertAnswer(new AnswerRow(
                        number,
                        answer.answerNo(),
                        answer.content(),
                        answer.answeredBy().value(),
                        offset(answer.answeredAt()),
                        answer.customerResponse().orElse(null),
                        answer.customerRespondedAt()
                                .map(MyBatisServiceCaseRepository::offset)
                                .orElse(null)));
            }
        }
    }

    private static ServiceCaseRow toRow(
            ServiceCase serviceCase, UserId actor, OffsetDateTime updatedAt, OffsetDateTime createdAt) {
        return new ServiceCaseRow(
                serviceCase.number().value(),
                serviceCase.kind().name(),
                serviceCase.shipperCompanyId().map(CompanyId::value).orElse(null),
                serviceCase.bookingId().orElse(null),
                serviceCase.trackingNumber().map(TrackingNumber::value).orElse(null),
                serviceCase.cause(),
                serviceCase.priority().name(),
                serviceCase.department(),
                serviceCase.assignee().map(UserId::value).orElse(null),
                serviceCase
                        .receivedAt()
                        .map(MyBatisServiceCaseRepository::offset)
                        .orElse(null),
                offset(serviceCase.receiveDueAt()),
                offset(serviceCase.answerDueAt()),
                offset(serviceCase.nextUpdateAt()),
                serviceCase.oldVersionRef().orElse(null),
                serviceCase.newVersionRef().orElse(null),
                serviceCase.manualSourceRef().orElse(null),
                serviceCase.reconciliationRequired(),
                serviceCase.reconciliationId().orElse(null),
                serviceCase
                        .acknowledgedAt()
                        .map(MyBatisServiceCaseRepository::offset)
                        .orElse(null),
                serviceCase.status().name(),
                serviceCase.aggregateVersion(),
                createdAt,
                actor.value(),
                updatedAt,
                actor.value());
    }

    private ServiceCase toAggregate(ServiceCaseRow row, CaseNumber number) {
        List<Source> sources = mapper.selectSources(row.caseNumber()).stream()
                .map(source -> new Source(
                        SourceKind.valueOf(source.sourceKind()), source.sourceRef(), instant(source.acquiredAt())))
                .toList();
        List<CaseProgress> progress = mapper.selectProgress(row.caseNumber()).stream()
                .map(entry -> new CaseProgress(
                        entry.progressNo(),
                        entry.content(),
                        Optional.ofNullable(entry.nextUpdateAt()).map(MyBatisServiceCaseRepository::instant),
                        new UserId(entry.updatedBy()),
                        instant(entry.updatedAt())))
                .toList();
        List<CaseAnswer> answers = mapper.selectAnswers(row.caseNumber()).stream()
                .map(entry -> new CaseAnswer(
                        entry.answerNo(),
                        entry.content(),
                        new UserId(entry.answeredBy()),
                        instant(entry.answeredAt()),
                        Optional.ofNullable(entry.customerResponse()),
                        Optional.ofNullable(entry.customerRespondedAt()).map(MyBatisServiceCaseRepository::instant)))
                .toList();
        return ServiceCase.reconstitute(
                number,
                ServiceCaseKind.valueOf(row.kind()),
                ServicePriority.valueOf(row.priority()),
                row.cause(),
                row.department(),
                Optional.ofNullable(row.shipperCompanyId()).map(CompanyId::new),
                Optional.ofNullable(row.bookingId()),
                Optional.ofNullable(row.trackingNumber()).map(TrackingNumber::new),
                sources,
                Optional.ofNullable(row.oldVersionRef()),
                Optional.ofNullable(row.newVersionRef()),
                Optional.ofNullable(row.manualSourceRef()),
                row.reconciliationRequired(),
                Optional.ofNullable(row.reconciliationId()),
                Optional.ofNullable(row.assigneeId()).map(UserId::new),
                Optional.ofNullable(row.receivedAt()).map(MyBatisServiceCaseRepository::instant),
                Optional.ofNullable(row.acknowledgedAt()).map(MyBatisServiceCaseRepository::instant),
                instant(row.receiveDueAt()),
                instant(row.answerDueAt()),
                instant(row.nextUpdateAt()),
                ServiceCaseStatus.valueOf(row.status()),
                progress,
                answers,
                instant(row.createdAt()),
                row.version());
    }

    private static OffsetDateTime offset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }

    private static UtcInstant instant(OffsetDateTime offset) {
        return new UtcInstant(offset.toInstant());
    }
}
