package com.cimparato.csbm.service.file.storage.impl;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.web.rest.errors.FileStorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private Path fileStorageLocation;

    private final AppProperties appProperties;

    public LocalFileStorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        var uploadDir = appProperties.getFileProcessing().getUploadDir();
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);

            if (!Files.isWritable(this.fileStorageLocation)) {
                throw new FileStorageException("Upload directory is not writable: " + this.fileStorageLocation);
            }
        } catch (IOException ex) {
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored", ex);
        }
    }

    public String storeFile(MultipartFile file, String jobId) {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String uniqueFileName = jobId + "_" + fileName;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return uniqueFileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }

    /**
     * Carica un file come Resource a partire dal suo percorso relativo o assoluto.
     *
     * @param filePathOrName Il percorso relativo o assoluto del file
     * @return La Resource che rappresenta il file
     */
    public Resource loadFileAsResource(String filePathOrName) {
        try {

            String errorMessage = "Access denied. Cannot access files outside the upload directory.";
            Path filePath = getFilePath(filePathOrName, errorMessage);

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found: " + filePath);
            }
        } catch (MalformedURLException | FileNotFoundException ex) {
            throw new FileStorageException("File not found: " + filePathOrName, ex);
        }
    }

    /**
     * Elimina un file a partire dal suo percorso relativo o assoluto
     *
     * @param filePathOrName Il percorso relativo o assoluto del file
     */
    public void deleteFile(String filePathOrName) {
        try {

            var errorMessage = "Access denied. Cannot delete files outside the upload directory";
            Path filePath = getFilePath(filePathOrName, errorMessage);

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("File deleted: {}", filePath);
            } else {
                log.debug("File not found for deletion: {}", filePath);
            }
        } catch (IOException ex) {
            log.error("Could not delete file: {}", filePathOrName, ex);
            throw new FileStorageException("Could not delete file: " + filePathOrName, ex);
        }
    }

    private Path getFilePath(String filePathOrName, String errorMessage) {

        Path filePath;

        Path inputPath = Paths.get(filePathOrName);
        if (inputPath.isAbsolute()) {
            filePath = inputPath.normalize();
            // Verifica che il path assoluto sia all'interno della directory di upload
            if (!filePath.startsWith(this.fileStorageLocation)) {
                throw new FileStorageException(errorMessage);
            }
        } else {
            // Normalizza il inputPath relativo per rimuovere sequenze come "../"
            String normalizedPath = inputPath.normalize().toString();
            if (normalizedPath.startsWith("..")) {
                throw new FileStorageException(errorMessage);
            }
            filePath = this.fileStorageLocation.resolve(normalizedPath);
        }
        return filePath;
    }
}
