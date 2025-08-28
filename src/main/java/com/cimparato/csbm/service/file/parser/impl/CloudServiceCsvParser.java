package com.cimparato.csbm.service.file.parser.impl;

import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.domain.file.FileSupportedExtension;
import com.cimparato.csbm.service.file.parser.CsvLineMapper;
import com.cimparato.csbm.service.file.parser.FileParser;
import com.cimparato.csbm.service.file.parser.ParsingError;
import com.cimparato.csbm.web.rest.errors.FileParsingException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class CloudServiceCsvParser implements FileParser<CloudServiceDTO> {

    private final CsvLineMapper<CloudServiceDTO> lineMapper;
    private final List<ParsingError> parsingErrors = new ArrayList<>();

    public CloudServiceCsvParser(CsvLineMapper<CloudServiceDTO> lineMapper) {
        this.lineMapper = lineMapper;
    }

    @Override
    public void parse(InputStream inputStream, Consumer<CloudServiceDTO> processor)
            throws FileParsingException {
        parsingErrors.clear();

        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            reader.readNext();

            String[] line;
            int lineNumber = 1;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                try {
                    CloudServiceDTO item = lineMapper.mapLine(line, lineNumber);
                    processor.accept(item);
                } catch (Exception e) {
                    parsingErrors.add(new ParsingError(lineNumber, String.join(",", line), e.getMessage()));
                    log.warn("Error parsing line {}: {}", lineNumber, e.getMessage());
                }
            }

        } catch (IOException | CsvValidationException e) {
            throw new FileParsingException("Error reading CSV file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return FileSupportedExtension.CSV.name().equalsIgnoreCase(fileExtension);
    }

    @Override
    public Class<CloudServiceDTO> getTargetType() {
        return CloudServiceDTO.class;
    }

    @Override
    public List<ParsingError> getParsingErrors() {
        return new ArrayList<>(parsingErrors);
    }
}
