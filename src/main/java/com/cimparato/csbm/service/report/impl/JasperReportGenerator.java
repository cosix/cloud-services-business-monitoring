package com.cimparato.csbm.service.report.impl;

import com.cimparato.csbm.dto.report.SummaryReportDTO;
import com.cimparato.csbm.service.report.PdfGenerator;
import com.cimparato.csbm.web.rest.errors.ReportGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JasperReportGenerator implements PdfGenerator {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public JasperReportGenerator(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] generatePdf(SummaryReportDTO data) {
        try {

            Map<String, Object> params = buildParameters(data);

            // carica il template
            Resource resource = resourceLoader.getResource("classpath:reports/summary_report.jrxml");
            InputStream inputStream = resource.getInputStream();

            // compila il report
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);

            // riempi il report con i dati
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, new JREmptyDataSource());

            // esporta in PDF
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (JRException e) {
            throw new ReportGenerationException("Failed to compile report template: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to read report template: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate PDF report", e);
        }
    }

    private Map<String, Object> buildParameters(SummaryReportDTO summaryData) {

        Map<String, Object> parameters = new HashMap<>();

        // activeServicesByType in una lista di mappe
        List<Map<String, Object>> activeServices = new ArrayList<>();
        summaryData.getActiveServicesByType().forEach((key, value) -> {
            Map<String, Object> service = new HashMap<>();
            service.put("serviceType", key);
            service.put("count", value);
            activeServices.add(service);
        });
        parameters.put("activeServices", new JRBeanCollectionDataSource(activeServices));

        // averageSpendingPerCustomer in una lista di mappe
        List<Map<String, Object>> averageSpending = new ArrayList<>();
        summaryData.getAverageSpendingPerCustomer().forEach((key, value) -> {
            Map<String, Object> spending = new HashMap<>();
            spending.put("customerId", key);
            spending.put("amount", value);
            averageSpending.add(spending);
        });
        parameters.put("averageSpending", new JRBeanCollectionDataSource(averageSpending));

        // liste di clienti in liste di mappe
        List<Map<String, Object>> expiredServices = new ArrayList<>();
        for (String customerId : summaryData.getCustomersWithMultipleExpiredServices()) {
            Map<String, Object> customer = new HashMap<>();
            customer.put("customerId", customerId);
            expiredServices.add(customer);
        }
        parameters.put("expiredServices", new JRBeanCollectionDataSource(expiredServices));

        List<Map<String, Object>> expiringServices = new ArrayList<>();
        for (String customerId : summaryData.getCustomersWithServicesExpiringInNext15Days()) {
            Map<String, Object> customer = new HashMap<>();
            customer.put("customerId", customerId);
            expiringServices.add(customer);
        }
        parameters.put("expiringServices", new JRBeanCollectionDataSource(expiringServices));

        return parameters;
    }
}
