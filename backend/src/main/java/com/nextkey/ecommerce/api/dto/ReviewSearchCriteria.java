package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 評價搜尋條件 DTO
 *
 * Sprint 18 US-003: 多維度評價搜尋與篩選
 *
 * 支援條件：
 * - 關鍵字搜尋 (標題/內容)
 * - 評分範圍 (minRating/maxRating)
 * - 日期範圍 (startDate/endDate)
 * - 是否有圖片
 * - 是否有商家回覆
 * - 排序選項
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSearchCriteria {

    /** 必要條件：商品/房型 ID */
    private UUID listingId;

    /** 關鍵字（搜尋標題或內容，不區分大小寫） */
    private String keyword;

    /** 最低評分 (1-5) */
    private Integer minRating;

    /** 最高評分 (1-5) */
    private Integer maxRating;

    /** 建立時間起始 */
    private Instant startDate;

    /** 建立時間結束 */
    private Instant endDate;

    /** 是否有圖片（true=只回傳有圖片的） */
    private Boolean hasImages;

    /** 是否有商家回覆（true=只回傳有回覆的） */
    private Boolean hasReply;

    /** 排序欄位：createdAt / rating / helpfulCount */
    private SortBy sortBy;

    /** 排序方向：ASC / DESC */
    private SortDir sortDir;

    /** 分頁 */
    private int page;
    private int size;

    /**
     * 排序欄位
     */
    public enum SortBy {
        CREATED_AT("createdAt"),
        RATING("rating"),
        HELPFUL_COUNT("helpfulCount");

        private final String field;

        SortBy(String field) {
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    /**
     * 排序方向
     */
    public enum SortDir {
        ASC, DESC
    }
}
