package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 輸送条件の入力。荷主が提出しようとする輸送条件で、項目が欠けていてよい（欠けた項目は null）。
 * 検証して違反がなければ輸送条件（{@link ShipmentTerms}）になる（{@link #validate}）。
 * 形式の誤り（UN/LOCODE、数値、日時の書き方）は画面の層で扱い、ここには形式の正しい値だけが来る。
 *
 * @param consigneeCompanyId 荷受人企業 ID
 * @param origin 出発地
 * @param destination 目的地
 * @param arrivalDeadline 希望到着期限
 * @param cargoCategory 貨物種別
 * @param packageType 荷姿
 * @param packageCount 個数
 * @param grossWeightKg 総重量（kg）
 * @param volumeM3 容積（m3）
 */
@ValueObject
public record ShipmentTermsInput(
        CompanyId consigneeCompanyId,
        Location origin,
        Location destination,
        UtcInstant arrivalDeadline,
        CargoCategory cargoCategory,
        PackageType packageType,
        Integer packageCount,
        BigDecimal grossWeightKg,
        BigDecimal volumeM3) {

    /**
     * 提出できるかを検証する（Q-INV-01、Q-INV-02、Q-INV-12）。不足と誤りはすべてまとめて返す（1 件ずつ直させない）。
     * 違反がなければ輸送条件を返す。検証を通った入力からしか輸送条件を作れないようにするため、変換を別のメソッドに分けない。
     *
     * @param submittedAt 提出時刻（希望到着期限はこれより後でなければならない）
     * @param policy MVP 受付範囲
     */
    public Validation validate(UtcInstant submittedAt, MvpAcceptancePolicy policy) {
        List<Violation> violations = new ArrayList<>();
        required(violations, Item.CONSIGNEE, consigneeCompanyId);
        required(violations, Item.ORIGIN, origin);
        if (required(violations, Item.DESTINATION, destination) && destination.equals(origin)) {
            violations.add(new Violation(Item.DESTINATION, Reason.SAME_AS_ORIGIN));
        }
        if (required(violations, Item.ARRIVAL_DEADLINE, arrivalDeadline)
                && !arrivalDeadline.instant().isAfter(submittedAt.instant())) {
            violations.add(new Violation(Item.ARRIVAL_DEADLINE, Reason.NOT_AFTER_SUBMISSION));
        }
        if (required(violations, Item.CARGO_CATEGORY, cargoCategory) && !policy.accepts(cargoCategory)) {
            violations.add(new Violation(Item.CARGO_CATEGORY, Reason.OUTSIDE_MVP));
        }
        required(violations, Item.PACKAGE_TYPE, packageType);
        if (required(violations, Item.PACKAGE_COUNT, packageCount) && packageCount < 1) {
            violations.add(new Violation(Item.PACKAGE_COUNT, Reason.NOT_POSITIVE));
        }
        measure(violations, Item.GROSS_WEIGHT_KG, grossWeightKg);
        measure(violations, Item.VOLUME_M3, volumeM3);
        if (!violations.isEmpty()) {
            return new Invalid(new SubmissionViolations(violations));
        }
        return new Valid(new ShipmentTerms(
                consigneeCompanyId,
                origin,
                destination,
                arrivalDeadline,
                new Cargo(cargoCategory, packageType, packageCount, grossWeightKg, volumeM3)));
    }

    /** 提出の検証の結果。違反がなければ輸送条件、あれば違反の一覧のどちらか。 */
    public sealed interface Validation {

        /**
         * ほかの規則（書類の受付規則など）の違反を足した結果を返す。足す違反がなければこの結果のまま、あれば違反をまとめた結果にする
         * （1 件ずつ直させない。Bolt 6〜8 レビュー R-11）。
         */
        default Validation and(List<Violation> more) {
            if (more.isEmpty()) {
                return this;
            }
            List<Violation> violations = new ArrayList<>();
            if (this instanceof Invalid(SubmissionViolations own)) {
                violations.addAll(own.violations());
            }
            violations.addAll(more);
            return new Invalid(new SubmissionViolations(violations));
        }
    }

    /**
     * 違反がなく、輸送条件になった。
     *
     * @param terms 輸送条件
     */
    public record Valid(ShipmentTerms terms) implements Validation {}

    /**
     * 不足や誤りがあった。
     *
     * @param violations 不足と誤り（空でない）
     */
    public record Invalid(SubmissionViolations violations) implements Validation {}

    /** 入力があるかを確かめ、なければ不足を足す。入力があれば true。 */
    private static boolean required(List<Violation> violations, Item item, Object value) {
        if (value == null) {
            violations.add(new Violation(item, Reason.MISSING));
            return false;
        }
        return true;
    }

    /** 総重量・容積を確かめる。0 より大きく、小数点以下 3 桁まで。 */
    private static void measure(List<Violation> violations, Item item, BigDecimal value) {
        if (!required(violations, item, value)) {
            return;
        }
        if (!Cargo.isPositive(value)) {
            violations.add(new Violation(item, Reason.NOT_POSITIVE));
        } else if (!Cargo.fitsDecimalPlaces(value)) {
            violations.add(new Violation(item, Reason.TOO_MANY_DECIMALS));
        }
    }
}
