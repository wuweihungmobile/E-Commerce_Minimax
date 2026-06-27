-- M10 IM: conversations 表
-- Sprint 23 US-001
CREATE TABLE conversations (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    listing_id             UUID         REFERENCES listings(id) ON DELETE SET NULL,
    order_id               UUID         REFERENCES orders(id) ON DELETE SET NULL,
    conversation_type      VARCHAR(30)  NOT NULL DEFAULT 'DIRECT',
    initiator_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_message_id        UUID,
    last_message_preview   TEXT,
    last_message_at        TIMESTAMP WITH TIME ZONE,
    initiator_unread_count INTEGER      NOT NULL DEFAULT 0,
    recipient_unread_count INTEGER      NOT NULL DEFAULT 0,
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata               JSONB,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_conversations PRIMARY KEY (id)
);

CREATE INDEX idx_conversations_initiator_id ON conversations(initiator_id);
CREATE INDEX idx_conversations_recipient_id ON conversations(recipient_id);
CREATE INDEX idx_conversations_last_message_at ON conversations(last_message_at DESC);
