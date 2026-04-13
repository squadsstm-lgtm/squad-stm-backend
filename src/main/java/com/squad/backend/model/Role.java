package com.squad.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "roles")
public class Role {
    
    @Id
    @JsonProperty("_id")
    private String id;
    
    private String clubId;
    private String name;
    private Boolean isActive;
    
    @Version
    @Field("__v")
    private Integer version;
}
