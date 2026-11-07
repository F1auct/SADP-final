package edu.sadp.checkcollect.identity;

import java.util.Set;
import java.util.UUID;

public record UserAccount(UUID id, String username, String displayName, String passwordHash,
                          boolean enabled, boolean mustChangePassword, Set<Role> roles,
                          boolean wechatBound) {
    public UserSummary summary() {
        return new UserSummary(id, username, displayName, enabled, mustChangePassword, roles, wechatBound);
    }
}
