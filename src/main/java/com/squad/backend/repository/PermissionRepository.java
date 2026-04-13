package com.squad.backend.repository;

import com.squad.backend.model.Permission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends MongoRepository<Permission, String> {
    
    List<Permission> findByClubId(String clubId);
    
    Optional<Permission> findByNameAndClubId(String name, String clubId);
}
