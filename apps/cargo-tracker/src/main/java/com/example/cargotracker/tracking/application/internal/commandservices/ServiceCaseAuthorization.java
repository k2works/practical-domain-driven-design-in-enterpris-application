package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import java.util.EnumSet;
import java.util.Set;

/**
 * 有人案件の認可の定め（Bolt 40-7。台帳 D-159。仮で業務責任者に確かめる）。役割の集合をここ 1 か所に置く。
 *
 * <p>起票: 社内の運用役割はどの種類も起票できる。荷主担当者は自社の顧客照会・見積り相談だけ（対象の荷主企業が自分の企業）。システム管理者と
 * 監査担当者は起票できない。受領: 社内の運用役割だけ（S-14・S-15 の利用者）。
 */
public final class ServiceCaseAuthorization {

    /** 社内の運用役割（有人案件の起票・受領ができる）。 */
    static final Set<Role> OPERATING_ROLES = Set.copyOf(EnumSet.of(
            Role.CUSTOMER_SUPPORT, Role.SALES, Role.ROUTE_DESIGNER, Role.TRACKING_MANAGER, Role.DATA_STEWARD));

    /** 荷主担当者が自社分として起票できる種類。 */
    private static final Set<ServiceCaseKind> SHIPPER_KINDS =
            Set.copyOf(EnumSet.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServiceCaseKind.QUOTATION_CONSULTATION));

    private ServiceCaseAuthorization() {}

    /** 起票できるか。 */
    static boolean canOpen(AuthenticatedActor actor, ServiceCaseDraft draft) {
        if (isOperating(actor)) {
            return true;
        }
        return actor.hasRole(Role.SHIPPER)
                && SHIPPER_KINDS.contains(draft.kind())
                && draft.shipperCompanyId().filter(actor.companyId()::equals).isPresent();
    }

    /** 進捗の更新・回答の記録・終結・回答期限の変更ができるか（社内の運用役割。担当者だけには限らない。Bolt 40-8。台帳 D-159）。 */
    public static boolean canOperate(AuthenticatedActor actor) {
        return isOperating(actor);
    }

    /** 再開を試みられるか（社内の運用役割か荷主担当者。荷主担当者は自社の案件だけ。US-23 AC2）。 */
    static boolean canReopen(AuthenticatedActor actor) {
        return isOperating(actor) || actor.hasRole(Role.SHIPPER);
    }

    /** その案件を扱えるか。社内の運用役割はどの案件も、荷主担当者は自社の案件だけ（他社の案件は存在を明かさない）。 */
    static boolean canAccess(AuthenticatedActor actor, ServiceCase serviceCase) {
        return isOperating(actor)
                || (actor.hasRole(Role.SHIPPER)
                        && serviceCase
                                .shipperCompanyId()
                                .filter(actor.companyId()::equals)
                                .isPresent());
    }

    /** 受領できるか。 */
    static boolean canReceive(AuthenticatedActor actor) {
        return isOperating(actor);
    }

    private static boolean isOperating(AuthenticatedActor actor) {
        return actor.roles().stream().anyMatch(OPERATING_ROLES::contains);
    }
}
