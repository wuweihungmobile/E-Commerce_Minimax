-- M10 IM: messages 表
-- Sprint 23 US-001
CREATE TABLE messages (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    conversation_id UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type    VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    content         TEXT,
    attachments     JSONB,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMP WITH TIME ZONE,
    read_by         UUID         REFERENCES users(id) ON DELETE SET NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_messages PRIMARY KEY (id)
);

CREATE INDEX idx_messages_conversation_id ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
