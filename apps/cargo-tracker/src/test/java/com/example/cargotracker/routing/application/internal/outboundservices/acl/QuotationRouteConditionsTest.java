package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionView;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/** 見積りの経路条件を経路設計の経路条件に翻訳する腐敗防止層（Bolt 17、貨物種別の翻訳は Bolt 39i の行 14・15）。 */
class QuotationRouteConditionsTest {

    private static final UUID TRANSPORT_REQUEST_ID = UUID.randomUUID();
    private static final UtcInstant DEADLINE = new UtcInstant(Instant.parse("2026-11-02T00:00:00Z"));

    private static RouteCargoCategory translate(String quotationName) {
        QuotationRouteConditions conditions =
                new QuotationRouteConditions((id, versionNo) -> Optional.of(new RouteConditionView(
                        "TR-2026-0001",
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        DEADLINE,
                        quotationName,
                        new CompanyId(UUID.randomUUID()))));
        return conditions
                .find(TRANSPORT_REQUEST_ID, 1)
                .orElseThrow()
                .specification()
                .cargoCategory();
    }

    @Nested
    class Bolt39i_貨物種別の翻訳 {

        private final Logger logger = (Logger) LoggerFactory.getLogger(QuotationRouteConditions.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        @BeforeEach
        void attach() {
            appender.start();
            logger.addAppender(appender);
        }

        @AfterEach
        void detach() {
            logger.detachAppender(appender);
        }

        @ParameterizedTest(name = "見積りの {0} は経路設計の {1}")
        @CsvSource({
            "GENERAL, GENERAL",
            "DANGEROUS, DANGEROUS",
            "REEFER, REEFER",
            "OTHER_SPECIAL, OTHER_SPECIAL",
        })
        void 見積りの四つの名前が同じ意味の種別に写り警告を出さない(String quotationName, RouteCargoCategory expected) {
            assertThat(translate(quotationName)).isEqualTo(expected);
            assertThat(appender.list).isEmpty();
        }

        @ParameterizedTest(name = "知らない名前 \"{0}\" は UNRECOGNIZED")
        @ValueSource(strings = {"GENERAL2", "general", ""})
        void 知らない名前はUNRECOGNIZEDに写し警告を一回出し例外にしない(String unknown) {
            assertThat(translate(unknown)).isEqualTo(RouteCargoCategory.UNRECOGNIZED);

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("'" + unknown + "'");
        }
    }
}
