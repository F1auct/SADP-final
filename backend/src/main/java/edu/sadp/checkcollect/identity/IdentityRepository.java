package edu.sadp.checkcollect.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private final JdbcTemplate jdbc;

    public IdentityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private UserAccount mapUser(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Set<Role> roles = jdbc.queryForList("SELECT role FROM user_roles WHERE user_id = ?", String.class, id)
                .stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
        Boolean bound = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM wechat_bindings WHERE user_id = ?)", Boolean.class, id);
        return new UserAccount(id, rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getBoolean("must_change_password"),
                roles, Boolean.TRUE.equals(bound));
    }

    public Optional<UserAccount> find(UUID id) {
        return jdbc.query("SELECT * FROM app_users WHERE id = ?", this::mapUser, id).stream().findFirst();
    }

    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.query("SELECT * FROM app_users WHERE username = ?", this::mapUser, username).stream().findFirst();
    }

    public Optional<UserAccount> findByWechat(String appId, String openId) {
        return jdbc.query("SELECT u.* FROM app_users u JOIN wechat_bindings b ON b.user_id = u.id WHERE b.app_id = ? AND b.open_id = ?",
                this::mapUser, appId, openId).stream().findFirst();
    }

    public List<UserAccount> list() {
        return jdbc.query("SELECT * FROM app_users ORDER BY username", this::mapUser);
    }

    public List<UserAccount> listByRole(Role role) {
        return jdbc.query("SELECT u.* FROM app_users u JOIN user_roles r ON r.user_id = u.id WHERE u.enabled AND r.role = ? ORDER BY u.username",
                this::mapUser, role.name());
    }

    public long countEnabledAdmins() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM app_users u JOIN user_roles r ON r.user_id = u.id WHERE u.enabled AND r.role = 'ADMIN'", Long.class);
        return count == null ? 0 : count;
    }

    public void lockAdminUpdates() {
        jdbc.execute("SELECT pg_advisory_xact_lock(57534)");
    }

    public void create(UUID id, String username, String name, String hash, Set<Role> roles) {
        jdbc.update("INSERT INTO app_users(id, username, display_name, password_hash) VALUES (?, ?, ?, ?)", id, username, name, hash);
        saveRoles(id, roles);
    }

    public void update(UUID id, String name, boolean enabled, Set<Role> roles) {
        jdbc.update("UPDATE app_users SET display_name = ?, enabled = ? WHERE id = ?", name, enabled, id);
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", id);
        saveRoles(id, roles);
    }

    private void saveRoles(UUID id, Set<Role> roles) {
        for (Role role : roles) jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", id, role.name());
    }

    public void changePassword(UUID id, String hash) {
        jdbc.update("UPDATE app_users SET password_hash = ?, must_change_password = false WHERE id = ?", hash, id);
    }

    public void bindWechat(UUID id, String appId, String openId) {
        jdbc.update("INSERT INTO wechat_bindings(user_id, app_id, open_id) VALUES (?, ?, ?)", id, appId, openId);
    }
}
