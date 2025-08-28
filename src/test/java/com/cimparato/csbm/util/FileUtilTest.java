package com.cimparato.csbm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {

    @Test
    @DisplayName("Verifica che l'hash venga calcolato correttamente per un file")
    void testCalculateFileHash() throws IOException {

        // arrange
        String content = "test content";
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content.getBytes());

        // cct
        String hash = FileUtil.calculateFileHash(file);

        // assert
        assertNotNull(hash);
        assertEquals(32, hash.length()); // MD5 hash è lungo 32 caratteri in esadecimale
    }

    @Test
    @DisplayName("Verifica che file identici producano lo stesso hash")
    void testIdenticalFilesProduceSameHash() throws IOException {

        // arrange
        String content = "identical content";
        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", content.getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "test2.txt", "text/plain", content.getBytes());

        // act
        String hash1 = FileUtil.calculateFileHash(file1);
        String hash2 = FileUtil.calculateFileHash(file2);

        // assert
        assertEquals(hash1, hash2, "Hash di file identici dovrebbero essere uguali");
    }

    @Test
    @DisplayName("Verifica che file diversi producano hash diversi")
    void testDifferentFilesProduceDifferentHash() throws IOException {

        // arrange
        String content1 = "content one";
        String content2 = "content two";
        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", content1.getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "test2.txt", "text/plain", content2.getBytes());

        // act
        String hash1 = FileUtil.calculateFileHash(file1);
        String hash2 = FileUtil.calculateFileHash(file2);

        // assert
        assertNotEquals(hash1, hash2, "Hash di file diversi dovrebbero essere diversi");
    }

    @Test
    @DisplayName("Verifica che l'hash sia resistente ai cambiamenti di nome file")
    void testHashIsIndependentOfFileName() throws IOException {

        // arrange
        String content = "same content";
        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", content.getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "different_name.txt", "text/plain", content.getBytes());

        // act
        String hash1 = FileUtil.calculateFileHash(file1);
        String hash2 = FileUtil.calculateFileHash(file2);

        // assert
        assertEquals(hash1, hash2, "Hash dovrebbe essere indipendente dal nome del file");
    }

    @Test
    @DisplayName("Verifica che l'hash gestisca correttamente file vuoti")
    void testHashForEmptyFile() throws IOException {

        // arrange
        MultipartFile emptyFile = new MockMultipartFile("emptyFile", "empty.txt", "text/plain", new byte[0]);

        // act
        String hash = FileUtil.calculateFileHash(emptyFile);

        // assert
        assertNotNull(hash);
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hash, "Hash di un file vuoto dovrebbe essere d41d8cd98f00b204e9800998ecf8427e");
    }

    @Test
    @DisplayName("Verifica che l'hash gestisca correttamente file null")
    void testHashForNullFile() {
        // act & assert
        assertThrows(NullPointerException.class, () -> FileUtil.calculateFileHash(null),
                "Dovrebbe lanciare NullPointerException per file null");
    }
}
