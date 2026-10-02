package com.example.cargotracker.quotation.interfaces.web;

/**
 * 見積依頼の作成画面（C-03 の 1 画面の形）の入力。画面の入力は文字列のまま受け取り、
 * 形式の検証と変換は {@link TransportRequestFormConverter}、業務の規則の検証はドメインに任せる。
 * 誤りがあっても入力値をそのまま画面に戻せるよう、変換前の文字列を持つ（UI 設計の共通部品「フォーム項目」）。
 */
public class TransportRequestForm {

    private String consignee;
    private String origin;
    private String destination;
    private String arrivalDeadline;
    private String cargoCategory = "GENERAL";
    private String packageType;
    private String packageCount;
    private String grossWeightKg;
    private String volumeM3;

    public String getConsignee() {
        return consignee;
    }

    public void setConsignee(String consignee) {
        this.consignee = consignee;
    }

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

    public String getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(String arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public String getCargoCategory() {
        return cargoCategory;
    }

    public void setCargoCategory(String cargoCategory) {
        this.cargoCategory = cargoCategory;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getPackageCount() {
        return packageCount;
    }

    public void setPackageCount(String packageCount) {
        this.packageCount = packageCount;
    }

    public String getGrossWeightKg() {
        return grossWeightKg;
    }

    public void setGrossWeightKg(String grossWeightKg) {
        this.grossWeightKg = grossWeightKg;
    }

    public String getVolumeM3() {
        return volumeM3;
    }

    public void setVolumeM3(String volumeM3) {
        this.volumeM3 = volumeM3;
    }
}
