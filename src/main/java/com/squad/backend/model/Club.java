package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "clubs")
public class Club {
    
    @Id
    private String id;
    
    private String seasonId;
    private String clubName;
    
    @Version
    @Field("__v")
    private Integer version;
}
