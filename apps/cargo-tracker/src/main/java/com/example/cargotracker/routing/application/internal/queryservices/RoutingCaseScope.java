package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 社内の側の経路設計案件の担当範囲の判定（Bolt 38g。US-16 AC4、IA-INV-02）。経路設計者が扱えるのは、担当企業（荷主企業）の案件だけ。
 * 照会のサービスとコマンドのサービスが最初に呼ぶ（38f の {@code StaffTransportRequestScope} の写し）。判定は
 * {@link AuthenticatedActor#handlesCompany} の 1 か所で、担当企業がなければどの案件も範囲の外（失敗に倒す）。案件の荷主企業は、DE-16 の
 * 処理で見積りの公開 API から写した値（NOT NULL）。一覧の空の守り（担当企業がなければ SQL を流さない。{@code IN ()} は書けない）も
 * ここの 1 か所に置く。主体を持たない読み出し（確定した区間の照会・確定した経路の評価）はこの判定を通さない。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code RoutingConfiguration}。
 */
@Service
public class RoutingCaseScope {

    private final RoutingCaseRepository repository;

    public RoutingCaseScope(RoutingCaseRepository repository) {
        this.repository = repository;
    }

    /** 案件番号の案件を、操作者の担当範囲で引き当てる。 */
    public RoutingCaseLookup find(RoutingCaseNumber number, AuthenticatedActor actor) {
        return repository
                .findByNumber(number)
                .<RoutingCaseLookup>map(routingCase -> actor.handlesCompany(routingCase.shipperCompanyId())
                        ? new RoutingCaseLookup.Found(routingCase)
                        : new RoutingCaseLookup.OutOfScope())
                .orElseGet(RoutingCaseLookup.NotFound::new);
    }

    /**
     * 担当企業で絞った一覧を読む。担当企業がなければ読まずに空を返す。
     *
     * @param query 担当企業（空でない）で絞って読む照会
     */
    public <T> List<T> listWithin(AuthenticatedActor actor, Function<Set<CompanyId>, List<T>> query) {
        if (actor.hasNoAssignedCompanies()) {
            return List.of();
        }
        return query.apply(actor.assignedCompanies());
    }
}
