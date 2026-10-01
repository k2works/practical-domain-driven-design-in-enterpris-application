package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.UUID;

/**
 * 見積りのステップ定義。見積りの入力ポートだけを呼ぶ（AT-05）。
 */
public class TransportRequestSteps {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId SUBMITTER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final ScenarioContext context;

    public TransportRequestSteps(TransportRequestCommandService commandService,
            TransportRequestQueryService queryService, ScenarioContext context) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.context = context;
    }

    @もし("荷主が出発地 {string}、目的地 {string} の輸送要求を提出する")
    public void 荷主が輸送要求を提出する(String origin, String destination) {
        TransportRequestId id = commandService.submit(new SubmitTransportRequestCommand(SHIPPER, SUBMITTER,
                new Location(origin), new Location(destination)));
        context.transportRequestId(id.value());
    }

    @ならば("輸送要求は審査中になる")
    public void 輸送要求は審査中になる() {
        assertThat(queryService.findById(new TransportRequestId(context.transportRequestId())))
                .hasValueSatisfying(request -> assertThat(request.status())
                        .isEqualTo(TransportRequestStatus.UNDER_REVIEW));
    }
}
