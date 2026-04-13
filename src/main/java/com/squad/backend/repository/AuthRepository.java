package com.squad.backend.repository;

import com.squad.backend.model.Auth;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthRepository extends MongoRepository<Auth, String> {
    
    Optional<Auth> findByEmail(String email);
    
    Optional<Auth> findByEmailAndClubId(String email, String clubId);
    
    Optional<Auth> findByPhone(String phone);
    
    List<Auth> findByClubId(String clubId);
    
    List<Auth> findByClubIdAndRoleId(String clubId, String roleId);
    
    Optional<Auth> findByUserNameIgnoreCase(String userName);
    
    Optional<Auth> findByUserId(String userId);

    Optional<Auth> findByForgotMpinToken(String forgotMpinToken);

    boolean existsByEmail(String email);
    
    boolean existsByPhone(String phone);
    
    /** Check if any Auth has the given role (e.g. "Controller"). */
    boolean existsByRole(String role);
}
