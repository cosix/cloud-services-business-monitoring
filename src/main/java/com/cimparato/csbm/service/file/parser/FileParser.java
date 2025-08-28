package com.cimparato.csbm.service.file.parser;

import com.cimparato.csbm.web.rest.errors.FileParsingException;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

// Definisce il contratto per la conversione di un file in una collezione di oggetti
public interface FileParser<T> {


     // Effettua il parsing di un file e applica il processor a ciascun oggetto man mano che viene letto, senza caricare l'intero file in memoria.
    void parse(InputStream inputStream, Consumer<T> processor) throws FileParsingException;

    // Verifica se questo parser supporta il formato di file specificato dall'estensione
    boolean supports(String fileExtension);

    // Restituisce il tipo di classe degli oggetti prodotti da questo parser
    Class<T> getTargetType();

    // Restituisce gli errori di parsing riscontrati durante l'ultima operazione di parsing.
    List<ParsingError> getParsingErrors();
}
