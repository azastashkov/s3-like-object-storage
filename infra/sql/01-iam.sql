CREATE TABLE principals (
  principal_id  VARCHAR(128) PRIMARY KEY,
  display_name  VARCHAR(255),
  api_key_hash  CHAR(64)     NOT NULL UNIQUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE bucket_acls (
  bucket_name   VARCHAR(63)  NOT NULL,
  principal_id  VARCHAR(128) NOT NULL REFERENCES principals(principal_id) ON DELETE CASCADE,
  permission    VARCHAR(16)  NOT NULL CHECK (permission IN ('READ','WRITE','OWNER')),
  PRIMARY KEY (bucket_name, principal_id, permission)
);

INSERT INTO principals (principal_id, display_name, api_key_hash)
VALUES
  ('root',     'Root user',
   encode(sha256('root-secret-key'::bytea), 'hex')),
  ('loadtest', 'Load test user',
   encode(sha256('loadtest-secret-key'::bytea), 'hex'));
