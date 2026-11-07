CREATE TABLE app_users (
  id UUID PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  role VARCHAR(32) NOT NULL CHECK (role IN ('COLLECTOR', 'REVIEWER', 'FIELD_WORKER', 'ADMIN')),
  PRIMARY KEY (user_id, role)
);

CREATE TABLE wechat_bindings (
  user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  app_id VARCHAR(128) NOT NULL,
  open_id VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (app_id, open_id)
);
