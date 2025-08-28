package com.cimparato.csbm.web.rest;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.dto.fileupload.FileUploadSummaryDTO;
import com.cimparato.csbm.config.security.SecurityUtils;
import com.cimparato.csbm.service.file.FileUploadService;
import com.cimparato.csbm.util.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/files")
@Tag(name = "File Uploads", description = "File upload management APIs")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    private final SecurityUtils securityUtils;

    public FileUploadController(FileUploadService fileUploadService, SecurityUtils securityUtils) {
        this.fileUploadService = fileUploadService;
        this.securityUtils = securityUtils;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a new file",
            description = """
                Upload a CSV file containing cloud service usage data for processing.
        
                ### File Requirements
                - **Format**: CSV 
                - **Max Size**: 10MB
                
                ### CSV Structure
                The CSV file must contain the following columns in this exact order:
                
                1. `customer_id` (string): Unique customer identifier
                2. `service_type` (string): Type of service (`hosting`, `pec`, `spid`, `fatturazione`, `firma_digitale`, `conservazione_digitale`)
                3. `activation_date` (ISO date): Service activation date in `YYYY-MM-DD` format
                4. `expiration_date` (ISO date): Service expiration date in `YYYY-MM-DD` format
                5. `amount` (float): Amount paid in euros
                6. `status` (string): Service status (`active`, `expired`, `pending_renewal`)
                
                ### Example CSV Row
                ```
                CUST001,hosting,2023-01-15,2024-01-15,29.99,active
                ```
                
                The file will be validated, processed, and the data will be stored in the system.
                """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "File uploaded successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseWrapper.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid file format or size"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @PreAuthorize("hasRole('data_uploader')")
    public ResponseEntity<ResponseWrapper<FileUploadJobDTO>> uploadFile(
            @Parameter(
                    description = "CSV file to upload",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file
    ) {
        String username = securityUtils.getAuthenticatedUsername();
        FileUploadJobDTO uploadedFile = fileUploadService.uploadFile(file, username);
        return ResponseEntity.ok(new ResponseWrapper<>(
                true,
                "File uploaded successfully",
                uploadedFile)
        );
    }



    @LogMethod(measureTime = true)
    @GetMapping("/{fileHash}")
    @Operation(
            summary = "Get file information",
            description = """
                    Get all information about a file including parsing errors.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "File retrieved successfully"),
                    @ApiResponse(responseCode = "404", description = "File not found"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @PreAuthorize("hasRole('data_uploader')")
    public ResponseEntity<ResponseWrapper<FileUploadSummaryDTO>> getFileByHashWithSummary(
            @Parameter(
                    description = "File hash",
                    required = true
            )
            @PathVariable String fileHash
    ) {
        var fileUpload = fileUploadService.findByHashWithSummary(fileHash);
        return ResponseEntity.ok(new ResponseWrapper<>(
                true,
                "File retrieved successfully",
                fileUpload)
        );
    }

}
