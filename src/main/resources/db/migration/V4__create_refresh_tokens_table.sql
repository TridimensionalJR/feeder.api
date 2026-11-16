-- Refresh tokens issued after a successful sign-in.
--
-- A refresh token is a one-time credential: when it is used to obtain a new
-- access token the row is immediately revoked and a fresh token is issued in its
-- place (rotation). This prevents replay attacks: a stolen token can only be
-- used once, and the legitimate session keeps a new one after every refresh.
--
-- Tokens are looked up by their value, so it is indexed. The column is sized at
-- 64 characters to match the hex-encoded 32-byte random value the application
-- generates; a check constraint enforces that floor so the index stays selective.

create table refresh_tokens
(
    id         UUID                     NOT NULL,
    -- The opaque, random token value sent to the client.
    token      VARCHAR(64)              NOT NULL,
    user_id    UUID                     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_refresh_tokens_token_length CHECK (char_length(token) = 64)
)
