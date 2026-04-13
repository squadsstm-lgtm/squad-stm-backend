package com.squad.backend.repository;

import com.squad.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    List<User> findByClubId(String clubId);
    
    List<User> findByClubIdAndSeasonId(String clubId, String seasonId);
    
    List<User> findByClubIdAndRoleId(String clubId, String roleId);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByEmailAndClubId(String email, String clubId);
    
    boolean existsByEmail(String email);
    
    // Find users with non-empty firstName and lastName, excluding specific email (for getAllUsers)
    @Query("{ 'clubId': ?0, 'firstName': { $ne: '' }, 'lastName': { $ne: '' }, 'email': { $ne: ?1 } }")
    List<User> findByClubIdAndFirstNameNotEmptyAndLastNameNotEmptyAndEmailNot(String clubId, String excludeEmail);

    @Query("{ 'clubId': ?0, 'firstName': { $ne: '' }, 'lastName': { $ne: '' }, 'email': { $ne: ?1 } }")
    Page<User> findByClubIdAndFirstNameNotEmptyAndLastNameNotEmptyAndEmailNot(String clubId, String excludeEmail, Pageable pageable);
}
