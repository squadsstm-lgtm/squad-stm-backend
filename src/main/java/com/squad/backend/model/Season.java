package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Data
@Document(collection = "seasons")
public class Season {
    
    @Id
    private String id;
    
    private LocalDate startDate;
    private LocalDate endDate;
    private String year;
    private Boolean active;
    
    @Version
    @Field("__v")
    private Integer version;
}
