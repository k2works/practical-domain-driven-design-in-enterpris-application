package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * KPI の基準値。パイロット開始前 4 週間の同じ母集団・算式による値（US-21 AC5、KPI-INV-02、PV-01。W9 の Bolt 44-2。台帳 D-172。仮）。
 * 算出方法と登録者とともに記録し、登録した後は変えない。訂正は前の記録を指す新しい記録で行う。期間は 4 週間（28 日。両端を含む）。
 * 値は KPI-01 が分（中央値と、あれば 90 パーセンタイル）、KPI-02 が割合（パーセント。中央値の欄に入れ、90 パーセンタイルは持たない）。
 */
@AggregateRoot
public final class KpiBaseline {

    /** 基準値の期間の日数（パイロット開始前の 4 週間。PV-01）。 */
    public static final int PERIOD_DAYS = 28;

    /** 算出方法の最大の文字数（表の列と同じ）。 */
    public static final int METHOD_MAX_LENGTH = 4000;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UUID id;
    private final KpiKind kpi;
    private final LocalDate periodFrom;
    private final LocalDate periodTo;
    private final BigDecimal valueP50;
    private final BigDecimal valueP90;
    private final String method;
    private final UserId registeredBy;
    private final UtcInstant registeredAt;
    private final UUID supersedes;

    @SuppressWarnings("java:S107") // 集約の値をすべて受け取る
    private KpiBaseline(
            UUID id,
            KpiKind kpi,
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal valueP50,
            BigDecimal valueP90,
            String method,
            UserId registeredBy,
            UtcInstant registeredAt,
            UUID supersedes) {
        this.id = Objects.requireNonNull(id, "id");
        this.kpi = Objects.requireNonNull(kpi, "kpi");
        this.periodFrom = Objects.requireNonNull(periodFrom, "periodFrom");
        this.periodTo = Objects.requireNonNull(periodTo, "periodTo");
        this.valueP50 = Objects.requireNonNull(valueP50, "valueP50");
        this.valueP90 = valueP90;
        this.method = Objects.requireNonNull(method, "method");
        this.registeredBy = Objects.requireNonNull(registeredBy, "registeredBy");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.supersedes = supersedes;
    }

    /**
     * 基準値を登録する。入力の誤りは例外（呼び手が画面に示す文を持つ）。
     *
     * @param valueP50 KPI-01 は中央値（分）、KPI-02 は自己照会率（パーセント）
     * @param valueP90 KPI-01 の 90 パーセンタイル（分。省略できる）。KPI-02 は持たない
     * @param supersedes 訂正する前の記録（初めての登録なら空）
     * @throws IllegalArgumentException 期間が 28 日でない、値が範囲外、算出方法が空か長すぎる
     */
    @SuppressWarnings("java:S107") // 基準値の入力（KPI・期間・値・算出方法）と登録の記録（登録者・時刻・訂正）をすべて受け取る
    public static KpiBaseline register(
            KpiKind kpi,
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal valueP50,
            Optional<BigDecimal> valueP90,
            String method,
            UserId registeredBy,
            UtcInstant registeredAt,
            Optional<UUID> supersedes) {
        Objects.requireNonNull(kpi, "kpi");
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(periodTo, "periodTo");
        Objects.requireNonNull(valueP50, "valueP50");
        Objects.requireNonNull(valueP90, "valueP90");
        Objects.requireNonNull(method, "method");
        if (ChronoUnit.DAYS.between(periodFrom, periodTo) + 1 != PERIOD_DAYS) {
            throw new IllegalArgumentException("基準値の期間は開始日から終了日まで " + PERIOD_DAYS + " 日（パイロット開始前の 4 週間）です");
        }
        validateValues(kpi, valueP50, valueP90);
        String trimmed = method.strip();
        if (trimmed.isEmpty() || trimmed.length() > METHOD_MAX_LENGTH) {
            throw new IllegalArgumentException("算出方法は 1〜" + METHOD_MAX_LENGTH + " 文字で入力してください");
        }
        return new KpiBaseline(
                UUID.randomUUID(),
                kpi,
                periodFrom,
                periodTo,
                valueP50,
                valueP90.orElse(null),
                trimmed,
                registeredBy,
                registeredAt,
                supersedes.orElse(null));
    }

    private static void validateValues(KpiKind kpi, BigDecimal valueP50, Optional<BigDecimal> valueP90) {
        if (kpi == KpiKind.KPI_02) {
            if (valueP90.isPresent()) {
                throw new IllegalArgumentException("KPI-02 の基準値は割合だけで、90 パーセンタイルは持ちません");
            }
            if (valueP50.signum() < 0 || valueP50.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("KPI-02 の基準値は 0 から 100 のパーセントで入力してください");
            }
            return;
        }
        if (valueP50.signum() <= 0) {
            throw new IllegalArgumentException("KPI-01 の基準値の中央値は 0 より大きい分で入力してください");
        }
        if (valueP90.isPresent() && valueP90.get().compareTo(valueP50) < 0) {
            throw new IllegalArgumentException("KPI-01 の 90 パーセンタイルは中央値以上の分で入力してください");
        }
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static KpiBaseline reconstitute(
            UUID id,
            KpiKind kpi,
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal valueP50,
            BigDecimal valueP90,
            String method,
            UserId registeredBy,
            UtcInstant registeredAt,
            UUID supersedes) {
        return new KpiBaseline(
                id, kpi, periodFrom, periodTo, valueP50, valueP90, method, registeredBy, registeredAt, supersedes);
    }

    public UUID id() {
        return id;
    }

    public KpiKind kpi() {
        return kpi;
    }

    public LocalDate periodFrom() {
        return periodFrom;
    }

    public LocalDate periodTo() {
        return periodTo;
    }

    /** KPI-01 は中央値（分）、KPI-02 は自己照会率（パーセント）。 */
    public BigDecimal valueP50() {
        return valueP50;
    }

    /** KPI-01 の 90 パーセンタイル（分）。なければ空。 */
    public Optional<BigDecimal> valueP90() {
        return Optional.ofNullable(valueP90);
    }

    public String method() {
        return method;
    }

    public UserId registeredBy() {
        return registeredBy;
    }

    public UtcInstant registeredAt() {
        return registeredAt;
    }

    /** 訂正した前の記録。初めての登録なら空。 */
    public Optional<UUID> supersedes() {
        return Optional.ofNullable(supersedes);
    }
}
