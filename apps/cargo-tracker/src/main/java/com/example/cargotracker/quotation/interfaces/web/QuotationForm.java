package com.example.cargotracker.quotation.interfaces.web;

import java.util.ArrayList;
import java.util.List;

/**
 * 見積りの作成（S-04）の入力。料金明細は 10 行の欄を出し、空の行は無視する（Q-INV-17。JavaScript で行を足さない）。
 * 日時は日本時間の {@code 2026-11-02 09:00} の形で受け取る。
 */
public class QuotationForm {

    /** 料金明細の欄の数（料金明細の上限と同じ）。 */
    static final int LINE_ROWS = 10;

    private List<Line> lines = new ArrayList<>();
    private String currency;
    private String expiresAt;
    private String via;
    private String departureAt;
    private String arrivalAt;

    public QuotationForm() {
        for (int i = 0; i < LINE_ROWS; i++) {
            lines.add(new Line());
        }
    }

    /**
     * 料金明細の行の入力。一覧は写しを返すが、行（{@link Line}）は同じものなので、{@code lines[0].description} のような
     * 送信の値は、行を通して結び付く（欄は 10 行を初めから用意している）。
     */
    public List<Line> getLines() {
        return List.copyOf(lines);
    }

    public void setLines(List<Line> lines) {
        this.lines = new ArrayList<>(lines);
        while (this.lines.size() < LINE_ROWS) {
            this.lines.add(new Line());
        }
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public String getDepartureAt() {
        return departureAt;
    }

    public void setDepartureAt(String departureAt) {
        this.departureAt = departureAt;
    }

    public String getArrivalAt() {
        return arrivalAt;
    }

    public void setArrivalAt(String arrivalAt) {
        this.arrivalAt = arrivalAt;
    }

    /** 料金明細の 1 行の入力。 */
    public static class Line {

        private String description;
        private String amount;
        private String contractReference;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getContractReference() {
            return contractReference;
        }

        public void setContractReference(String contractReference) {
            this.contractReference = contractReference;
        }

        /** どの欄も空か（空の行は無視する）。 */
        boolean isBlank() {
            return isBlank(description) && isBlank(amount) && isBlank(contractReference);
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
