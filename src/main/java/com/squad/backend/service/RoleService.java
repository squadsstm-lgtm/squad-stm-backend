package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.role.CreateRoleRequest;
import com.squad.backend.dto.response.role.RoleDeleteResponse;
import com.squad.backend.dto.response.role.RoleResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Permission;
import com.squad.backend.model.Role;
import com.squad.backend.model.User;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.PermissionRepository;
import com.squad.backend.repository.RoleRepository;
import com.squad.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final AuthRepository authRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplateClient;
    private static final Sort DEFAULT_ROLE_PAGE_SORT = Sort.by(Sort.Order.desc("_id"));

    public RoleResponse createRole(CreateRoleRequest request, String clubId) {
        // Create Role
        Role role = new Role();
        mapToModel(request, role);
        role.setClubId(clubId);
        role.setIsActive(true);
        role = roleRepository.save(role);
        
        // Create Permission with same ID as Role
        Permission permission = new Permission();
        permission.setId(role.getId()); // Same ID as Role
        permission.setClubId(clubId);
        permission.setName(request.getName());
        permission.setGroups(buildDefaultPermissionGroups());
        permissionRepository.save(permission);
        
        return mapToResponse(role, clubId);
    }

    public PagedRolesResult getAllRolesPaged(String clubId, String search, int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, pageSize);

        Query query = new Query();
        query.addCriteria(Criteria.where("clubId").is(clubId));
        query.addCriteria(Criteria.where("isActive").is(true));
        query.addCriteria(Criteria.where("name").ne("Admin"));
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch != null) {
            query.addCriteria(Criteria.where("name").regex(normalizedSearch, "i"));
        }

        long totalItems = mongoTemplateClient.count(query, Role.class);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, DEFAULT_ROLE_PAGE_SORT);
        query.with(pageable);

        List<RoleResponse> roles = mongoTemplateClient.find(query, Role.class)
                .stream()
                .map(role -> mapToResponse(role, clubId))
                .collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) totalItems / safeSize);

        return new PagedRolesResult(roles, totalItems, totalPages, safePage, safeSize);
    }

    private void mapToModel(CreateRoleRequest request, Role role) {
        role.setName(request.getName());
    }

    public boolean checkRoleExists(String roleName, String clubId) {
        String normalized = roleName == null ? "" : roleName.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return roleRepository.findByNameIgnoreCaseAndClubId(normalized, clubId)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .isPresent();
    }

    public RoleResponse getRoleById(String id, String clubId) {
        Role role = roleRepository.findById(id)
                .filter(existingRole -> clubId.equals(existingRole.getClubId()))
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.ROLE_NOT_FOUND));
        return mapToResponse(role, clubId);
    }

    public RoleResponse updateRole(String id, String clubId, CreateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .filter(r -> clubId.equals(r.getClubId()))
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.ROLE_NOT_FOUND));

        role.setName(request.getName());
        role = roleRepository.save(role);

        Permission permission = permissionRepository.findById(id)
                .filter(p -> clubId.equals(p.getClubId()))
                .orElse(null);
        if (permission != null) {
            permission.setName(request.getName());
            permissionRepository.save(permission);
        }

        List<User> users = userRepository.findByClubId(clubId)
                .stream()
                .filter(u -> id.equals(u.getRoleId()))
                .collect(Collectors.toList());
        for (User user : users) {
            user.setRole(request.getName());
            userRepository.save(user);
        }

        List<Auth> auths = authRepository.findByClubIdAndRoleId(clubId, id);
        for (Auth auth : auths) {
            auth.setRole(request.getName());
            authRepository.save(auth);
        }

        return mapToResponse(role, clubId);
    }

    public RoleDeleteResponse deleteRole(String id, String clubId) {
        Role role = roleRepository.findById(id)
                .filter(r -> clubId.equals(r.getClubId()))
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.ROLE_NOT_FOUND));

        // Check both User and Auth for roleId references (more accurate than role name)
        List<User> users = userRepository.findByClubIdAndRoleId(clubId, id);
        List<Auth> auths = authRepository.findByClubIdAndRoleId(clubId, id);

        if (!users.isEmpty() || !auths.isEmpty()) {
            // Users or Auth records are assigned to this role, cannot delete
            return new RoleDeleteResponse(false);
        } else {
            // Soft delete: set isActive = false (keep data for future use)
            role.setIsActive(false);
            roleRepository.save(role);
            // Permission document is kept as well (not deleted)
            return new RoleDeleteResponse(true);
        }
    }

    private RoleResponse mapToResponse(Role role, String clubId) {
        List<Auth> users = authRepository.findByClubIdAndRoleId(clubId, role.getId()).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsBlocked()))
                .collect(Collectors.toList());
        return RoleResponse.builder()
                .id(role.getId())
                .clubId(role.getClubId())
                .name(role.getName())
                .isActive(role.getIsActive())
                .users(List.copyOf(users))
                .build();
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        if (normalized.isEmpty() || "undefined".equalsIgnoreCase(normalized) || normalized.length() < 2) {
            return null;
        }
        return normalized;
    }

    public record PagedRolesResult(
            List<RoleResponse> roles,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize
    ) {}

    /**
     * Builds default permission groups for new roles.
     * Only "Get Session", "Get Player", "Get Team", "Get Finance" are set to true by default.
     * All other permissions are set to false.
     */
    private List<Permission.PermissionGroup> buildDefaultPermissionGroups() {
        List<String> defaultEnabledPermissions = Arrays.asList(
            "Get Session", "Get Player", "Get Team", "Get Finance"
        );
        
        List<Permission.PermissionGroup> groups = new ArrayList<>();
        
        // User Management permissions (group id: 1, permission groupId: 1)
        Permission.PermissionGroup userManagement = new Permission.PermissionGroup();
        userManagement.setId(1);
        userManagement.setGroup("User Management");
        List<Permission.PermissionItem> userPerms = new ArrayList<>();
        userPerms.add(createPermissionItem(1, 1, "Invite User", false));
        userPerms.add(createPermissionItem(1, 2, "Edit User", false));
        userPerms.add(createPermissionItem(1, 3, "Delete User", false));
        userPerms.add(createPermissionItem(1, 4, "Get User", defaultEnabledPermissions.contains("Get User")));
        userManagement.setPermissions(userPerms);
        groups.add(userManagement);
        
        // Player Management permissions (group id: 2, permission groupId: 3)
        Permission.PermissionGroup playerManagement = new Permission.PermissionGroup();
        playerManagement.setId(2);
        playerManagement.setGroup("Player Management");
        List<Permission.PermissionItem> playerPerms = new ArrayList<>();
        playerPerms.add(createPermissionItem(3, 1, "Add Player", false));
        playerPerms.add(createPermissionItem(3, 2, "Edit Player", false));
        playerPerms.add(createPermissionItem(3, 3, "Delete Player", false));
        playerPerms.add(createPermissionItem(3, 4, "Get Player", defaultEnabledPermissions.contains("Get Player")));
        playerPerms.add(createPermissionItem(3, 5, "Invite Player", false));
        playerManagement.setPermissions(playerPerms);
        groups.add(playerManagement);
        
        // Team Management permissions (group id: 3, permission groupId: 2)
        Permission.PermissionGroup teamManagement = new Permission.PermissionGroup();
        teamManagement.setId(3);
        teamManagement.setGroup("Team Management");
        List<Permission.PermissionItem> teamPerms = new ArrayList<>();
        teamPerms.add(createPermissionItem(2, 1, "Add Team", false));
        teamPerms.add(createPermissionItem(2, 2, "Edit Team", false));
        teamPerms.add(createPermissionItem(2, 3, "Delete Team", false));
        teamPerms.add(createPermissionItem(2, 4, "Get Team", defaultEnabledPermissions.contains("Get Team")));
        teamManagement.setPermissions(teamPerms);
        groups.add(teamManagement);
        
        // Session Management permissions (group id: 4, permission groupId: 4)
        Permission.PermissionGroup sessionManagement = new Permission.PermissionGroup();
        sessionManagement.setId(4);
        sessionManagement.setGroup("Session Management");
        List<Permission.PermissionItem> sessionPerms = new ArrayList<>();
        sessionPerms.add(createPermissionItem(4, 1, "Add Session", false));
        sessionPerms.add(createPermissionItem(4, 2, "Edit Session", false));
        sessionPerms.add(createPermissionItem(4, 3, "Delete Session", false));
        sessionPerms.add(createPermissionItem(4, 4, "Get Session", defaultEnabledPermissions.contains("Get Session")));
        sessionPerms.add(createPermissionItem(4, 5, "Mark Player's Attendance", false));
        sessionManagement.setPermissions(sessionPerms);
        groups.add(sessionManagement);
        
        // Finance Management permissions (group id: 5, permission groupId: 5)
        Permission.PermissionGroup financeManagement = new Permission.PermissionGroup();
        financeManagement.setId(5);
        financeManagement.setGroup("Finance Management");
        List<Permission.PermissionItem> financePerms = new ArrayList<>();
        financePerms.add(createPermissionItem(5, 2, "Edit Finance", false));
        financePerms.add(createPermissionItem(5, 4, "Get Finance", defaultEnabledPermissions.contains("Get Finance")));
        financeManagement.setPermissions(financePerms);
        groups.add(financeManagement);

        // Club Wallet permissions (group id: 6, permission groupId: 6)
        Permission.PermissionGroup clubWallet = new Permission.PermissionGroup();
        clubWallet.setId(6);
        clubWallet.setGroup("Club Wallet");
        List<Permission.PermissionItem> clubWalletPerms = new ArrayList<>();
        clubWalletPerms.add(createPermissionItem(6, 1, "Access Club Wallet", false));
        clubWallet.setPermissions(clubWalletPerms);
        groups.add(clubWallet);

        return groups;
    }

    private Permission.PermissionItem createPermissionItem(Integer groupId, Integer roleId, String description, Boolean status) {
        Permission.PermissionItem item = new Permission.PermissionItem();
        item.setGroupId(groupId);
        item.setRoleId(roleId);
        item.setDescription(description);
        item.setStatus(status);
        return item;
    }
}
