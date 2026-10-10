package com.example.cargotracker.booking.acceptance;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * メモリ上の貨物予約のリポジトリ（業務ルール層の受入シナリオ）。追跡番号・見積り ID・業務番号と見積り番号・コマンド ID の一意を表の
 * 制約と同じく守る（B-INV-03・B-INV-11）。
 * 貨物予約と処理済みコマンドは不変なので、保存したインスタンスをそのまま返しても呼び出し側が書き換えられない（T-61）。
 * 処理済みコマンドは貨物予約と一緒に記録し、貨物予約の保存に失敗したら記録しない（同じトランザクション。Bolt 24）。
 */
public class InMemoryBookingRepository implements BookingRepository {

    private final Map<String, Booking> byTrackingNumber = new LinkedHashMap<>();
    private final Map<CommandId, ProcessedCommand> processedCommands = new LinkedHashMap<>();

    @Override
    public synchronized void save(Booking booking, UUID operator, ProcessedCommand processedCommand) {
        UUID quotationId = booking.currentVersion().terms().quotationId();
        boolean sameQuotation = byTrackingNumber.values().stream()
                .anyMatch(saved -> saved.currentVersion().terms().quotationId().equals(quotationId));
        sameQuotation |= byTrackingNumber.values().stream()
                .anyMatch(saved -> saved.transportRequestNumber().equals(booking.transportRequestNumber())
                        && saved.quotationNo() == booking.quotationNo());
        if (sameQuotation) {
            throw new DuplicateBookingException("同じ見積りの予約がすでにある: " + quotationId, null);
        }
        if (byTrackingNumber.containsKey(booking.trackingNumber().value())) {
            throw new IllegalStateException(
                    "追跡番号が重なった: " + booking.trackingNumber().value());
        }
        if (processedCommands.containsKey(processedCommand.commandId())) {
            // 処理済みコマンドの主キーの違反と同じく、同時の確定の決着として扱う（Bolt 24 レビュー P-1）
            throw new DuplicateBookingException("同じコマンド ID の確定がすでにある: " + processedCommand.commandId(), null);
        }
        byTrackingNumber.put(booking.trackingNumber().value(), booking);
        processedCommands.put(processedCommand.commandId(), processedCommand);
    }

    @Override
    public synchronized Optional<ProcessedCommand> findProcessedCommand(CommandId commandId) {
        return Optional.ofNullable(processedCommands.get(commandId));
    }

    @Override
    public synchronized Optional<TrackingNumber> findTrackingNumber(String transportRequestNumber, int quotationNo) {
        return byTrackingNumber.values().stream()
                .filter(saved -> saved.transportRequestNumber().equals(transportRequestNumber)
                        && saved.quotationNo() == quotationNo)
                .map(Booking::trackingNumber)
                .findFirst();
    }

    @Override
    public synchronized Optional<Booking> findByTrackingNumber(TrackingNumber trackingNumber) {
        return Optional.ofNullable(byTrackingNumber.get(trackingNumber.value()));
    }

    @Override
    public synchronized boolean existsByTrackingNumber(TrackingNumber trackingNumber) {
        return byTrackingNumber.containsKey(trackingNumber.value());
    }

    /** 荷主企業の予約だけを、S-10 と同じ並びで上限まで返す（C-06。MyBatis の実装と同じく荷主企業で絞る。Bolt 27b）。 */
    @Override
    public synchronized List<BookingSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit) {
        return summaries(
                byTrackingNumber.values().stream()
                        .filter(booking -> booking.shipperCompanyId().equals(shipperCompanyId))
                        .toList(),
                limit);
    }

    /** 確定時刻（予約版 1）の新しい順、同じ時刻なら追跡番号の順に、上限までの要約の写しを返す（MyBatis の実装と同じ。T-61）。 */
    @Override
    public synchronized List<BookingSummary> findRecentSummaries(int limit) {
        return summaries(List.copyOf(byTrackingNumber.values()), limit);
    }

    /** 確定時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで。値の写しを返す（T-61）。 */
    private static List<BookingSummary> summaries(List<Booking> found, int limit) {
        return found.stream()
                .map(booking -> new BookingSummary(
                        booking.id(),
                        booking.trackingNumber(),
                        booking.transportRequestNumber(),
                        booking.quotationNo(),
                        booking.versions().getFirst().committedAt()))
                .sorted(Comparator.comparing((BookingSummary summary) ->
                                summary.committedAt().instant())
                        .reversed()
                        .thenComparing(summary -> summary.trackingNumber().value()))
                .limit(limit)
                .toList();
    }

    public synchronized List<Booking> findAll() {
        return new ArrayList<>(byTrackingNumber.values());
    }

    public synchronized void clear() {
        byTrackingNumber.clear();
        processedCommands.clear();
    }
}
