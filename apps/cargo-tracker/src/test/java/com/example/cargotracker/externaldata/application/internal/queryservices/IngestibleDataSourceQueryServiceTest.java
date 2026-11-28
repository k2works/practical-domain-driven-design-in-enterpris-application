package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 取り込める情報源の照会（Bolt 39d4 の行 21。開始準備の決定 (9)）。S-16 の選択肢は照会のサービスが提供元・原本種別の Java の {@link String} の順に並べ、ポートは並びを約束しない。
 * 書く順を逆にしたデータを置く（T-245）。
 */
class IngestibleDataSourceQueryServiceTest {

    private static DataSource approved(String provider, String recordType) {
        return new DataSource(UUID.randomUUID(), provider, recordType, ApprovalStatus.APPROVED);
    }

    private static IngestibleDataSourceQueryService serviceReturning(List<DataSource> sources) {
        return new IngestibleDataSourceQueryService(new DataSourceRepository() {
            @Override
            public Optional<DataSource> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public List<DataSource> findApproved() {
                return sources;
            }

            @Override
            public int countApproved() {
                return sources.size();
            }
        });
    }

    private static List<IngestibleDataSource> expected(DataSource... sources) {
        return Stream.of(sources)
                .map(source -> new IngestibleDataSource(source.id(), source.provider(), source.recordType()))
                .toList();
    }

    @Test
    void 行21_提供元と原本種別の順に並べポートの返す順によらない() {
        DataSource firstMilestone = approved("A", "MILESTONE");
        DataSource firstVoyage = approved("A", "VOYAGE");
        // 原本種別で先に並べると B が A の VOYAGE より前になる組
        DataSource second = approved("B", "MILESTONE");
        List<DataSource> sources = new ArrayList<>(List.of(firstMilestone, firstVoyage, second));

        List<IngestibleDataSource> inOrder =
                serviceReturning(List.copyOf(sources)).ingestibleSources();
        Collections.reverse(sources);
        List<IngestibleDataSource> reversed =
                serviceReturning(List.copyOf(sources)).ingestibleSources();

        assertThat(reversed).isEqualTo(expected(firstMilestone, firstVoyage, second));
        assertThat(inOrder).isEqualTo(reversed);
    }

    static Stream<Arguments> stringOrders() {
        return Stream.of(
                Arguments.of("大文字は小文字より前", "Zeta", "alpha"),
                Arguments.of("日本語は符号の順", "あ海運", "ん海運"),
                Arguments.of("ASCII は日本語より前", "Zeta", "あ海運"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stringOrders")
    void 行21_提供元の順はJavaのStringの順(String name, String first, String second) {
        DataSource earlier = approved(first, "VOYAGE");
        DataSource later = approved(second, "VOYAGE");

        assertThat(serviceReturning(List.of(later, earlier)).ingestibleSources())
                .isEqualTo(expected(earlier, later));
    }
}
