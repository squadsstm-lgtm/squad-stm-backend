package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.permission.PermissionItemToggleRequest;
import com.squad.backend.dto.response.permission.PermissionGroupDto;
import com.squad.backend.dto.response.permission.PermissionItemDto;
import com.squad.backend.dto.response.permission.PermissionResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Permission;
import com.squad.backend.model.Role;
import com.squad.backend.repository.PermissionRepository;
import com.squad.backend.repository.RoleRepository;
import com.squad.backend.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public PermissionResponse getPermissionByUser(Auth auth) {
        if (Boolean.TRUE.equals(auth.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_BLOCKED);
        }
        if (auth.getRoleId() == null || auth.getRoleId().isEmpty()) {
            throw new IllegalArgumentException("User roleId is not set");
        }

        String clubId = auth.getClubId() != null ? auth.getClubId() : "";
        if ("Controller".equalsIgnoreCase(auth.getRole()) && clubId.isEmpty()) {
            return PermissionResponse.builder()
                    .id(auth.getRoleId())
                    .clubId(null)
                    .name("Controller")
                    .groups(new ArrayList<>())
                    .build();
        }

        Role role = roleRepository.findById(auth.getRoleId()).orElse(null);
        if (role == null || !Boolean.TRUE.equals(role.getIsActive()) || role.getClubId() == null || !role.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Permission not found for roleId: " + auth.getRoleId() + " and clubId: " + clubId);
        }

        Permission permission = permissionRepository.findById(auth.getRoleId())
                .filter(p -> p.getClubId() != null && p.getClubId().equals(clubId))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found for roleId: " + auth.getRoleId() + " and clubId: " + clubId));

        return buildResponse(permission);
    }

    public PermissionResponse getPermissionById(String id, Auth auth) {
        if (Boolean.TRUE.equals(auth.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_BLOCKED);
        }
        Role role = requireRoleForPermissionAccess(auth, id);

        Permission permission = permissionRepository.findById(id)
                .filter(p -> p.getClubId() != null && p.getClubId().equals(role.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));

        return buildResponse(permission);
    }

    public PermissionResponse updatePermission(String id, List<PermissionItemToggleRequest> items, Auth auth) {
        if (Boolean.TRUE.equals(auth.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_BLOCKED);
        }
        Role role = requireRoleForPermissionAccess(auth, id);

        Permission permission = permissionRepository.findById(id)
                .filter(p -> p.getClubId() != null && p.getClubId().equals(role.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));

        List<PermissionItemToggleRequest> patchList  = items != null ? items : List.of();
        for (PermissionItemToggleRequest patch : patchList) {
            if (patch.getGroupId() == null || patch.getRoleId() == null || patch.getStatus() == null) {
                continue;
            }
            for (Permission.PermissionGroup group : nullSafe(permission.getGroups())) {
                for (Permission.PermissionItem item : nullSafe(group.getPermissions())) {
                    if (Objects.equals(item.getGroupId(), patch.getGroupId())
                            && Objects.equals(item.getRoleId(), patch.getRoleId())) {
                        item.setStatus(patch.getStatus());
                    }
                }
            }
        }

        permission = permissionRepository.save(permission);
        return buildResponse(permission);
    }

    private Role requireRoleForPermissionAccess(Auth auth, String rolePermissionId) {
        Role role = roleRepository.findById(rolePermissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
        if (!Boolean.TRUE.equals(role.getIsActive())) {
            throw new IllegalArgumentException("Permission not found");
        }
        if (TenantScope.isControllerWithoutClub(auth)) {
            return role;
        }
        if (TenantScope.denyClubOnly(auth, role.getClubId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        return role;
    }

    private PermissionResponse buildResponse(Permission permission) {
        List<Permission.PermissionGroup> groups = ensureClubWalletGroup(permission.getGroups(), permission.getName());
        return PermissionResponse.builder()
                .id(permission.getId())
                .clubId(permission.getClubId())
                .name(permission.getName())
                .groups(mapGroupDtos(groups))
                .build();
    }

    private List<PermissionGroupDto> mapGroupDtos(List<Permission.PermissionGroup> groups) {
        if (groups == null) {
            return new ArrayList<>();
        }
        List<PermissionGroupDto> out = new ArrayList<>();
        for (Permission.PermissionGroup g : groups) {
            if (g == null) {
                continue;
            }
            out.add(PermissionGroupDto.builder()
                    .id(g.getId())
                    .group(g.getGroup())
                    .permissions(mapItemDtos(g.getPermissions()))
                    .build());
        }
        return out;
    }

    private List<PermissionItemDto> mapItemDtos(List<Permission.PermissionItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        List<PermissionItemDto> out = new ArrayList<>();
        for (Permission.PermissionItem it : items) {
            if (it == null) {
                continue;
            }
            out.add(PermissionItemDto.builder()
                    .groupId(it.getGroupId())
                    .roleId(it.getRoleId())
                    .description(it.getDescription())
                    .status(it.getStatus())
                    .build());
        }
        return out;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    /** Ensures "Club Wallet" group exists in the list (for existing permissions created before Club Wallet was added). */
    private List<Permission.PermissionGroup> ensureClubWalletGroup(List<Permission.PermissionGroup> groups, String roleName) {
        List<Permission.PermissionGroup> result = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
        boolean hasClubWallet = result.stream()
                .anyMatch(g -> g != null && "Club Wallet".equals(g.getGroup()));
        if (!hasClubWallet) {
            Permission.PermissionGroup clubWallet = new Permission.PermissionGroup();
            clubWallet.setId(6);
            clubWallet.setGroup("Club Wallet");
            Permission.PermissionItem item = new Permission.PermissionItem();
            item.setGroupId(6);
            item.setRoleId(1);
            item.setDescription("Access Club Wallet");
            item.setStatus("Admin".equals(roleName) || "Treasurer".equals(roleName));
            clubWallet.setPermissions(List.of(item));
            result.add(clubWallet);
        }
        return result;
    }
}
