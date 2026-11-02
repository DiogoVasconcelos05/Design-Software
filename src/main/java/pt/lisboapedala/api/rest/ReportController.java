package pt.lisboapedala.api.rest;

import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.municipal_reporting.query.MonthlyReport;
import pt.lisboapedala.municipal_reporting.query.MonthlyReportQuery;

/** Endpoint 19 de ARCHITECTURE.md §7.3 (US-4, §7.4.4): relatório mensal municipal por freguesia. */
@RestController
public class ReportController {

    private final MonthlyReportQuery monthlyReportQuery;

    public ReportController(MonthlyReportQuery monthlyReportQuery) {
        this.monthlyReportQuery = monthlyReportQuery;
    }

    /** 19. {@code GET /reports/monthly?month=2026-10} → 200. */
    @GetMapping("/reports/monthly")
    public MonthlyReport monthly(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return monthlyReportQuery.generate(month);
    }
}
