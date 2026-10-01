package com.example.cargotracker.quotation.interfaces.web;

/**
 * 見積依頼の作成画面（C-03 の最小形）の入力。形式の検証はドメインの場所（Location）に任せる。
 */
public class TransportRequestForm {

    private String origin;
    private String destination;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
}
