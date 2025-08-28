package com.cimparato.csbm.util;

import jakarta.xml.bind.DatatypeConverter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtil {

    /**
     * Calcola l'hash MD5 di un file.
     *
     * @param file Il file di cui calcolare l'hash
     * @return L'hash MD5 del file come stringa esadecimale
     * @throws IOException Se si verifica un errore durante la lettura del file
     */
    public static String calculateFileHash(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            return DatatypeConverter.printHexBinary(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

}
