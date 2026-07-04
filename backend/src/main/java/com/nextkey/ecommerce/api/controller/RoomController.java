package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.RoomDto;
import com.nextkey.ecommerce.core.room.RoomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RestController
@RequestMapping("/v2/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @PreAuthorize("hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<Page<RoomDto.ListResponse>>> getRooms(
            @RequestParam(required = false) Integer maxGuests,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Page<RoomDto.ListResponse> rooms = roomService.getRooms(
                maxGuests, location, keyword, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @GetMapping("/{listingId}")
    @PreAuthorize("hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<RoomDto.Response>> getRoom(
            @PathVariable UUID listingId) {
        RoomDto.Response room = roomService.getRoom(listingId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('room:create')")
    public ResponseEntity<ApiResponse<RoomDto.Response>> createRoom(
            @Valid @RequestBody RoomDto.CreateRequest request) {
        RoomDto.Response room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created successfully", room));
    }

    @PutMapping("/{listingId}")
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<RoomDto.Response>> updateRoom(
            @PathVariable UUID listingId,
            @Valid @RequestBody RoomDto.UpdateRequest request) {
        RoomDto.Response room = roomService.updateRoom(listingId, request);
        return ResponseEntity.ok(ApiResponse.success("Room updated successfully", room));
    }

    @DeleteMapping("/{listingId}")
    @PreAuthorize("hasAuthority('room:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable UUID listingId) {
        roomService.deleteRoom(listingId);
        return ResponseEntity.ok(ApiResponse.success("Room deleted successfully", null));
    }

    /**
     * 清除開放窗（Sprint 57 AI-2202f）：將 openUntilDate/bookingWindowDays 清回 null（無限制）。
     * `updateRoom` 沿用「非 null 才更新」慣例無法清除，故另立專屬端點（比照 CartController.clearCart 模式）。
     */
    @DeleteMapping("/{listingId}/open-window")
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<RoomDto.Response>> clearOpenWindow(
            @PathVariable UUID listingId) {
        RoomDto.Response room = roomService.clearOpenWindow(listingId);
        return ResponseEntity.ok(ApiResponse.success("Open window cleared", room));
    }
}