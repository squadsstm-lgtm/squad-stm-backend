package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Document(collection = "clubs")
public class Club {
    
    @Id
    private String id;
    
    private String seasonId;
    private String clubName;

    /**
     * Squad's fee for this club, in pounds. Null means it has never been saved.
     * Zero means this club has no fee. It does not mean "use the default".
     * Invoices do not read this yet.
     */
    private Double platformFee;
    /** True when this club should move whenever the default fee changes. */
    private Boolean platformFeeFollowsDefault;
    private Instant platformFeeUpdatedAt;
    private String platformFeeUpdatedBy;
    
    @Version
    @Field("__v")
    private Integer version;
}
