package com.squad.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PageMetaResponse {
    private int page;
    private int limit;
    private int pageSize;
    private long total;
    private int pages;
}
