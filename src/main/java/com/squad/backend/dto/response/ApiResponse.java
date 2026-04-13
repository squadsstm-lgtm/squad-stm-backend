package com.squad.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    private T data;
    private String message;
    private Boolean success;
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null, true);
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(data, message, true);
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, false);
    }
    
    public static <T> ApiResponse<T> error(T data, String message) {
        return new ApiResponse<>(data, message, false);
    }
}
