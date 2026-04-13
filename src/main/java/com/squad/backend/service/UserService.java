package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.user.CreateUserRequest;
import com.squad.backend.dto.request.user.InviteUserRequest;
import com.squad.backend.dto.response.ClubResponse;
import com.squad.backend.dto.response.user.UserResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.Role;
import com.squad.backend.model.User;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.RoleRepository;
import com.squad.backend.repository.UserRepository;
import com.squad.backend.security.JwtTokenProvider;
import com.squad.backend.security.TenantScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private static final Sort DEFAULT_USER_PAGE_SORT = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("_id")
    );

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final ClubRepository clubRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    public User createUser(CreateUserRequest request, Auth auth) {
        User existingUser = userRepository.findByEmailAndClubId(request.getEmail(), auth.getClubId()).orElse(null);

        if (existingUser != null && existingUser.getFirstName() != null && existingUser.getLastName() != null) {
            throw new IllegalArgumentException("This user has already completed their profile. Please use Edit instead of Add.");
        }

        User user = new User();
        mapToModel(request, user);
        user.setClubId(auth.getClubId());
        user.setSeasonId(auth.getSeasonId());
        user.setIsBlocked(false);
        user.setCreatedBy(auth.getId());
        user.setUpdatedBy(auth.getId());
        return userRepository.save(user);
    }

    public PagedUsersResult getAllUsersPaged(String clubId, String excludeEmail, String search, int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, pageSize);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, DEFAULT_USER_PAGE_SORT);

        String safeExcludeEmail = excludeEmail != null ? excludeEmail : "";
        Criteria baseCriteria = new Criteria().andOperator(
                Criteria.where("clubId").is(clubId),
                Criteria.where("firstName").ne(""),
                Criteria.where("lastName").ne(""),
                Criteria.where("email").ne(safeExcludeEmail),
                Criteria.where("isBlocked").ne(true)
        );
        String searchTerm = search != null ? search.trim() : "";
        Criteria finalCriteria = baseCriteria;
        if (searchTerm.length() >= 2) {
            String pattern = ".*" + java.util.regex.Pattern.quote(searchTerm) + ".*";
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("role").regex(pattern, "i"),
                    Criteria.where("userName").regex(pattern, "i")
            );
            finalCriteria = new Criteria().andOperator(baseCriteria, searchCriteria);
        }

        long totalItems = mongoTemplate.count(new Query(finalCriteria), User.class);
        Query query = new Query(finalCriteria).with(pageable);
        List<User> users = mongoTemplate.find(query, User.class);
        List<UserResponse> rows = users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) totalItems / safeSize);

        return new PagedUsersResult(rows, totalItems, totalPages, safePage, safeSize);
    }

    public record PagedUsersResult(
            List<UserResponse> users,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize) {
    }

    public UserResponse getUserById(String id, Auth auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, user.getClubId(), user.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND);
        }
        return mapToResponse(user);
    }

    public Auth updateUser(String id, CreateUserRequest request, Auth auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, user.getClubId(), user.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND);
        }

        String hashedPassword = null;
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            hashedPassword = passwordEncoder.encode(request.getPassword());
        }

        Role roleItem = roleRepository.findByNameIgnoreCaseAndClubId(request.getRole(), request.getClubId())
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        String roleId = roleItem.getId();

        if ("Secretary".equals(request.getRole()) || "Treasurer".equals(request.getRole())) {
            List<User> existing = userRepository.findByClubId(request.getClubId())
                    .stream()
                    .filter(u -> (u.getFirstName() != null && !u.getFirstName().isEmpty()) &&
                            (u.getLastName() != null && !u.getLastName().isEmpty()) &&
                            request.getRole().equals(u.getRole()) &&
                            !u.getId().equals(id))
                    .collect(Collectors.toList());
            if (!existing.isEmpty()) {
                throw new IllegalArgumentException(request.getRole() + " is already added for this club.");
            }
        }

        String updatedBy = request.getLoginUser() != null ? request.getLoginUser() : auth.getId();
        
        mapToModel(request, user);
        user.setRoleId(roleId);
        user.setUpdatedBy(updatedBy);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));

        Optional<Auth> existingAuth = authRepository.findByEmailAndClubId(request.getEmail().toLowerCase(), request.getClubId());
        Auth authResult;
        if (existingAuth.isPresent()) {
            Auth authUser = existingAuth.get();
            authUser.setFirstName(request.getFirstName());
            authUser.setLastName(request.getLastName());
            authUser.setUserName(request.getUsername());
            authUser.setEmail(request.getEmail().toLowerCase());
            authUser.setPhone(request.getPhone());
            authUser.setRoleId(roleId);
            authUser.setRole(request.getRole());
            if (hashedPassword != null) {
                authUser.setPassword(hashedPassword);
            }
            authResult = authRepository.save(authUser);
        } else {
            Auth newAuth = new Auth();
            newAuth.setClubId(request.getClubId());
            newAuth.setFirstName(request.getFirstName());
            newAuth.setLastName(request.getLastName());
            newAuth.setEmail(request.getEmail().toLowerCase());
            newAuth.setUserName(request.getUsername());
            newAuth.setPhone(request.getPhone());
            newAuth.setClubName(club.getClubName());
            newAuth.setPassword(hashedPassword != null ? hashedPassword : "");
            newAuth.setIsVerified(false);
            newAuth.setRoleId(roleId);
            newAuth.setRole(request.getRole());
            newAuth.setUserId(id);
            newAuth.setSeasonId(request.getSeasonId());
            newAuth.setIsBlocked(false);
            authResult = authRepository.save(newAuth);

            Map<String, String> templateData = new HashMap<>();
            templateData.put("emailTitle", "Squad STM - Email Verification");
            templateData.put("emailHeading", "Email Verification Required");
            templateData.put("emailMessage", "<strong>" + newAuth.getFirstName() + "</strong>,<br><br>Please verify your email address to complete your registration.");
            templateData.put("buttonText", "Verify Email");
            templateData.put("buttonLink", backendUrl + "/auth/verifyEmail/" + authResult.getId());
            templateData.put("buttonColor", "#28a745");
            templateData.put("additionalInfo", "Click the button above to verify your email address.");
            templateData.put("footerMessage", "If you believe this email was sent in error, please contact support.");

            emailService.sendEmail(
                    request.getEmail(),
                    "Squad STM - Email Verification",
                    "Please verify your email address.",
                    templateData
            );
        }

        return authResult;
    }

    public Auth updateUserWithToken(String id, String token, CreateUserRequest request) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new SecurityException("Token is invalid");
        }
        String tokenSubject = jwtTokenProvider.getUserIdFromToken(token);
        if (!id.equals(tokenSubject)) {
            throw new SecurityException("Token is invalid");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND);
        }

        String hashedPassword = null;
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            hashedPassword = passwordEncoder.encode(request.getPassword());
        }

        Role roleItem = roleRepository.findByNameIgnoreCaseAndClubId(request.getRole(), request.getClubId())
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        String roleId = roleItem.getId();

        mapToModel(request, user);
        user.setRoleId(roleId);
        user.setUpdatedBy(id);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));

        Optional<Auth> existingAuth = authRepository.findByEmailAndClubId(request.getEmail().toLowerCase(), request.getClubId());
        Auth authResult;
        if (existingAuth.isPresent()) {
            Auth authUser = existingAuth.get();
            authUser.setFirstName(request.getFirstName());
            authUser.setLastName(request.getLastName());
            authUser.setUserName(request.getUsername());
            authUser.setEmail(request.getEmail().toLowerCase());
            authUser.setPhone(request.getPhone());
            authUser.setRoleId(roleId);
            authUser.setRole(request.getRole());
            if (hashedPassword != null) {
                authUser.setPassword(hashedPassword);
            }
            authResult = authRepository.save(authUser);
        } else {
            Auth newAuth = new Auth();
            newAuth.setClubId(request.getClubId());
            newAuth.setFirstName(request.getFirstName());
            newAuth.setLastName(request.getLastName());
            newAuth.setEmail(request.getEmail().toLowerCase());
            newAuth.setUserName(request.getUsername());
            newAuth.setPhone(request.getPhone());
            newAuth.setClubName(club.getClubName());
            newAuth.setPassword(hashedPassword != null ? hashedPassword : "");
            newAuth.setIsVerified(false);
            newAuth.setRoleId(roleId);
            newAuth.setRole(request.getRole());
            newAuth.setUserId(id);
            newAuth.setSeasonId(request.getSeasonId());
            newAuth.setIsBlocked(false);
            authResult = authRepository.save(newAuth);
        }

        return authResult;
    }

    private void mapToModel(CreateUserRequest request, User user) {
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUserName(request.getUsername());
        user.setEmail(request.getEmail().toLowerCase());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        if (request.getClubId() != null) {
            user.setClubId(request.getClubId());
        }
    }

    public boolean checkEmailAvailability(String email, String clubId, String userId) {
        Optional<Auth> existing = authRepository.findByEmailAndClubId(email, clubId);
        // When editing a user, exclude their Auth record by User id (frontend sends User _id)
        if (userId != null && existing.isPresent() && userId.equals(existing.get().getUserId())) {
            return true;
        }
        return existing.isEmpty();
    }

    public boolean checkPhoneAvailability(String phone, String clubId, String userId) {
        List<Auth> auths = authRepository.findByClubId(clubId);
        Optional<Auth> existing = auths.stream()
                .filter(a -> phone.equals(a.getPhone()))
                .findFirst();
        // When editing a user, exclude their Auth record by User id (frontend sends User _id)
        if (userId != null && existing.isPresent() && userId.equals(existing.get().getUserId())) {
            return true;
        }
        return existing.isEmpty();
    }

    public User inviteUser(InviteUserRequest request, Auth auth, String seasonId) {
        Optional<Auth> authExists = authRepository.findByEmailAndClubId(request.getEmail(), auth.getClubId());
        if (authExists.isPresent()) {
            throw new IllegalArgumentException("User with this email \"" + request.getEmail() + "\" already exists.");
        }

        if ("Secretary".equals(request.getRole()) || "Treasurer".equals(request.getRole())) {
            List<User> existing = userRepository.findByClubId(auth.getClubId())
                    .stream()
                    .filter(u -> (u.getFirstName() != null && !u.getFirstName().isEmpty()) &&
                            (u.getLastName() != null && !u.getLastName().isEmpty()) &&
                            request.getRole().equals(u.getRole()))
                    .collect(Collectors.toList());
            if (!existing.isEmpty()) {
                throw new IllegalArgumentException(request.getRole() + " is already added for this club.");
            }
        }

        Optional<User> userExists = userRepository.findByEmailAndClubId(request.getEmail(), auth.getClubId());

        User user;
        if (userExists.isPresent()) {
            user = userExists.get();
            user.setRole(request.getRole());
            if (request.getPhone() != null && !request.getPhone().isEmpty()) {
                user.setPhone(request.getPhone());
            }
            userRepository.save(user);
        } else {
            user = new User();
            user.setClubId(auth.getClubId());
            user.setSeasonId(seasonId);
            user.setFirstName("");
            user.setLastName("");
            user.setUserName("");
            user.setEmail(request.getEmail().toLowerCase());
            user.setRole(request.getRole());
            user.setPhone(request.getPhone() != null ? request.getPhone() : "");
            user.setIsBlocked(false);
            user.setCreatedBy(auth.getId());
            user.setUpdatedBy("");
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            user = userRepository.save(user);
        }

        String token = jwtTokenProvider.generateToken(user.getId());
        String inviteLink = frontendUrl + "#/add/user-details/" + user.getId() + "/" + token;

        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM Invitation");
        templateData.put("emailHeading", "User Invitation");
        templateData.put("emailMessage", "<strong>" + (auth.getFirstName() != null ? auth.getFirstName() : "Squad STM") + "</strong> has invited you to join Squad STM as <strong>" + request.getRole() + "</strong>. Please click the button below to provide your details.");
        templateData.put("buttonText", "User Invitation Request");
        templateData.put("buttonLink", inviteLink);
        templateData.put("buttonColor", "#007bff");
        templateData.put("additionalInfo", "Please ensure to provide accurate information as it will be used for system access and communication purposes.");
        templateData.put("footerMessage", "If you believe this invitation was sent in error, please ignore this email.");

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            boolean mailSent = emailService.sendEmail(
                    request.getEmail(),
                    "Squad STM Invitation",
                    (auth.getFirstName() != null ? auth.getFirstName() : "Squad STM") + " has invited you to join Squad STM as " + request.getRole() + ". Please complete your profile information.",
                    templateData
            );
            if (!mailSent) {
                throw new RuntimeException("Mail sending error.");
            }
        }

        return user;
    }

    public List<ClubResponse> getAllClubs() {
        return clubRepository.findAll().stream()
                .map(this::mapClubToResponse)
                .collect(Collectors.toList());
    }

    private ClubResponse mapClubToResponse(Club club) {
        return ClubResponse.builder()
                .id(club.getId())
                .seasonId(club.getSeasonId())
                .clubName(club.getClubName())
                .build();
    }

    public Auth changeUserStatus(String id, Boolean isBlocked, Auth auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (TenantScope.denyClubScopedEntity(auth, user.getClubId(), user.getSeasonId())) {
            throw new IllegalArgumentException("User not found");
        }
        user.setIsBlocked(isBlocked);
        user.setUpdatedBy(auth.getId());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Auth authUser = authRepository.findByUserId(id)
                .orElse(null);
        if (authUser != null) {
            authUser.setIsBlocked(isBlocked);
            authUser = authRepository.save(authUser);
        }

        return authUser;
    }

    public void deleteUser(String id, Auth auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (TenantScope.denyClubScopedEntity(auth, user.getClubId(), user.getSeasonId())) {
            throw new IllegalArgumentException("User not found");
        }
        userRepository.delete(user);
        authRepository.findByUserId(id).ifPresent(authRepository::delete);
    }

    public UserResponse getUserByIdWithToken(String id, String token) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND);
        }

        if (token != null && !token.isEmpty()) {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("Token is invalid");
            }
        }

        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .seasonId(user.getSeasonId())
                .clubId(user.getClubId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .userName(user.getUserName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleId(user.getRoleId())
                .role(user.getRole())
                .isBlocked(user.getIsBlocked())
                .build();
    }
}
