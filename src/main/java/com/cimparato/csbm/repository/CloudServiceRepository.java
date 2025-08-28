package com.cimparato.csbm.repository;

import com.cimparato.csbm.domain.model.CloudService;
import com.cimparato.csbm.repository.projection.CustomerAverageSpend;
import com.cimparato.csbm.repository.projection.CustomerWithExpiredService;
import com.cimparato.csbm.repository.projection.ServiceTypeCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CloudServiceRepository extends JpaRepository<CloudService, Long> {

    Page<CloudService> findByCustomerId(String customerId, Pageable peageble);

    @Query(value = """
            select *
            from cloud_services
            where customer_id = :customerId AND service_type = :serviceType
            """, nativeQuery = true)
    Optional<CloudService> findByCustomerIdAndServiceType(String customerId, String serviceType);

    @Query(value = """
           SELECT   cs.customer_id as customerId,
                    cs.service_type as serviceType,
                    cs.expiration_date as expirationDate
           FROM cloud_services cs
           WHERE cs.status = 'EXPIRED'
           AND cs.customer_id IN (
               SELECT customer_id
               FROM cloud_services
               WHERE status = 'EXPIRED'
               GROUP BY customer_id
               HAVING COUNT(*) > :maxExpiredServicesCount
           )
           ORDER BY cs.customer_id, cs.service_type
            """, nativeQuery = true)
    List<CustomerWithExpiredService> findCustomersWithMaxExpiredServices(@Param("maxExpiredServicesCount") int maxExpiredServicesCount);

    @Query(value = """
                select *
                from
                cloud_services 
                where status = 'ACTIVE' AND activation_date <= :threeYearsAgo
                """, nativeQuery = true)
    List<CloudService> findActiveServicesOlderThan(LocalDate threeYearsAgo);

    @Query(value = """
                select service_type, count(*)
                from cloud_services
                where status = 'ACTIVE'
                group by service_type
                order by count(*) desc
                """, nativeQuery = true)
    List<ServiceTypeCount> findActiveServicesByType();

    @Query(value = """
            select customer_id as customerId, AVG(amount)::NUMERIC(10,2) as averageAmount 
            from cloud_services 
            group by customer_id
            order by AVG(amount) desc
        """, nativeQuery = true)
    List<CustomerAverageSpend> calculateAverageSpendPerCustomer();

    @Query(value = """
            select distinct customer_id
            from cloud_services
            where status = 'EXPIRED'
            group by customer_id HAVING count(*) > 1
            order by customer_id asc
            """, nativeQuery = true)
    List<String> findCustomersWithMultipleExpiredServices();

    @Query(value = """
            select distinct customer_id
            from cloud_services
            where expiration_date BETWEEN :currentDate AND :endDate 
            AND status IN ('ACTIVE', 'PENDING_RENEWAL')
            order by customer_id asc
            """, nativeQuery = true)
    List<String> findCustomersWithServicesExpiringBetween(LocalDate currentDate, LocalDate endDate);

}
