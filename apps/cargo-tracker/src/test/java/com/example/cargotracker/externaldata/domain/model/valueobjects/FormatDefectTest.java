package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect.Extent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 形式の誤り（Bolt 39c3 の行 14。開始準備の決定 (8)、T-240・T-244）。範囲がファイルの理由は、すべて空でない「どうすればよいか」を持ち、
 * ファイルの誤りの注記の全文が理由ごとに期待と一致する。期待の組が範囲がファイルの理由をすべて含むことも確かめる（表から作る規則の網羅）。
 */
class FormatDefectTest {

    /** 範囲がファイルの理由と、その注記の全文（詳細は理由にかかわる列名）。 */
    static Stream<Arguments> 範囲がファイルの理由と注記の全文() {
        return Stream.of(
                Arguments.of(
                        FormatDefect.UNDECODABLE, List.of(), "ファイル: 文字コードが UTF-8 ではありません。UTF-8 で保存し直したファイルを取り込んでください。"),
                Arguments.of(FormatDefect.UNREADABLE_HEADER, List.of(), "ファイル: ヘッダーを読めません。ヘッダーの引用符を確かめてください。"),
                Arguments.of(
                        FormatDefect.MISSING_COLUMN,
                        List.of("acquired_at", "port_calls"),
                        "ファイル: 必要な列がありません（acquired_at、port_calls）。" + "選んだ情報源の種別に合ったファイルか確かめてください。合っていれば提供元に確かめてください。"),
                Arguments.of(
                        FormatDefect.DUPLICATE_COLUMN,
                        List.of("external_id"),
                        "ファイル: 同じ名前の列があります（external_id）。提供元に確かめてください。"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("範囲がファイルの理由と注記の全文")
    void 行14_範囲がファイルの理由の注記は理由と詳細とどうすればよいかの全文になる(FormatDefect defect, List<String> details, String expected) {
        assertThat(defect.fileNote(details)).isEqualTo(expected);
    }

    @Test
    void 行14_期待の組は範囲がファイルの理由をすべて含む() {
        List<FormatDefect> fileDefects = Arrays.stream(FormatDefect.values())
                .filter(defect -> defect.occursIn(Extent.FILE))
                .toList();

        assertThat(範囲がファイルの理由と注記の全文().map(arguments -> (FormatDefect) arguments.get()[0]))
                .containsExactlyInAnyOrderElementsOf(fileDefects);
    }
}
