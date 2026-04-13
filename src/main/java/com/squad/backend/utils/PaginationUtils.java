package com.squad.backend.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static PageParams parse(String pageNumber, String pageSize) {
        Integer page = RequestParamUtils.parseInteger(pageNumber);
        Integer size = RequestParamUtils.parseInteger(pageSize);
        return new PageParams(page, size);
    }

    public static Map<String, Object> buildMeta(long totalItems, int totalPages, int currentPage, int pageSize) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("totalItems", totalItems);
        meta.put("totalPages", totalPages);
        meta.put("currentPage", currentPage);
        meta.put("pageSize", pageSize);
        return meta;
    }

    public static <T> Map<String, Object> buildPagedBody(
            String itemsKey,
            List<T> items,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize) {
        Map<String, Object> body = new HashMap<>();
        body.put(itemsKey, items);
        body.put("meta", buildMeta(totalItems, totalPages, currentPage, pageSize));
        return body;
    }

    public static <T> Map<String, Object> buildListBody(String itemsKey, List<T> items) {
        Map<String, Object> body = new HashMap<>();
        body.put(itemsKey, items);
        return body;
    }

    public record PageParams(Integer page, Integer size) {
        public boolean hasPagination() {
            return page != null && size != null;
        }
    }
}
