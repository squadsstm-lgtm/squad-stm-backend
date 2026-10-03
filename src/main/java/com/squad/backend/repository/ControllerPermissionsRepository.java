package com.squad.backend.repository;

import com.squad.backend.model.ControllerPermissions;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ControllerPermissionsRepository extends MongoRepository<ControllerPermissions, String> {

    Optional<ControllerPermissions> findByAuthId(String authId);

    List<ControllerPermissions> findByAuthIdIn(Collection<String> authIds);

    boolean existsByAuthId(String authId);

    void deleteByAuthId(String authId);

    long countByManageControllerPermissionsTrue();
}
