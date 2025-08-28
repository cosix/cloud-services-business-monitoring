package com.cimparato.csbm.service.report;

import com.cimparato.csbm.dto.report.SummaryReportDTO;

public interface PdfGenerator {
    byte[] generatePdf(SummaryReportDTO data);
}
