package com.example.cargotracker.tracking.interfaces.web;

/**
 * S-23 照会の記録の入力（Bolt 40-15）。画面の入力は文字列のまま受け取り、形式の検証と変換は {@link InquiryFormConverter}、業務の判定
 * （担当範囲・必須項目・重複）はサービスに任せる。誤りがあっても入力値をそのまま画面に戻せるよう、変換前の文字列を持つ。
 */
public class InquiryForm {

    private String channel;
    private String trackingNumber;
    private String inquirerKind;
    private String receivedAt;
    private String content;
    private boolean handOff;
    private String sourceReference;
    private String department = InquiryFormConverter.DEFAULT_DEPARTMENT;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getInquirerKind() {
        return inquirerKind;
    }

    public void setInquirerKind(String inquirerKind) {
        this.inquirerKind = inquirerKind;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isHandOff() {
        return handOff;
    }

    public void setHandOff(boolean handOff) {
        this.handOff = handOff;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
