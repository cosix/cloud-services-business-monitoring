package com.cimparato.csbm.repository;

import com.cimparato.csbm.domain.model.ServiceFileRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceFileRelationRepository extends JpaRepository<ServiceFileRelation, Long> {
}
