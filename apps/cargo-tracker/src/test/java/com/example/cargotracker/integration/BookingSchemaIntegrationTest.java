package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 予約のスキーマ（データモデル `booking`。Bolt 23）。追跡番号の一意、1 つの見積りから予約は 1 件（B-INV-11）、予約ごとに予約サガは
 * 1 つ、状態の値を表の制約で守る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingSchemaIntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-10-08T09:00:00Z"));

    @Autowired
    DataSource dataSource;

    @Test
    void 貨物予約と予約版と予約サガを保存できる() throws SQLException {
        UUID bookingId = insertBooking(newTrackingNumber(), "CONFIRMED");
        insertVersion(bookingId, 1, UUID.randomUUID());
        insertSaga(bookingId, "IN_PROGRESS");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM booking.booking_version WHERE booking_id = ?")) {
            statement.setObject(1, bookingId);
            var result = statement.executeQuery();
            result.next();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void 追跡番号は一意() throws SQLException {
        String trackingNumber = newTrackingNumber();
        insertBooking(trackingNumber, "CONFIRMED");

        assertViolation(() -> insertBooking(trackingNumber, "CONFIRMED"), UNIQUE_VIOLATION);
    }

    @Test
    void 一つの見積りから予約は一件だけ() throws SQLException {
        UUID quotationId = UUID.randomUUID();
        insertBooking(newTrackingNumber(), quotationId, "CONFIRMED");

        assertViolation(() -> insertBooking(newTrackingNumber(), quotationId, "CONFIRMED"), UNIQUE_VIOLATION);
    }

    @Test
    void 予約ごとに予約サガは一つ() throws SQLException {
        UUID bookingId = insertBooking(newTrackingNumber(), "CONFIRMED");
        insertSaga(bookingId, "IN_PROGRESS");

        assertViolation(() -> insertSaga(bookingId, "IN_PROGRESS"), UNIQUE_VIOLATION);
    }

    @Test
    void 貨物予約の状態は決めた値だけ() {
        assertViolation(() -> insertBooking(newTrackingNumber(), "BOOKED"), CHECK_VIOLATION);
    }

    @Test
    void 予約サガの状態は決めた値だけ() throws SQLException {
        UUID bookingId = insertBooking(newTrackingNumber(), "CONFIRMED");

        assertViolation(() -> insertSaga(bookingId, "STARTED"), CHECK_VIOLATION);
    }

    @Test
    void 予約版の番号は1から() throws SQLException {
        UUID bookingId = insertBooking(newTrackingNumber(), "CONFIRMED");

        assertViolation(() -> insertVersion(bookingId, 0, UUID.randomUUID()), CHECK_VIOLATION);
    }

    private static String newTrackingNumber() {
        return "CT"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    @Test
    void 一つの業務番号と見積り番号から予約は一件だけ() throws SQLException {
        String transportRequestNumber = newTransportRequestNumber();
        insertBooking(newTrackingNumber(), UUID.randomUUID(), transportRequestNumber, 1, "CONFIRMED");

        assertViolation(
                () -> insertBooking(newTrackingNumber(), UUID.randomUUID(), transportRequestNumber, 1, "CONFIRMED"),
                UNIQUE_VIOLATION);
        insertBooking(newTrackingNumber(), UUID.randomUUID(), transportRequestNumber, 2, "CONFIRMED");
    }

    @Test
    void 見積り番号は1から() {
        assertViolation(
                () -> insertBooking(
                        newTrackingNumber(), UUID.randomUUID(), newTransportRequestNumber(), 0, "CONFIRMED"),
                CHECK_VIOLATION);
    }

    @Test
    void 処理済みコマンドのコマンドIDは一意でコマンドの種類は決めた値だけ() throws SQLException {
        UUID commandId = UUID.randomUUID();
        insertProcessedCommand(commandId, "ConfirmBooking");

        assertViolation(() -> insertProcessedCommand(commandId, "ConfirmBooking"), UNIQUE_VIOLATION);
        assertViolation(() -> insertProcessedCommand(UUID.randomUUID(), "SubmitTransportRequest"), CHECK_VIOLATION);
    }

    /** 業務番号と見積り番号の一意制約（Bolt 24）に当たらないよう、テストごとに新しい業務番号にする。 */
    private static String newTransportRequestNumber() {
        return "TR-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private UUID insertBooking(String trackingNumber, String status) throws SQLException {
        return insertBooking(trackingNumber, UUID.randomUUID(), status);
    }

    private UUID insertBooking(String trackingNumber, UUID quotationId, String status) throws SQLException {
        return insertBooking(trackingNumber, quotationId, newTransportRequestNumber(), 1, status);
    }

    private UUID insertBooking(
            String trackingNumber, UUID quotationId, String transportRequestNumber, int quotationNo, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO booking.booking"
                        + " (id, tracking_number, transport_request_number, quotation_no, quotation_id,"
                        + " shipper_company_id, status, transport_phase, current_version_no, version, created_at,"
                        + " created_by, updated_at, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 'BEFORE_PICKUP', 1, 0, ?, ?, ?, ?)")) {
            UUID user = UUID.randomUUID();
            statement.setObject(1, id);
            statement.setString(2, trackingNumber);
            statement.setString(3, transportRequestNumber);
            statement.setInt(4, quotationNo);
            statement.setObject(5, quotationId);
            statement.setObject(6, UUID.randomUUID());
            statement.setString(7, status);
            statement.setTimestamp(8, NOW);
            statement.setObject(9, user);
            statement.setTimestamp(10, NOW);
            statement.setObject(11, user);
            statement.executeUpdate();
        }
        return id;
    }

    private void insertProcessedCommand(UUID commandId, String commandType) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO booking.processed_command"
                                + " (command_id, command_type, payload_hash, result_ref, processed_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, commandId);
            statement.setString(2, commandType);
            statement.setString(3, "0".repeat(64));
            statement.setString(4, UUID.randomUUID() + ":CTABCDEFGH2345");
            statement.setTimestamp(5, NOW);
            statement.executeUpdate();
        }
    }

    private void insertVersion(UUID bookingId, int versionNo, UUID quotationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO booking.booking_version"
                        + " (booking_id, booking_version_no, transport_request_id, transport_request_version_no,"
                        + " quotation_id, routing_case_number, route_version_no, consignee_company_id, cargo_category,"
                        + " cargo_summary, shipper_approver_id, confirmed_by, committed_at)"
                        + " VALUES (?, ?, ?, 1, ?, ?, 1, ?, 'GENERAL', '一般貨物 パレット 10 個', ?, ?, ?)")) {
            statement.setObject(1, bookingId);
            statement.setInt(2, versionNo);
            statement.setObject(3, UUID.randomUUID());
            statement.setObject(4, quotationId);
            statement.setString(5, "RC-2026-0001");
            statement.setObject(6, UUID.randomUUID());
            statement.setObject(7, UUID.randomUUID());
            statement.setObject(8, UUID.randomUUID());
            statement.setTimestamp(9, NOW);
            statement.executeUpdate();
        }
    }

    private void insertSaga(UUID bookingId, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO booking.booking_saga"
                        + " (id, booking_id, tracking_number, status, current_step, started_at, updated_at, version)"
                        + " VALUES (?, ?, 'CT0000000000', ?, 'START_TRACKING', ?, ?, 0)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, bookingId);
            statement.setString(3, status);
            statement.setTimestamp(4, NOW);
            statement.setTimestamp(5, NOW);
            statement.executeUpdate();
        }
    }

    private static void assertViolation(SqlAction action, String sqlState) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(sqlState));
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
