-- 確定した経路版と確定した候補を表でも守る（Bolt 19 の開発レビュー。R-INV-05・06）。
-- 案件の確定した経路版の番号は、その案件の経路版を指す。確定の記録の候補番号は、その経路版の候補を指す（確定した候補を消せない）。
-- どちらも NULL を許す（未確定）。確定していない経路版の候補の消して入れ直す（再算出）は妨げない。
ALTER TABLE routing.routing_case ADD CONSTRAINT fk_routing_case_confirmed_version
    FOREIGN KEY (id, confirmed_route_version_no) REFERENCES routing.route_version (routing_case_id, route_version_no);
ALTER TABLE routing.route_version ADD CONSTRAINT fk_route_version_selected_candidate
    FOREIGN KEY (routing_case_id, route_version_no, selected_candidate_no)
    REFERENCES routing.route_candidate (routing_case_id, route_version_no, candidate_no);
