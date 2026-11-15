package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * KPI の種類（US-21。パイロットの判定 PV-01 に使う KPI-01 と KPI-02。W9 の Bolt 44-2）。
 */
public enum KpiKind {
    /** KPI-01 経路提示リードタイム（値は分）。 */
    KPI_01("KPI-01"),
    /** KPI-02 自己照会率（値はパーセント）。 */
    KPI_02("KPI-02");

    private final String code;

    KpiKind(String code) {
        this.code = code;
    }

    /** 表や画面での表記（KPI-01・KPI-02）。 */
    public String code() {
        return code;
    }

    /** 表記から種類を引く。知らない表記は例外。 */
    public static KpiKind ofCode(String code) {
        for (KpiKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("知らない KPI: " + code);
    }
}
