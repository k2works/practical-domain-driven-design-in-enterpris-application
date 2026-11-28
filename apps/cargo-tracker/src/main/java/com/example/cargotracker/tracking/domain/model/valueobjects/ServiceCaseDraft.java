package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Source;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 起票の入力。有人案件の起票の入力（Bolt 40-5）で、必須項目が足りなくても作れる（足りない項目は {@link MissingItem} で返し、呼び手が入力を保持する。40-7）。
 * 値を足すときは {@code with…} で新しい入力を作る。
 *
 * @param kind 種類
 * @param priority 優先度
 * @param cause 原因・照会内容（なければ {@code null}）
 * @param department 担当部門（なければ {@code null}）
 * @param shipperCompanyId 荷主企業
 * @param bookingId 予約 ID
 * @param trackingNumber 追跡番号
 * @param sources 参照した出典
 * @param oldVersionRef 旧版の参照
 * @param newVersionRef 新版の参照
 * @param manualSourceRef 手動入力の出典の参照
 * @param reconciliationRequested 復旧照合が必要と指定されたか（外部停止は種類で必要）
 */
@ValueObject
public record ServiceCaseDraft(
        ServiceCaseKind kind,
        ServicePriority priority,
        String cause,
        String department,
        Optional<CompanyId> shipperCompanyId,
        Optional<UUID> bookingId,
        Optional<TrackingNumber> trackingNumber,
        List<Source> sources,
        Optional<String> oldVersionRef,
        Optional<String> newVersionRef,
        Optional<String> manualSourceRef,
        boolean reconciliationRequested) {

    public ServiceCaseDraft {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(oldVersionRef, "oldVersionRef");
        Objects.requireNonNull(newVersionRef, "newVersionRef");
        Objects.requireNonNull(manualSourceRef, "manualSourceRef");
    }

    /** 種類・優先度・原因・担当部門だけの入力。 */
    public static ServiceCaseDraft of(ServiceCaseKind kind, ServicePriority priority, String cause, String department) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false);
    }

    public ServiceCaseDraft withShipper(CompanyId shipper) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                Optional.ofNullable(shipper),
                bookingId,
                trackingNumber,
                sources,
                oldVersionRef,
                newVersionRef,
                manualSourceRef,
                reconciliationRequested);
    }

    public ServiceCaseDraft withBooking(UUID booking) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                shipperCompanyId,
                Optional.ofNullable(booking),
                trackingNumber,
                sources,
                oldVersionRef,
                newVersionRef,
                manualSourceRef,
                reconciliationRequested);
    }

    public ServiceCaseDraft withTracking(TrackingNumber tracking) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                shipperCompanyId,
                bookingId,
                Optional.ofNullable(tracking),
                sources,
                oldVersionRef,
                newVersionRef,
                manualSourceRef,
                reconciliationRequested);
    }

    public ServiceCaseDraft withSources(List<Source> referenced) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                shipperCompanyId,
                bookingId,
                trackingNumber,
                referenced,
                oldVersionRef,
                newVersionRef,
                manualSourceRef,
                reconciliationRequested);
    }

    public ServiceCaseDraft withVersions(String oldVersion, String newVersion) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                shipperCompanyId,
                bookingId,
                trackingNumber,
                sources,
                Optional.ofNullable(oldVersion),
                Optional.ofNullable(newVersion),
                manualSourceRef,
                reconciliationRequested);
    }

    public ServiceCaseDraft withManualSource(String manualSource) {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                shipperCompanyId,
                bookingId,
                trackingNumber,
                sources,
                oldVersionRef,
                newVersionRef,
                Optional.ofNullable(manualSource),
                reconciliationRequested);
    }

    public ServiceCaseDraft requiringReconciliation() {
        return new ServiceCaseDraft(
                kind,
                priority,
                cause,
                department,
                shipperCompanyId,
                bookingId,
                trackingNumber,
                sources,
                oldVersionRef,
                newVersionRef,
                manualSourceRef,
                true);
    }
}
