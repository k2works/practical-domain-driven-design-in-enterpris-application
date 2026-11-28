package com.example.cargotracker.externaldata.interfaces.web;

/**
 * S-17 の判断の画面の却下の入力（Bolt 39d2）。理由の必須と長さはコントローラーが欄の誤りにする（判断の不変条件も守る）。
 */
public class RejectionForm {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
