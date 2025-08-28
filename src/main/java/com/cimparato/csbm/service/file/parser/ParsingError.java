package com.cimparato.csbm.service.file.parser;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParsingError {
    private int lineNumber;
    private String rawData;
    private String errorMessage;
}
