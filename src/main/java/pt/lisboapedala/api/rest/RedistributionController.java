package pt.lisboapedala.api.rest;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.redistribution_planning.query.RedistributionQuery;
import pt.lisboapedala.redistribution_planning.query.VanRoute;

/** Endpoint 18 de ARCHITECTURE.md §7.3 (Req. 6): rotas de reequilíbrio sugeridas às carrinhas. */
@RestController
public class RedistributionController {

    private final RedistributionQuery redistributionQuery;

    public RedistributionController(RedistributionQuery redistributionQuery) {
        this.redistributionQuery = redistributionQuery;
    }

    /** 18. {@code GET /redistribution/routes?vans=2&vanCapacity=20} → 200. */
    @GetMapping("/redistribution/routes")
    public List<VanRoute> suggestRoutes(@RequestParam int vans, @RequestParam int vanCapacity) {
        return redistributionQuery.suggestRoutes(vans, vanCapacity);
    }
}
