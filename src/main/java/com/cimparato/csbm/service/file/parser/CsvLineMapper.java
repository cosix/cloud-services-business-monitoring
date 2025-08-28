package com.cimparato.csbm.service.file.parser;

import com.cimparato.csbm.web.rest.errors.CsvMappingException;

// Definisce il contratto per mappare una riga CSV in un oggetto di dominio
public interface CsvLineMapper<T> {

    // Mappa una riga CSV in un oggetto del tipo specificato
    T mapLine(String[] line, int lineNumber) throws CsvMappingException;
}
