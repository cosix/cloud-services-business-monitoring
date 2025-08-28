package com.cimparato.csbm.service.file.validator;

import org.springframework.core.Ordered;
import org.springframework.web.multipart.MultipartFile;

// Interfaccia che definisce una regola di validazione per i file caricati
public interface FileValidationRule extends Ordered {

    // Valida un file caricato secondo una regola specifica
    boolean validate(MultipartFile file);

    // Restituisce un messaggio di errore quando la validazione fallisce
    String getErrorMessage();
}
