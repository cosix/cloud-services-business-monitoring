package com.cimparato.csbm.service.file.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// Definisce il contratto per la creazione di un file storage
public interface FileStorageService {

    String storeFile(MultipartFile file, String jobId);

    Resource loadFileAsResource(String filePathOrName);

    void deleteFile(String filePathOrName);

}
