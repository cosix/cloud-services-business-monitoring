package com.cimparato.csbm.web.rest;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.dto.jobexecution.JobStatusDTO;
import com.cimparato.csbm.service.file.FileUploadService;
import com.cimparato.csbm.util.PagedResponse;
import com.cimparato.csbm.util.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/jobs")
@Tag(name = "Jobs", description = "Job APIs")
public class JobController {

    private final FileUploadService fileUploadService;

    public JobController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @LogMethod(measureTime = true)
    @GetMapping("/{jobId}")
    @Operation(
            summary = "Get job status",
            description = """
                    Get the status of a file processing job.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Job status retrieved successfully"),
                    @ApiResponse(responseCode = "404", description = "Job not found"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @PreAuthorize("hasRole('data_uploader')")
    public ResponseEntity<ResponseWrapper<JobStatusDTO>> getJobStatus(
            @Parameter(
                    description = "Job id",
                    required = true
            )
            @PathVariable String jobId
    ) {
        var status = fileUploadService.getJobStatus(jobId);
        return ResponseEntity.ok(new ResponseWrapper<>(
                true,
                "Job status retrieved",
                status)
        );
    }

    @LogMethod(measureTime = true)
    @GetMapping("/file/{fileHash}")
    @Operation(
            summary = "Get jobs by file hash",
            description = """
            Get all processing jobs associated with a file, results are paginated.
            
            ### Pagination
            - `page`: Zero-based page index (0..N)
            - `size`: The size of the page to be returned (1..100)
            - `sort`: Optional sorting criteria in the format: property(,asc|desc)
              Multiple sort criteria are supported.
            
            ### Available Sort Fields
            - `id`: Job's internal ID
            - `jobId`: Unique job identifier
            - `status`: Job status
            - `startTime`: When the job started
            - `endTime`: When the job completed
            - `createdBy`: User who created the job

            ### Examples
            ```
            - /v1/jobs/file/abc123?page=1&size=10&sort=startTime,desc
            - /v1/jobs/file/abc123?sort=status,asc&sort=startTime,desc
            ```
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully"),
                    @ApiResponse(responseCode = "404", description = "File not found"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @Parameters({
            @Parameter(name = "page", description = "Zero-based page index (0..N)", schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "The size of the page to be returned", schema = @Schema(type = "integer", defaultValue = "50")),
            @Parameter(name = "sort", description = "Sorting criteria in the format: property(,asc|desc). Default sort property is startTime and default order is descending.", schema = @Schema(type = "string"))
    })
    @PreAuthorize("hasRole('data_uploader')")
    public ResponseEntity<ResponseWrapper<PagedResponse<JobStatusDTO>>> getJobsByFileId(
            @Parameter(
                    description = "File hash",
                    required = true
            )
            @PathVariable String fileHash,

            @Parameter(hidden = true)
            @PageableDefault(size = 50, sort = "startTime", direction = Sort.Direction.DESC)
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "startTime", direction = Sort.Direction.DESC)
            })
            Pageable pageable
    ) {
        var jobs = fileUploadService.getJobsByFileHash(fileHash, pageable);

        if (jobs.isEmpty() && pageable.getPageNumber() == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseWrapper<>(
                            false,
                            "No jobs found for file",
                            new PagedResponse(Page.empty()))
                    );
        }

        // converte Page<JobStatusDTO> in PagedResponse<JobStatusDTO>
        PagedResponse<JobStatusDTO> pagedResponse = new PagedResponse<>(jobs);

        return ResponseEntity.ok(new ResponseWrapper<>(
                true,
                "Jobs retrieved successfully",
                pagedResponse)
        );
    }
}
