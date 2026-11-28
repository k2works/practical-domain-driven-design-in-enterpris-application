package com.example.cargotracker.externaldata.interfaces.web;

/**
 * S-17 の判断の画面の採用の入力（Bolt 39d3b）。理由は任意で、長さはコントローラーが欄の誤りにする（判断の不変条件も守る）。
 */
public class AdoptionForm {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
