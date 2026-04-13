package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "counts")
public class Count {
    
    @Id
    private String id;
    
    private String seasonId;
    private String clubId;
    private Integer playerCount;
    private Integer teamCount;
    private Integer sessionCount;
    
    @Version
    @Field("__v")
    private Integer version;
}
