package com.cimparato.csbm.web.rest;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.util.PagedResponse;
import com.cimparato.csbm.util.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
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

import java.util.Optional;

@RestController
@RequestMapping("/v1/cloud-services")
@Tag(name = "Cloud Services", description = "Cloud Services management APIs")
public class CloudServiceController {

    private final CloudServiceService cloudServiceService;

    public CloudServiceController(CloudServiceService cloudServiceService) {
        this.cloudServiceService = cloudServiceService;
    }

    @LogMethod(measureTime = true)
    @GetMapping("/customer/{customerId}")
    @Operation(
            summary = "Retrieve all cloud services for a customer",
            description = """
                    Returns details about all cloud services for the given customer ID, results are paginated.
                    
                    ### Pagination
                    - `page`: Zero-based page index (0..N)
                    - `size`: The size of the page to be returned (1..100)
                    - `sort`: Optional sorting criteria in the format: property(,asc|desc). Multiple sort criteria are supported.
                    
                    ### Available Sort Fields
                    - `id`: Id of the cloud service
                    - `customerId`: Customer identifier
                    - `serviceType`: Type of cloud service
                    - `activationDate`: Date when the service was activated
                    - `expirationDate`: Date when the service will expire
                    - `amount`: Cost of the service in euros
                    - `status`: Current status of the service
                    - `lastUpdated`: Timestamp of the last update
                    
                    ### Example
                    ```
                    GET /v1/cloud-services/customer/CUST123?page=0&size=10&sort=activationDate,desc
                    ```
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Cloud services found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseWrapper.class)
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "Cloud services not found"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @Parameters({
            @Parameter(name = "page", description = "Zero-based page index (0..N)", schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "The size of the page to be returned", schema = @Schema(type = "integer", defaultValue = "50")),
            @Parameter(name = "sort", description = "Sorting criteria in the format: property(,asc|desc). Default sort property is activationDate and default order is descending.", schema = @Schema(type = "string"))
    })
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ResponseWrapper<PagedResponse<CloudServiceDTO>>> getCloudServicesByCustomerId(
            @Parameter(
                    description = "Customer Id",
                    required = true,
                    example = "CUST001"
            )
            @PathVariable
            String customerId,

            @Parameter(hidden = true)
            @PageableDefault(size = 50, sort = "activationDate", direction = Sort.Direction.DESC)
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "activationDate", direction = Sort.Direction.DESC)
            })
            Pageable pageable
    ) {
        var services  = cloudServiceService.findByCustomerId(customerId, pageable);

        if (services.isEmpty() && pageable.getPageNumber() == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseWrapper<>(
                    true,
                    "No cloud services found for the specified customer",
                    new PagedResponse(Page.empty()))
            );
        }

        // converte Page<CloudServiceDTO> in PagedResponse<CloudServiceDTO>
        PagedResponse<CloudServiceDTO> pagedResponse = new PagedResponse<>(services);

        return ResponseEntity.ok(new ResponseWrapper<>(
                true,
                "Cloud services retrieved successfully",
                pagedResponse));
    }



    @LogMethod(measureTime = true)
    @GetMapping("/service/{serviceType}/customer/{customerId}")
    @Operation(
            summary = """
                    Retrieve information about a specific cloud service for a customer.
                    """,
            description = "Returns details about the specified service type for the given customer ID",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Cloud service found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseWrapper.class)
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "Cloud service not found"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
            },
            security = @SecurityRequirement(name = "oauth2")
    )
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ResponseWrapper<CloudServiceDTO>> getCloudService(
            @Parameter(
                    description = "Type of cloud service",
                    required = true,
                    schema = @Schema(
                            allowableValues = {"HOSTING", "PEC", "SPID", "FATTURAZIONE", "FIRMA_DIGITALE", "CONSERVAZIONE_DIGITALE"}
                    )
            )
            @PathVariable CloudServiceType serviceType,

            @Parameter(
                    description = "Customer id",
                    required = true
            )
            @PathVariable String customerId
    ) {
        Optional<CloudServiceDTO> service = cloudServiceService.findByCustomerIdAndServiceType(customerId, serviceType);

        return service.map(dto -> ResponseEntity.ok(new ResponseWrapper<>(
                true,
                        "Cloud service retrieved successfully",
                        dto)))
                .orElseGet(()-> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseWrapper<>(
                                false,
                                "Cloud service not found",
                                null)));
    }

}
