package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.AddressDto;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.core.user.AddressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 收貨地址簿 REST API（PRD §14.3.1 Phase 2-B，Sprint 87）
 */
@Slf4j
@RestController
@RequestMapping("/v2/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ResponseEntity<ApiResponse<List<AddressDto.Response>>> listAddresses() {
        List<AddressDto.Response> addresses = addressService.listAddresses();
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<AddressDto.Response>> createAddress(
            @Valid @RequestBody AddressDto.CreateRequest request) {
        AddressDto.Response address = addressService.createAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address created", address));
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<AddressDto.Response>> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressDto.UpdateRequest request) {
        AddressDto.Response address = addressService.updateAddress(addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated", address));
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID addressId) {
        addressService.deleteAddress(addressId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted", null));
    }

    @PutMapping("/{addressId}/default")
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<AddressDto.Response>> setDefaultAddress(@PathVariable UUID addressId) {
        AddressDto.Response address = addressService.setDefaultAddress(addressId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated", address));
    }
}
