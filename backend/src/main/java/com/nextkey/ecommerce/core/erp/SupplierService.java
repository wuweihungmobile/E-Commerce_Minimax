package com.nextkey.ecommerce.core.erp;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.erp.SupplierCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.SupplierDto;
import com.nextkey.ecommerce.api.dto.erp.SupplierUpdateRequest;
import com.nextkey.ecommerce.domain.model.erp.Supplier;
import com.nextkey.ecommerce.domain.repository.SupplierRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 供應商 Service
 * PRD §9.15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    /**
     * 建立供應商
     */
    @Transactional
    public SupplierDto createSupplier(final SupplierCreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Supplier supplier = Supplier.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .contactPerson(request.getContactPerson())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();

        Supplier saved = supplierRepository.save(supplier);
        log.info("Created supplier: id={}, tenantId={}", saved.getId(), tenantId);

        return toDto(saved);
    }

    /**
     * 取得單一供應商
     */
    @Transactional(readOnly = true)
    public SupplierDto getSupplier(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Supplier supplier = findByIdAndTenantId(id, tenantId);
        return toDto(supplier);
    }

    /**
     * 列出供應商列表
     */
    @Transactional(readOnly = true)
    public List<SupplierDto> listSuppliers(final String status) {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<Supplier> suppliers;
        if (status != null && !status.isEmpty()) {
            Supplier.SupplierStatus supplierStatus = Supplier.SupplierStatus.valueOf(status.toUpperCase());
            suppliers = supplierRepository.findByTenantIdAndStatus(tenantId, supplierStatus);
        } else {
            suppliers = supplierRepository.findByTenantId(tenantId);
        }

        return suppliers.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 搜尋供應商（依名稱模糊比對）
     */
    @Transactional(readOnly = true)
    public List<SupplierDto> searchSuppliers(final String keyword) {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<Supplier> suppliers = supplierRepository.searchByName(tenantId, keyword);
        return suppliers.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 更新供應商
     */
    @Transactional
    public SupplierDto updateSupplier(final UUID id, final SupplierUpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Supplier supplier = findByIdAndTenantId(id, tenantId);

        if (request.getName() != null) {
            supplier.setName(request.getName());
        }
        if (request.getContactPerson() != null) {
            supplier.setContactPerson(request.getContactPerson());
        }
        if (request.getEmail() != null) {
            supplier.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            supplier.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            supplier.setAddress(request.getAddress());
        }
        if (request.getStatus() != null) {
            try {
                supplier.setStatus(Supplier.SupplierStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.E_7010, "Invalid supplier status: " + request.getStatus());
            }
        }

        Supplier updated = supplierRepository.save(supplier);
        log.info("Updated supplier: id={}, tenantId={}", id, tenantId);

        return toDto(updated);
    }

    /**
     * 依 ID 和 Tenant 取得供應商，若不存在拋例外
     */
    private Supplier findByIdAndTenantId(final UUID id, final UUID tenantId) {
        return supplierRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7000,
                        String.format("Supplier not found: id=%s, tenantId=%s", id, tenantId)));
    }

    /**
     * 轉換為 DTO
     */
    private SupplierDto toDto(final Supplier supplier) {
        return SupplierDto.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .contactPerson(supplier.getContactPerson())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .address(supplier.getAddress())
                .status(supplier.getStatus() != null ? supplier.getStatus().name() : null)
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}