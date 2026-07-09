package com.nextkey.ecommerce.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.AddressDto;
import com.nextkey.ecommerce.domain.model.user.Address;
import com.nextkey.ecommerce.domain.repository.AddressRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * AddressService 單元測試（Sprint 87，PRD §14.3.1 Phase 2-B 收貨地址簿）。
 *
 * <p>地址簿與租戶無關，僅比對 userId 擁有權（買家可能向多個不同租戶下單）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService 單元測試（Sprint 87）")
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    private AddressService addressService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        addressService = new AddressService(addressRepository);
        TenantContext.setCurrentUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Address addressOf(final UUID userId, final boolean isDefault) {
        return Address.builder()
                .id(ADDRESS_ID)
                .userId(userId)
                .recipientName("王小明")
                .phone("0912345678")
                .city("台北市")
                .district("中正區")
                .addressLine("忠孝東路一段1號")
                .isDefault(isDefault)
                .build();
    }

    @Test
    @DisplayName("createAddress：第一筆地址自動設為預設")
    void createAddress_firstAddress_autoDefault() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressDto.CreateRequest request = AddressDto.CreateRequest.builder()
                .recipientName("王小明").phone("0912345678").city("台北市")
                .district("中正區").addressLine("忠孝東路一段1號").build();

        AddressDto.Response response = addressService.createAddress(request);

        assertThat(response.isDefault()).isTrue();
    }

    @Test
    @DisplayName("createAddress：非第一筆地址不自動設為預設")
    void createAddress_notFirstAddress_notDefault() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressDto.CreateRequest request = AddressDto.CreateRequest.builder()
                .recipientName("王小明").phone("0912345678").city("台北市")
                .addressLine("忠孝東路一段1號").build();

        AddressDto.Response response = addressService.createAddress(request);

        assertThat(response.isDefault()).isFalse();
    }

    @Test
    @DisplayName("listAddresses：僅回傳自己的地址")
    void listAddresses_returnsOwnAddresses() {
        when(addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(USER_ID))
                .thenReturn(List.of(addressOf(USER_ID, true)));

        List<AddressDto.Response> result = addressService.listAddresses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(ADDRESS_ID);
    }

    @Test
    @DisplayName("updateAddress：本人地址可更新")
    void updateAddress_ownAddress_succeeds() {
        Address address = addressOf(USER_ID, false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressDto.UpdateRequest request = AddressDto.UpdateRequest.builder()
                .recipientName("王大明").build();

        AddressDto.Response response = addressService.updateAddress(ADDRESS_ID, request);

        assertThat(response.getRecipientName()).isEqualTo("王大明");
    }

    @Test
    @DisplayName("updateAddress：他人地址存在但非本人擁有應拋出 E_8007")
    void updateAddress_othersAddress_throwsE8007() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(addressOf(OTHER_USER_ID, false)));

        AddressDto.UpdateRequest request = AddressDto.UpdateRequest.builder().recipientName("測試").build();

        assertThatThrownBy(() -> addressService.updateAddress(ADDRESS_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8007));
    }

    @Test
    @DisplayName("updateAddress：地址不存在應拋出 E_8006")
    void updateAddress_addressNotFound_throwsE8006() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        AddressDto.UpdateRequest request = AddressDto.UpdateRequest.builder().recipientName("測試").build();

        assertThatThrownBy(() -> addressService.updateAddress(ADDRESS_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8006));
    }

    @Test
    @DisplayName("deleteAddress：本人地址可刪除")
    void deleteAddress_ownAddress_succeeds() {
        Address address = addressOf(USER_ID, false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(address));

        addressService.deleteAddress(ADDRESS_ID);

        verify(addressRepository).delete(address);
    }

    @Test
    @DisplayName("deleteAddress：他人地址應拒絕且不刪除")
    void deleteAddress_othersAddress_rejectedWithoutDelete() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(addressOf(OTHER_USER_ID, false)));

        assertThatThrownBy(() -> addressService.deleteAddress(ADDRESS_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8007));
        verify(addressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("setDefaultAddress：設定新預設會清除同使用者其餘地址的預設狀態")
    void setDefaultAddress_clearsOtherDefaults() {
        Address target = addressOf(USER_ID, false);
        UUID previousDefaultId = UUID.randomUUID();
        Address previousDefault = Address.builder()
                .id(previousDefaultId).userId(USER_ID).recipientName("舊預設")
                .phone("0900000000").city("台中市").addressLine("某路").isDefault(true).build();

        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(target));
        when(addressRepository.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(List.of(previousDefault));
        when(addressRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressDto.Response response = addressService.setDefaultAddress(ADDRESS_ID);

        assertThat(response.isDefault()).isTrue();
        assertThat(previousDefault.isDefault()).isFalse();

        ArgumentCaptor<List<Address>> savedAllCaptor = ArgumentCaptor.forClass(List.class);
        verify(addressRepository).saveAll(savedAllCaptor.capture());
        assertThat(savedAllCaptor.getValue()).contains(previousDefault);
    }

    @Test
    @DisplayName("getOwnedAddress：供其他模組（如 OrderService）重用查詢邏輯")
    void getOwnedAddress_reusableByOtherModules() {
        Address address = addressOf(USER_ID, false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(address));

        Address result = addressService.getOwnedAddress(ADDRESS_ID, USER_ID);

        assertThat(result).isEqualTo(address);
    }
}
