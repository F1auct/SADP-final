package edu.sadp.checkcollect.identity;

import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapUsers implements ApplicationRunner {
    private final IdentityRepository users;
    private final PasswordEncoder passwords;
    private final String adminUser;
    private final String adminPassword;
    private final boolean seedDemo;
    private final String demoPassword;

    public BootstrapUsers(IdentityRepository users, PasswordEncoder passwords,
                          @Value("${app.bootstrap.admin-user:${INITIAL_ADMIN_USERNAME:}}") String adminUser,
                          @Value("${app.bootstrap.admin-password:${INITIAL_ADMIN_PASSWORD:}}") String adminPassword,
                          @Value("${APP_SEED_DEMO:false}") boolean seedDemo,
                          @Value("${DEMO_PASSWORD:}") String demoPassword) {
        this.users = users;
        this.passwords = passwords;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.seedDemo = seedDemo;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.list().isEmpty()) {
            if (adminUser.isBlank() || adminPassword.length() < 10) {
                throw new IllegalStateException("首次启动需提供 INITIAL_ADMIN_USERNAME 和至少10位的 INITIAL_ADMIN_PASSWORD");
            }
            users.create(UUID.randomUUID(), adminUser, "管理员", passwords.encode(adminPassword), Set.of(Role.ADMIN));
        }
        if (seedDemo) {
            if (demoPassword.length() < 10) throw new IllegalStateException("DEMO_PASSWORD 至少需要10位");
            demo("member-a", "成员 A", Set.of(Role.ADMIN, Role.COLLECTOR));
            demo("member-b", "成员 B", Set.of(Role.COLLECTOR));
            demo("member-c", "成员 C", Set.of(Role.REVIEWER));
            demo("member-d", "成员 D", Set.of(Role.FIELD_WORKER));
        }
    }

    private void demo(String username, String name, Set<Role> roles) {
        if (users.findByUsername(username).isEmpty()) {
            users.create(UUID.randomUUID(), username, name, passwords.encode(demoPassword), roles);
        }
    }
}
