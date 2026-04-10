package com.nextkey.ecommerce.core.room;

import com.nextkey.ecommerce.api.dto.RoomDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ListingRepository listingRepository;

    @Transactional(readOnly = true)
    public Page<RoomDto.ListResponse> getRooms(
            Integer maxGuests,
            String location,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        UUID tenantId = TenantContext.getCurrentTenant();
        Page<Room> rooms;

        if (keyword != null && !keyword.isBlank()) {
            rooms = roomRepository.findByListing_TenantIdAndListing_Status(
                    tenantId, Listing.ListingStatus.ACTIVE, pageRequest);
        } else if (maxGuests != null) {
            rooms = roomRepository.findByMinGuests(maxGuests, pageRequest);
        } else if (location != null && !location.isBlank()) {
            rooms = roomRepository.findByLocation(location, pageRequest);
        } else {
            rooms = roomRepository.findByListing_TenantIdAndListing_Status(
                    tenantId, Listing.ListingStatus.ACTIVE, pageRequest);
        }

        return rooms.map(this::toListResponse);
    }

    @Transactional(readOnly = true)
    public RoomDto.Response getRoom(UUID listingId) {
        Room room = findRoomByListingId(listingId);
        return toResponse(room);
    }

    @Transactional
    public RoomDto.Response createRoom(RoomDto.CreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID ownerId = TenantContext.getCurrentUser();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenantId(tenantId)
                .listingType(Listing.ListingType.ROOM)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(request.getBasePrice())
                .currency("TWD")
                .tags(request.getTags())
                .build();

        listing = listingRepository.save(listing);

        // Create Room
        Room room = Room.builder()
                .listing(listing)
                .location(request.getLocation())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .maxGuests(request.getMaxGuests() != null ? request.getMaxGuests() : 2)
                .amenities(request.getAmenities())
                .checkInTime(request.getCheckInTime() != null ? request.getCheckInTime() : LocalTime.of(15, 0))
                .checkOutTime(request.getCheckOutTime() != null ? request.getCheckOutTime() : LocalTime.of(11, 0))
                .roomCount(request.getRoomCount() != null ? request.getRoomCount() : 1)
                .build();

        room = roomRepository.save(room);
        log.info("Created room with listingId: {}", listing.getId());

        return toResponse(room);
    }

    @Transactional
    public RoomDto.Response updateRoom(UUID listingId, RoomDto.UpdateRequest request) {
        Room room = findRoomByListingId(listingId);
        Listing listing = room.getListing();

        if (request.getTitle() != null) {
            listing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            listing.setDescription(request.getDescription());
        }
        if (request.getCoverImageUrl() != null) {
            listing.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.getBasePrice() != null) {
            listing.setBasePrice(request.getBasePrice());
        }
        if (request.getTags() != null) {
            listing.setTags(request.getTags());
        }
        if (request.getStatus() != null) {
            listing.setStatus(Listing.ListingStatus.valueOf(request.getStatus().toUpperCase()));
        }

        listingRepository.save(listing);

        if (request.getLocation() != null) {
            room.setLocation(request.getLocation());
        }
        if (request.getLatitude() != null) {
            room.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            room.setLongitude(request.getLongitude());
        }
        if (request.getMaxGuests() != null) {
            room.setMaxGuests(request.getMaxGuests());
        }
        if (request.getAmenities() != null) {
            room.setAmenities(request.getAmenities());
        }
        if (request.getCheckInTime() != null) {
            room.setCheckInTime(request.getCheckInTime());
        }
        if (request.getCheckOutTime() != null) {
            room.setCheckOutTime(request.getCheckOutTime());
        }
        if (request.getRoomCount() != null) {
            room.setRoomCount(request.getRoomCount());
        }

        room = roomRepository.save(room);
        log.info("Updated room with listingId: {}", listingId);

        return toResponse(room);
    }

    @Transactional
    public void deleteRoom(UUID listingId) {
        Room room = findRoomByListingId(listingId);
        Listing listing = room.getListing();

        // Soft delete: set status to DELETED
        listing.setStatus(Listing.ListingStatus.DELETED);
        listingRepository.save(listing);

        log.info("Deleted room with listingId: {}", listingId);
    }

    private Room findRoomByListingId(UUID listingId) {
        return roomRepository.findByListingId(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000));
    }

    private RoomDto.Response toResponse(Room room) {
        Listing listing = room.getListing();
        return RoomDto.Response.builder()
                .listingId(listing.getId())
                .tenantId(listing.getTenantId())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .location(room.getLocation())
                .latitude(room.getLatitude())
                .longitude(room.getLongitude())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus().name())
                .tags(listing.getTags())
                .maxGuests(room.getMaxGuests())
                .amenities(room.getAmenities())
                .checkInTime(room.getCheckInTime())
                .checkOutTime(room.getCheckOutTime())
                .roomCount(room.getRoomCount())
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }

    private RoomDto.ListResponse toListResponse(Room room) {
        Listing listing = room.getListing();
        return RoomDto.ListResponse.builder()
                .listingId(listing.getId())
                .title(listing.getTitle())
                .location(room.getLocation())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus().name())
                .maxGuests(room.getMaxGuests())
                .roomCount(room.getRoomCount())
                .createdAt(listing.getCreatedAt())
                .build();
    }
}