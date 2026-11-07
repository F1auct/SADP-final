package edu.sadp.checkcollect.identity;

import java.util.Set;
import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName, boolean enabled,
                          boolean mustChangePassword, Set<Role> roles, boolean wechatBound) {}
