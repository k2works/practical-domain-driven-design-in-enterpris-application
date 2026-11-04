package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 受信記録の MyBatis マッパー（Bolt 39b）。SQL は同じパッケージの ReceivedRecordMapper.xml に置く。受信記録の表は追記専用なので、
 * 追加と数えることと、取込の隔離を行の範囲の順に読むこと（Bolt 39c3）と、判断を待つ隔離を読み数えること（Bolt 39d）だけを持つ。
 */
@Mapper
public interface ReceivedRecordMapper {

    void insert(ReceivedRecordRow row);

    int countByIngestionAndClassification(
            @Param("ingestionId") UUID ingestionId, @Param("classification") String classification);

    List<ReceivedRecordRow> quarantinedOf(@Param("ingestionId") UUID ingestionId, @Param("limit") int limit);

    /** 判断を待つ隔離（Bolt 39d）。{@code ingestionId} が {@code null} なら絞らない。 */
    List<UndecidedQuarantineRow> undecidedQuarantines(
            @Param("ingestionId") UUID ingestionId, @Param("offset") int offset, @Param("limit") int limit);

    int countUndecidedQuarantines(@Param("ingestionId") UUID ingestionId);

    /** 取込で書いた受信記録を ID で引く（Bolt 39d2）。取込のない受信記録は返さない。 */
    Optional<ReceivedRecordRow> selectById(@Param("id") UUID id);

    /**
     * ID で絞った判断を待つ隔離（Bolt 39d2）。一覧と同じ SQL の断片を使うので、断片の取込の絞り込みの引数 {@code ingestionId} も受ける
     * （リポジトリは {@code null} を渡す）。
     */
    Optional<UndecidedQuarantineRow> undecidedQuarantine(
            @Param("receivedRecordId") UUID receivedRecordId, @Param("ingestionId") UUID ingestionId);

    /**
     * 判断を待つ隔離の照会の 1 行（受信記録に取込と外部原本を結んだ行。Bolt 39d）。外部原本のないキーのない隔離は外部 ID・外部版が
     * {@code null}、ファイルの誤りは行の範囲の 2 列が {@code null}。
     */
    record UndecidedQuarantineRow(
            UUID id,
            UUID ingestionId,
            OffsetDateTime ingestedAt,
            UUID dataSourceId,
            Integer firstLineNumber,
            Integer lastLineNumber,
            String externalId,
            String externalVersion,
            String classificationNote) {}
}
