package com.squad.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@Document(collection = "permissions")
public class Permission {
    
    @Id
    @JsonProperty("_id")
    private String id;
    
    private String clubId;
    private String name;
    private List<PermissionGroup> groups;
    
    @Version
    @Field("__v")
    private Integer version;
    
    @Data
    public static class PermissionGroup {
        private Integer id;
        private String group;
        private List<PermissionItem> permissions;
    }
    
    @Data
    public static class PermissionItem {
        private Integer groupId;
        private Integer roleId;
        private String description;
        private Boolean status;
    }
}
