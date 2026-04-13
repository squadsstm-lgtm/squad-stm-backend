package com.squad.backend.repository;

import com.squad.backend.model.Role;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends MongoRepository<Role, String> {
    
    List<Role> findByClubId(String clubId);
    
    List<Role> findByClubIdAndIsActive(String clubId, Boolean isActive);
    
    Optional<Role> findByNameAndClubId(String name, String clubId);
    
    Optional<Role> findByNameIgnoreCaseAndClubId(String name, String clubId);
    
    /** Platform-level role (Master Panel Controller) has no club. */
    Optional<Role> findByNameIgnoreCaseAndClubIdIsNull(String name);
}
