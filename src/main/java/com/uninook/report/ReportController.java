package com.uninook.report;

import com.uninook.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<Long> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.success(reportService.create(authorization, request));
    }
}
