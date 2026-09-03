package com.nextkey.ecommerce.domain.repository.returns;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.returns.ReturnRequestItem;

/**
 * 退貨品項 Repository（Sprint 118，DEF-044）。
 */
@Repository
public interface ReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, UUID> {

    List<ReturnRequestItem> findByReturnRequestId(UUID returnRequestId);
}
