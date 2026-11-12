package com.example.cargotracker.externaldata.interfaces.web;

import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * S-17 の判断の画面の値の領域の表示の値（受け取った値とこの外部版の採用値。Bolt 39d3、主要実績と読めない値は Bolt 39d3c の開始準備の決定 (5)、
 * 【業務の判断】(i)・(j)・(k)）。航海は航海番号と寄港の番号付きの並び、主要実績は追跡番号・実績の種類・場所・発生時刻を、取得した日時（この
 * 外部版の採用値は情報版・公開した日時・取得した日時）の後に示す。日時は社内の画面の形（UTC を併記）。値には取込ファイルから来た値が入るので、
 * テンプレートは {@code th:text} で示す。
 */
final class UndecidedRecordValueViews {

    private static final String RECEIVED_META = "取得した日時 %s";

    private static final String ADOPTED_META = "情報版 %s・公開した日時 %s・取得した日時 %s";
    private static final String PORT_CALL = "港 %s・到着 %s・出発 %s";
    private static final String NO_TIME = "—";
    private static final String NOT_RECORDED = "この隔離は、受け取った値を記録する前に取り込みました。値は原本のファイルで確かめてください。";
    private static final String NO_ADOPTED = "この外部版の採用値はありません。";

    /** 値を読めない側の文（表を直に書き換えたときなど。【業務の判断】(k)）。 */
    private static final String ILLEGIBLE = "値を読めませんでした。";

    /** 値が同じときの文（値も取得時刻も同じ、取得した日時だけが違う。取得時刻は原本のハッシュに入るが値に入らない）。 */
    private static final String SAME_VALUES = "受け取った値はこの外部版の採用値と同じです。同じ原本か、原本の書き方（引用符・空白など）だけが違います。";

    /** この外部 ID の採用値の領域の説明（差異の規則が比べる相手。Bolt 39h3 の決定 (4)）。 */
    private static final String PARTNER_BY_ACQUIRED = "取得した日時が最も新しい採用値です。";

    private static final String PARTNER_BY_PUBLISHED = "公開した日時が最も新しい採用値です。";

    /** 古い外部版の隔離の採用の警告（Bolt 39h3 の決定 (3)。航海と主要実績で採用の影響が違う）。 */
    private static final String OLD_VERSION_VOYAGE =
            "この外部 ID は、この隔離を受け取った時刻より後に取得した外部版を採用しています。この隔離を採用すると、経路設計の航海がこの古い値に置き換わります"
                    + "（算出済みの候補の確定は拒否されることがあります）。「この外部 ID の採用値」と比べてから採用してください。";

    private static final String OLD_VERSION_MILESTONE =
            "この外部 ID は、この隔離を受け取った時刻より後に取得した外部版を採用しています。この隔離を採用すると、追跡のまだ採用していない下書きの候補がこの古い値に"
                    + "置き換わります。「この外部 ID の採用値」と比べてから採用してください。";

    private static final String SAME_VALUES_BUT_ACQUIRED_AT = "受け取った値はこの外部版の採用値と同じで、取得した日時だけが違います。";

    /**
     * 実績の種類の表示名（用語集の実績の種類。追跡の表示名の写しで、外部データは追跡の型を参照しない）。表にない値は例外にし、すべての値に
     * 表示名があることを画面の単体テストで確かめる（T-240）。
     */
    private static final Map<MilestoneKindValue, String> KIND_NAMES = new EnumMap<>(Map.of(
            MilestoneKindValue.PICKUP, "集荷",
            MilestoneKindValue.RECEIPT_AT_ORIGIN, "搬入",
            MilestoneKindValue.DEPARTURE, "出発",
            MilestoneKindValue.TRANSSHIPMENT, "積替",
            MilestoneKindValue.ARRIVAL, "到着",
            MilestoneKindValue.DELIVERY, "引渡し"));

    private UndecidedRecordValueViews() {}

    /** 受け取った値の領域（値の列がなければその旨、読めなければ取得した日時と読めない文）。 */
    static ValuesView receivedOf(UndecidedRecordValues values) {
        String meta = RECEIVED_META.formatted(DateTimeDisplay.staff(values.receivedAcquiredAt()));
        if (values.receivedIllegible()) {
            return illegible(meta);
        }
        return values.received() == null ? none(NOT_RECORDED) : valuesOf(meta, values.received());
    }

    /** この外部版の採用値の領域（なければその旨、読めなければ情報版・公開した日時・取得した日時と読めない文）。 */
    static ValuesView adoptedOf(UndecidedRecordValues values) {
        UndecidedRecordValues.Adopted adopted = values.adopted();
        if (adopted == null) {
            return none(NO_ADOPTED);
        }
        String meta = ADOPTED_META.formatted(
                adopted.infoVersion(),
                DateTimeDisplay.staff(adopted.publishedAt()),
                DateTimeDisplay.staff(adopted.acquiredAt()));
        return adopted.illegible() ? illegible(meta) : valuesOf(meta, adopted.values());
    }

