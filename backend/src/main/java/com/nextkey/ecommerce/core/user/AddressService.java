package com.nextkey.ecommerce.core.user;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.AddressDto;
import com.nextkey.ecommerce.domain.model.user.Address;
import com.nextkey.ecommerce.domain.repository.AddressRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 收貨地址簿服務（PRD §14.3.1 Phase 2-B，Sprint 87）
 *
 * <p>地址簿屬於買家個人資料，與租戶無關（買家可能向多個不同租戶下單），擁有權檢查
 * 為 {@code userId} 比對，刻意不做租戶擁有權檢查——非漏洞遺漏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<AddressDto.Response> listAddresses() {
        UUID userId = TenantContext.getCurrentUser();
        return addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressDto.Response createAddress(final AddressDto.CreateRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        boolean firstAddress = addressRepository.countByUserId(userId) == 0;

        Address address = Address.builder()
                .userId(userId)
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .postalCode(request.getPostalCode())
                .city(request.getCity())
                .district(request.getDistrict())
                .addressLine(request.getAddressLine())
                .isDefault(firstAddress)
                .build();

        Address saved = addressRepository.save(address);
        log.info("Address created: id={}, userId={}, isDefault={}", saved.getId(), userId, firstAddress);
        return toResponse(saved);
    }

    @Transactional
    public AddressDto.Response updateAddress(final UUID addressId, final AddressDto.UpdateRequest request) {
        Address address = findOwnedAddress(addressId);

        if (request.getRecipientName() != null) {
            address.setRecipientName(request.getRecipientName());
        }
        if (request.getPhone() != null) {
            address.setPhone(request.getPhone());
        }
        if (request.getPostalCode() != null) {
            address.setPostalCode(request.getPostalCode());
        }
        if (request.getCity() != null) {
            address.setCity(request.getCity());
        }
        if (request.getDistrict() != null) {
            address.setDistrict(request.getDistrict());
        }
        if (request.getAddressLine() != null) {
            address.setAddressLine(request.getAddressLine());
        }

        Address saved = addressRepository.save(address);
        log.info("Address updated: id={}", addressId);
        return toResponse(saved);
    }

    @Transactional
    public void deleteAddress(final UUID addressId) {
        Address address = findOwnedAddress(addressId);
        addressRepository.delete(address);
        log.info("Address deleted: id={}", addressId);
    }

    /**
     * 設為預設地址（同使用者其餘地址的 isDefault 一併清除）
     */
    @Transactional
    public AddressDto.Response setDefaultAddress(final UUID addressId) {
        UUID userId = TenantContext.getCurrentUser();
        Address address = findOwnedAddress(addressId);

        List<Address> currentDefaults = addressRepository.findByUserIdAndIsDefaultTrue(userId);
        for (Address current : currentDefaults) {
            if (!current.getId().equals(addressId)) {
                current.setDefault(false);
            }
        }
        addressRepository.saveAll(currentDefaults);

        address.setDefault(true);
        Address saved = addressRepository.save(address);
        log.info("Address set as default: id={}, userId={}", addressId, userId);
        return toResponse(saved);
    }

    private Address findOwnedAddress(final UUID addressId) {
        return getOwnedAddress(addressId, TenantContext.getCurrentUser());
    }

    /**
     * 查詢地址簿內指定地址並驗證擁有權，供其他模組（如 {@code OrderService} 下單時複製收件
     * 資訊）重用，避免各自重複實作同一段擁有權判斷邏輯。地址簿與租戶無關，僅比對 userId。
     */
    public Address getOwnedAddress(final UUID addressId, final UUID userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> {
                    boolean exists = addressRepository.findById(addressId).isPresent();
                    if (exists) {
                        return new BusinessException(ErrorCode.E_8007, "Not authorized to access this address");
                    }
                    return new BusinessException(ErrorCode.E_8006, "Address not found");
                });
    }

    private AddressDto.Response toResponse(final Address address) {
        return AddressDto.Response.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .postalCode(address.getPostalCode())
                .city(address.getCity())
                .district(address.getDistrict())
                .addressLine(address.getAddressLine())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}
