package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 顧客 Web の見積依頼（C-03 見積依頼の作成・編集の最小形）。
 */
@Controller
@RequestMapping("/customer/transport-requests")
public class TransportRequestController {

    private static final String FORM_VIEW = "quotation/transport-requests/new";
    private static final String LOCATION_FORMAT_MESSAGE = "UN/LOCODE の形式（例: JPTYO）で入力してください";

    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final ProvisionalActorProperties provisionalActor;

    public TransportRequestController(TransportRequestCommandService commandService,
            TransportRequestQueryService queryService, ProvisionalActorProperties provisionalActor) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.provisionalActor = provisionalActor;
    }

    @GetMapping("/new")
    public String newForm(@ModelAttribute TransportRequestForm transportRequestForm) {
        return FORM_VIEW;
    }

    /**
     * 輸送要求を提出する。成功したら完了画面へリダイレクトする（PRG）。
     */
    @PostMapping
    public String submit(@ModelAttribute TransportRequestForm transportRequestForm, BindingResult bindingResult) {
        Optional<Location> origin = toLocation(transportRequestForm.getOrigin(), "origin", bindingResult);
        Optional<Location> destination = toLocation(transportRequestForm.getDestination(), "destination",
                bindingResult);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        TransportRequestId id = commandService.submit(new SubmitTransportRequestCommand(
                new CompanyId(provisionalActor.shipperCompanyId()), new UserId(provisionalActor.userId()),
                origin.orElseThrow(), destination.orElseThrow()));
        return "redirect:/customer/transport-requests/" + id.value() + "/submitted";
    }

    @GetMapping("/{id}/submitted")
    public String submitted(@PathVariable UUID id, Model model) {
        TransportRequest transportRequest = queryService.findById(new TransportRequestId(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("transportRequestId", transportRequest.id().value());
        model.addAttribute("statusLabel", statusLabel(transportRequest.status()));
        return "quotation/transport-requests/submitted";
    }

    private static Optional<Location> toLocation(String value, String field, BindingResult bindingResult) {
        try {
            return Optional.of(new Location(value));
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue(field, "location.format", LOCATION_FORMAT_MESSAGE);
            return Optional.empty();
        }
    }

    private static String statusLabel(TransportRequestStatus status) {
        return switch (status) {
            case UNDER_REVIEW -> "審査中";
        };
    }
}
