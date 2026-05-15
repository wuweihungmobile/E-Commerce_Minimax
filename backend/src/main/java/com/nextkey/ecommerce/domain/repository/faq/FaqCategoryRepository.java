package com.nextkey.ecommerce.domain.repository.faq;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.faq.FaqCategory;

@Repository
public interface FaqCategoryRepository extends JpaRepository<FaqCategory, UUID> {

    List<FaqCategory> findAllByOrderBySortOrderAsc();

    Optional<FaqCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);
}