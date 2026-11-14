package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * システムが有人案件を起票する（利用者の操作でない内部の経路。拒否した主要実績・経路候補なしの起票。Bolt 40-13・40-17。Bolt 40-18 で
 * {@link ServiceCaseCommandService} から分けた）。**認可を通らない**ので、画面（{@code interfaces}）からは呼ばない（{@code SystemOpenArchitectureTest}
 * が禁じる）。役割の判定をしない以外は利用者の起票と同じ手順（{@link ServiceCaseIntake}）で、次の 2 点が違う。
 *
 * <ul>
 *   <li>起票元キーが同じなら、案件を終結した後でも新しく起票せず、起票済みの受付番号を返す（イベントの再配信の冪等。一意制約が並行の配信も
 *       守る。W8 の週全体レビュー A）。
 *   <li>営業カレンダーが未登録なら、既定のカレンダーで期限を計算して起票する（W8 の週全体レビュー B。台帳 D-163。仮）。
 * </ul>
 *
 * <p>組み立ては {@code TrackingConfiguration} が担う。
 */
public class SystemServiceCaseOpener {

    private final ServiceCaseRepository repository;
    private final ServiceCaseIntake intake;

    public SystemServiceCaseOpener(
            ServiceCaseRepository repository,
            ServiceCaseNumberIssuer numberIssuer,
            BusinessCalendarRepository calendars,
            Clock clock) {
        this.repository = repository;
        this.intake = new ServiceCaseIntake(repository, numberIssuer, calendars, clock);
    }

    /**
     * 起票する。
     *
     * @param requestedBy 起票者として記録する利用者（起こした操作の主）
     * @param originRef 起票元キー（同じ起票元で一意）
     */
    @Transactional
    public OpenServiceCaseOutcome open(ServiceCaseDraft draft, UserId requestedBy, String originRef) {
        Optional<CaseNumber> existing = repository.findByOriginRef(originRef);
        if (existing.isPresent()) {
            return new OpenServiceCaseOutcome.Duplicate(existing.get());
        }
        return intake.open(draft, requestedBy, Optional.of(originRef));
    }
}
