package com.cimparato.csbm.repository;

import com.cimparato.csbm.domain.model.ProcessingError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessingErrorRepository extends JpaRepository<ProcessingError, Long> {
}
