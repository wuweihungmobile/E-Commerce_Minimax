package com.nextkey.ecommerce.api.controller;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.RoomCalendarService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 房源日曆維護狀態 API（PRD §5.5.3）
 */
@Slf4j
@RestController
@RequestMapping("/v2/dashboard/rooms/{roomListingId}/maintenance")
@RequiredArgsConstructor
public class RoomCalendarController {

    private final RoomCalendarService roomCalendarService;

    @PostMapping
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<Void>> markMaintenance(
            @PathVariable UUID roomListingId,
            @Valid @RequestBody BookingDto.MaintenanceRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.E_4003, "End date must be after start date");
        }
        log.info("Mark maintenance: roomListingId={}, startDate={}, endDate={}",
                roomListingId, request.getStartDate(), request.getEndDate());
        roomCalendarService.markMaintenance(roomListingId, request.getStartDate(), request.getEndDate());
        return ResponseEntity.ok(ApiResponse.success("Dates marked as under maintenance", null));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<Void>> unmarkMaintenance(
            @PathVariable UUID roomListingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.E_4003, "End date must be after start date");
        }
        log.info("Unmark maintenance: roomListingId={}, startDate={}, endDate={}", roomListingId, startDate, endDate);
        roomCalendarService.unmarkMaintenance(roomListingId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Maintenance cleared", null));
    }
}