    /**
     * この外部 ID の採用値の領域（比べる相手の情報版・公開した日時・取得した日時と値。値を読めなければ読めない文。比べる相手がなければ
     * {@code null}。Bolt 39h3）。
     */
    static ValuesView partnerOf(UndecidedRecordValues values) {
        UndecidedRecordValues.Adopted partner = values.partner();
        if (partner == null) {
            return null;
        }
        String meta = ADOPTED_META.formatted(
                partner.infoVersion(),
                DateTimeDisplay.staff(partner.publishedAt()),
                DateTimeDisplay.staff(partner.acquiredAt()));
        return partner.illegible() ? illegible(meta) : valuesOf(meta, partner.values());
    }

    /** この外部 ID の採用値の領域の見出しの後の 1 文（版の逆転なら取得した日時の順、そうでなければ公開した日時の順）。 */
    static String partnerNoteOf(UndecidedRecordValues values) {
        return values.versionReversed() ? PARTNER_BY_ACQUIRED : PARTNER_BY_PUBLISHED;
    }

    /**
     * 古い外部版の隔離を採用するときの警告の文（値の型で選ぶ。固定の文で、取込ファイルから来た値を含まない。すべての型に文がある。T-240）。
     *
     * @param received 受け取った値（採用できる隔離は読める値を持つ）
     */
    static String oldVersionWarningOf(RecordValues received) {
        return switch (received) {
            case VoyageValues _ -> OLD_VERSION_VOYAGE;
            case MilestoneValues _ -> OLD_VERSION_MILESTONE;
        };
    }

    /** 同じ値の文（値が等しければ取得時刻が同じかで選ぶ。値が違う・ない・読めなければ {@code null}）。 */
    static String sameValuesTextOf(UndecidedRecordValues values) {
        if (!values.sameValues()) {
            return null;
        }
        return values.sameAcquiredAt() ? SAME_VALUES : SAME_VALUES_BUT_ACQUIRED_AT;
    }

    /**
     * 実績の種類の表示名。
     *
     * @throws IllegalStateException 表示名のない値（値を足して表を直し忘れたとき）
     */
    static String kindName(MilestoneKindValue kind) {
        String name = KIND_NAMES.get(kind);
        if (name == null) {
            throw new IllegalStateException("実績の種類の表示名がありません: " + kind);
        }
        return name;
    }

    private static ValuesView none(String emptyText) {
        return new ValuesView(null, List.of(), List.of(), emptyText);
    }

    private static ValuesView illegible(String meta) {
        return new ValuesView(meta, List.of(), List.of(), ILLEGIBLE);
    }

    private static ValuesView valuesOf(String meta, RecordValues values) {
        return switch (values) {
            case VoyageValues voyage ->
                new ValuesView(
                        meta,
                        List.of(new Definition("航海番号", voyage.voyageNumber())),
                        voyage.portCalls().stream()
                                .map(call -> PORT_CALL.formatted(
                                        call.port().unLocode(), time(call.arrivalAt()), time(call.departureAt())))
                                .toList(),
                        null);
            case MilestoneValues milestone ->
                new ValuesView(
                        meta,
                        List.of(
                                new Definition(
                                        "追跡番号", milestone.trackingNumber().value()),
                                new Definition("実績の種類", kindName(milestone.kind())),
                                new Definition("場所", milestone.location().unLocode()),
                                new Definition("発生時刻", time(milestone.occurredAt()))),
                        List.of(),
                        null);
        };
    }

    private static String time(UtcInstant time) {
        return time == null ? NO_TIME : DateTimeDisplay.staff(time.instant());
    }

    /**
     * 判断の画面の値の領域の 1 つ（受け取った値か、この外部版の採用値）。寄港は運航の順の番号付きの並びで示す（表にしない。幅 320 px で横
     * スクロールを出さない。ACC-01）。
     *
     * @param meta 取得した日時（この外部版の採用値は情報版・公開した日時・取得した日時）の文（値の列がない・採用値がなければ {@code null}）
     * @param definitions 値の項目（名前と値の組。航海は航海番号、主要実績は追跡番号・実績の種類・場所・発生時刻）
     * @param portCalls 寄港の文（港・到着・出発。空の時刻は「—」。主要実績は空）
     * @param emptyText 値を示さないときの文（値の列がない・採用値がない・読めない。値を示すなら {@code null}）
     */
    record ValuesView(String meta, List<Definition> definitions, List<String> portCalls, String emptyText) {

        ValuesView {
            definitions = List.copyOf(definitions);
            portCalls = List.copyOf(portCalls);
        }
    }

    /**
     * 値の項目（{@code dl} の名前と値の組）。
     *
     * @param term 名前
     * @param description 値
     */
    record Definition(String term, String description) {}
}
