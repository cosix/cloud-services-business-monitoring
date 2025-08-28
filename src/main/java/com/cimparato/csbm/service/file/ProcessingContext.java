package com.cimparato.csbm.service.file;

import com.cimparato.csbm.domain.model.CloudService;
import com.cimparato.csbm.domain.model.ServiceFileRelation;
import com.cimparato.csbm.dto.processingerror.ProcessingErrorCreateDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe per mantenere il contesto di elaborazione dei file
 */
@Builder
@Getter
public class ProcessingContext {

    @Builder.Default
    int validRecords = 0;

    @Builder.Default
    List<ProcessingErrorCreateDTO> processingErrors = new ArrayList<>();

    List<CloudService> batchServices;
    List<ServiceFileRelation> batchRelations;

    /**
     * Crea un nuovo contesto di elaborazione con le liste inizializzate alla dimensione del batch.
     *
     * @param batchSize La dimensione del batch per inizializzare le liste
     * @return Un nuovo contesto di elaborazione
     */
    public static ProcessingContext createWithBatchSize(int batchSize) {
        return ProcessingContext.builder()
                .batchServices(new ArrayList<>(batchSize))
                .batchRelations(new ArrayList<>(batchSize))
                .build();
    }

}
