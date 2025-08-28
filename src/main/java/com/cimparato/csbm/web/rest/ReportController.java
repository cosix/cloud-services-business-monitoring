package com.cimparato.csbm.web.rest;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.dto.report.SummaryReportDTO;
import com.cimparato.csbm.service.report.ReportService;
import com.cimparato.csbm.util.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/report")
@Tag(name = "Report", description = "Report generation APIs")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @LogMethod(measureTime = true)
    @GetMapping("/summary")
    @Operation(
            summary = "Build summary report",
            description = """
                    Generates an aggregated report containing the following statistics:
                    1. total active services by type
                    2. average spending per customer
                    3. list of customers with more than one expired service
                    4. list of customers with services expiring within the next 15 days
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Report generated successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseWrapper.class)
                            )
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error occurred while generating the report")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @PreAuthorize("hasRole('data_analyst')")
    public ResponseEntity<ResponseWrapper<SummaryReportDTO>> buildSummaryReport() {
        SummaryReportDTO summaryReportDTO = reportService.generateSummaryReport();
        return ResponseEntity.ok(new ResponseWrapper<>(
                true,
                "Summary Report generated successfully",
                summaryReportDTO)
        );
    }

    @LogMethod(measureTime = true)
    @GetMapping("/summary/pdf")
    @Operation(
            summary = "Generate summary report in PDF format",
            description = """
                    Generates a PDF report containing the same information as the JSON summary report.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "PDF report generated successfully",
                            content = @Content(mediaType = "application/pdf")
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error occurred while generating the report")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @PreAuthorize("hasRole('data_analyst')")
    public ResponseEntity<byte[]> generateSummaryReportPdf() {
        byte[] pdfBytes = reportService.generateSummaryReportPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("filename", "summary-report.pdf");
        headers.setCacheControl("must-revalidate"); // assicura che gli utenti ottengano sempre la versione più recente

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
