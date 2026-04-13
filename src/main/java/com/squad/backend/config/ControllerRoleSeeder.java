package com.squad.backend.config;

import com.squad.backend.model.Role;
import com.squad.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ControllerRoleSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (roleRepository.findByNameIgnoreCaseAndClubIdIsNull("Controller").isEmpty()) {
            Role role = new Role();
            role.setClubId(null);
            role.setName("Controller");
            role.setIsActive(true);
            roleRepository.save(role);
            log.info("Master Panel: Controller role created.");
        }
    }
}
