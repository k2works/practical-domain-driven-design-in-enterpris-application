package com.example.cargotracker.quotation.interfaces.web;

/**
 * 見積依頼の審査（S-03）の入力。確定と差戻しの 2 つのフォームが、判断（{@code decision}）を変えて同じ URL に送る。
 * 対象の版番号は隠し項目で送り、集約の現在の版番号と照合する（Q-INV-04）。
 */
public class ReviewForm {

    private String decision;
    private Integer versionNo;
    private String rationale;
    private String reason;
    private String missingItems;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMissingItems() {
        return missingItems;
    }

    public void setMissingItems(String missingItems) {
        this.missingItems = missingItems;
    }
}
