package com.cimparato.csbm.service.file.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileParserStrategy {
    private final List<FileParser<?>> parsers;

    public FileParserStrategy(List<FileParser<?>> parsers) {
        this.parsers = parsers;
    }

    @SuppressWarnings("unchecked")
    public <T> FileParser<T> getParser(String fileExtension, Class<T> targetType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileExtension) && parser.getTargetType().equals(targetType))
                .map(parser -> (FileParser<T>) parser)
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No parser available for file extension: " + fileExtension + " and target type: " + targetType.getName()));
    }
}
