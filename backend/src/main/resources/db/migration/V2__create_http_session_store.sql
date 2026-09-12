CREATE TABLE http_sessions (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(255),
    CONSTRAINT pk_http_sessions PRIMARY KEY (primary_id),
    CONSTRAINT uq_http_sessions_session_id UNIQUE (session_id),
    CONSTRAINT ck_http_sessions_max_inactive_interval
        CHECK (max_inactive_interval > 0)
);

CREATE INDEX ix_http_sessions_expiry_time
    ON http_sessions (expiry_time);

CREATE INDEX ix_http_sessions_principal_name
    ON http_sessions (principal_name)
    WHERE principal_name IS NOT NULL;

CREATE TABLE http_sessions_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT pk_http_sessions_attributes
        PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT fk_http_sessions_attributes_session
        FOREIGN KEY (session_primary_id)
        REFERENCES http_sessions (primary_id)
        ON DELETE CASCADE
);
