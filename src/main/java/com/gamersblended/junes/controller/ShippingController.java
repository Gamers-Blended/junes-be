package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.dto.request.CalculateShippingRequest;
import com.gamersblended.junes.exception.InvalidProductIdException;
import com.gamersblended.junes.exception.NegativeWeightException;
import com.gamersblended.junes.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/shipping")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @Operation(summary = "Get shipping fees from list of items")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shipping fees successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid product ID",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidProductIdException.class))}),
            @ApiResponse(responseCode = "400", description = "Calculated shipping weight is negative",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = NegativeWeightException.class))})
    })
    @PostMapping("/calculate")
    public ResponseEntity<ResponseMessage> getShippingFee(@RequestBody CalculateShippingRequest calculateShippingRequest) {

        log.info("Calculating shipping fees...");
        String shippingFees = shippingService.getShippingFee(calculateShippingRequest.getTransactionItemDTOList());
        return ResponseEntity.ok(new ResponseMessage(shippingFees));
    }
}
