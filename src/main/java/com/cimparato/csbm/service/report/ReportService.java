package com.cimparato.csbm.service.report;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.dto.report.SummaryReportDTO;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.service.CloudServiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReportService {

    private final CloudServiceRepository cloudServiceRepository;
    private final CloudServiceService cloudServiceService;
    private final PdfGenerator pdfGenerator;

    public ReportService(CloudServiceRepository cloudServiceRepository, CloudServiceService cloudServiceService, PdfGenerator pdfGenerator) {
        this.cloudServiceRepository = cloudServiceRepository;
        this.cloudServiceService = cloudServiceService;
        this.pdfGenerator = pdfGenerator;
    }

    @LogMethod(level = LogLevel.INFO, logParams = true, logResult = true, measureTime = true, message = "Generating report")
    public SummaryReportDTO generateSummaryReport() {
        SummaryReportDTO report = new SummaryReportDTO();

        // servizi attivi per tipo
        Map<String, Long> activeServicesByType = cloudServiceService.getActiveServicesByType();
        report.setActiveServicesByType(activeServicesByType);

        // spesa media per cliente
        Map<String, BigDecimal> averageSpendPerCustomer = cloudServiceService.getAverageSpendPerCustomer();
        report.setAverageSpendingPerCustomer(averageSpendPerCustomer);

        // clienti con più di un servizio scaduto
        List<String> customersWithMultipleExpired = cloudServiceService.getCustomersWithMultipleExpiredServices();
        report.setCustomersWithMultipleExpiredServices(customersWithMultipleExpired);

        // clienti con servizi in scadenza entro i prossimi 15 giorni
        LocalDate now = LocalDate.now();
        LocalDate in15Days = LocalDate.now().plusDays(15);
        List<String> customersWithExpiringServices = cloudServiceRepository
                .findCustomersWithServicesExpiringBetween(now, in15Days);
        report.setCustomersWithServicesExpiringInNext15Days(customersWithExpiringServices);

        return report;
    }

    public byte[] generateSummaryReportPdf() {
        SummaryReportDTO summaryReport = generateSummaryReport();
        return pdfGenerator.generatePdf(summaryReport);
    }
}
