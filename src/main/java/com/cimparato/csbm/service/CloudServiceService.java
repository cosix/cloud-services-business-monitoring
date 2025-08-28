package com.cimparato.csbm.service;

import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.dto.cloudservice.CustomerWithExpiredServicesDTO;
import com.cimparato.csbm.dto.cloudservice.ServiceWithExpirationDTO;
import com.cimparato.csbm.mapper.CloudServiceMapper;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.repository.projection.CustomerAverageSpend;
import com.cimparato.csbm.repository.projection.CustomerWithExpiredService;
import com.cimparato.csbm.repository.projection.ServiceTypeCount;
import com.cimparato.csbm.web.rest.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CloudServiceService {

    private final CloudServiceRepository cloudServiceRepository;
    private final CloudServiceMapper cloudServiceMapper;

    public CloudServiceService(CloudServiceRepository cloudServiceRepository, CloudServiceMapper cloudServiceMapper) {
        this.cloudServiceRepository = cloudServiceRepository;
        this.cloudServiceMapper = cloudServiceMapper;
    }

    @Transactional(readOnly = true)
    public Page<CloudServiceDTO> findByCustomerId(String customerId, Pageable pageable) {
        return cloudServiceRepository.findByCustomerId(customerId, pageable)
                .map(cloudServiceMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<CloudServiceDTO> findByCustomerIdAndServiceType(String customerId, CloudServiceType serviceType) {
        return cloudServiceRepository
                .findByCustomerIdAndServiceType(customerId, serviceType.name())
                .map(cloudServiceMapper::toDto);
    }

    @Transactional(readOnly = true)
    public CloudServiceDTO getByCustomerIdAndServiceTypeWithException(String customerId, String serviceType) {
        return cloudServiceRepository
                .findByCustomerIdAndServiceType(customerId, serviceType)
                .map(cloudServiceMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("CloudService not found with customerId: " + customerId + ", serviceType: " + serviceType));
    }

    @Transactional(readOnly = true)
    public CustomerWithExpiredServicesDTO getCustomersWithMaxExpiredServices(int maxExpiredServicesCount) {
        List<CustomerWithExpiredService> results = cloudServiceRepository.findCustomersWithMaxExpiredServices(maxExpiredServicesCount);

        Map<String, Set<ServiceWithExpirationDTO>> customerServicesMap = results.stream()
                .collect(Collectors.groupingBy(
                        CustomerWithExpiredService::getCustomerId,
                        Collectors.mapping(
                                es -> new ServiceWithExpirationDTO(
                                        CloudServiceType.valueOf(es.getServiceType()),
                                        es.getExpirationDate()
                                ),
                                Collectors.toSet()
                        )
                ));
        return new CustomerWithExpiredServicesDTO(customerServicesMap);
    }

    @Transactional(readOnly = true)
    public List<CloudServiceDTO> getActiveServicesOlderThan(LocalDate yearsAgo) {
        return cloudServiceRepository.findActiveServicesOlderThan(yearsAgo)
                .stream()
                .map(cloudServiceMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getActiveServicesByType() {
        return cloudServiceRepository.findActiveServicesByType().stream()
                .collect(Collectors.toMap(
                        ServiceTypeCount::serviceType,
                        ServiceTypeCount::count,
                        (e1, e2) -> e1,
                        LinkedHashMap::new // per mantenere l'ordine di inserimento
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getAverageSpendPerCustomer() {
        return cloudServiceRepository.calculateAverageSpendPerCustomer().stream()
                .collect(Collectors.toMap(
                        CustomerAverageSpend::customerId,
                        CustomerAverageSpend::averageAmount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public List<String> getCustomersWithMultipleExpiredServices() {
        return cloudServiceRepository.findCustomersWithMultipleExpiredServices();
    }

}
