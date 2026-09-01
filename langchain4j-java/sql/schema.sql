-- =====================================================================
-- 企业 AI 知识问答库 — 表结构初始化脚本（schema.sql）
-- 用途：创建平台所需的 11 张新表 + vector_store 两个 metadata 表达式索引
-- 执行方式（本机 PG 跑在 Docker 容器 local-postgres 中）：
--   docker exec -i local-postgres psql -U postgres -d xufg_db -f /dev/stdin < sql/schema.sql
-- 说明：全部使用 IF NOT EXISTS，可重复执行（幂等）
-- =====================================================================

-- ============ 1. 用户-角色-权限（RBAC 三层模型） ============

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT ck_sys_user_status CHECK (status IN (0, 1))
);

-- 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL,
    role_name   VARCHAR(50)  NOT NULL,
    description VARCHAR(200),
    built_in    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

-- 系统权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGSERIAL PRIMARY KEY,
    perm_code   VARCHAR(100) NOT NULL,
    perm_name   VARCHAR(50)  NOT NULL,
    perm_group  VARCHAR(50),
    description VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_sys_permission_code UNIQUE (perm_code)
);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    perm_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, perm_id)
);

-- ============ 2. 知识库与成员协作 ============

-- 知识库表
CREATE TABLE IF NOT EXISTS kb_base (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(500),
    owner_user_id      BIGINT       NOT NULL REFERENCES sys_user(id),
    embedding_model    VARCHAR(100),
    embedding_dimension INT,
    status             SMALLINT     DEFAULT 1,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP,
    CONSTRAINT uk_kb_base_owner_name UNIQUE (owner_user_id, name),
    CONSTRAINT ck_kb_base_status CHECK (status IN (0, 1))
);

-- 知识库成员表（库内角色：OWNER/EDITOR/VIEWER）
CREATE TABLE IF NOT EXISTS kb_member (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT      NOT NULL REFERENCES kb_base(id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    member_role VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_member_kb_user UNIQUE (kb_id, user_id),
    CONSTRAINT ck_kb_member_role CHECK (member_role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

-- 部分唯一索引：无论并发事务来自哪个入口，每个知识库最多只能有一个 OWNER。
-- PostgreSQL 支持部分唯一索引；该语句可重复执行。
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_member_owner
    ON kb_member (kb_id) WHERE member_role = 'OWNER';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id            BIGSERIAL PRIMARY KEY,
    kb_id         BIGINT      NOT NULL REFERENCES kb_base(id) ON DELETE CASCADE,
    doc_name      VARCHAR(255) NOT NULL,
    file_type     VARCHAR(10),
    file_size     BIGINT,
    file_path     VARCHAR(500),
    splitter_type VARCHAR(30)  DEFAULT 'recursive',
    chunk_size    INT          DEFAULT 200,
    chunk_overlap INT          DEFAULT 50,
    segment_count INT,
    status        SMALLINT     NOT NULL DEFAULT 0,
    error_msg     VARCHAR(1000),
    upload_user_id BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uk_kb_document_kb_name UNIQUE (kb_id, doc_name),
    CONSTRAINT ck_kb_document_status CHECK (status IN (0, 1, 2))
);

-- ============ 3. 问答会话 / 历史 / 反馈 ============

-- 问答会话表（id 即 LangChain4j 的 memoryId，UUID 字符串）
CREATE TABLE IF NOT EXISTS qa_session (
    id             VARCHAR(36) PRIMARY KEY,
    kb_id          BIGINT   NOT NULL REFERENCES kb_base(id) ON DELETE CASCADE,
    user_id        BIGINT   NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    title          VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP
);

-- 问答历史表（sources 为引用来源 JSONB 数组）
CREATE TABLE IF NOT EXISTS qa_history (
    id         BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    kb_id      BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    question   TEXT        NOT NULL,
    answer     TEXT,
    sources    JSONB,
    model      VARCHAR(100),
    latency_ms BIGINT,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 问答反馈表（评分：1 赞 / -1 踩 / 0 取消）
CREATE TABLE IF NOT EXISTS qa_feedback (
    id         BIGSERIAL PRIMARY KEY,
    history_id BIGINT      NOT NULL REFERENCES qa_history(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    rating     SMALLINT    NOT NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_feedback_history_user UNIQUE (history_id, user_id),
    CONSTRAINT ck_qa_feedback_rating CHECK (rating IN (-1, 0, 1))
);

-- ============ 4. 常规索引 ============

-- 用户/角色/权限关联表外键索引
CREATE INDEX IF NOT EXISTS idx_sys_user_role_uid  ON sys_user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_rid  ON sys_user_role (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_perm_rid  ON sys_role_permission (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_perm_pid  ON sys_role_permission (perm_id);

-- 知识库相关索引
CREATE INDEX IF NOT EXISTS idx_kb_base_owner      ON kb_base (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_kb_member_kb       ON kb_member (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_member_uid      ON kb_member (user_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_kb_status ON kb_document (kb_id, status);

-- 问答相关索引
CREATE INDEX IF NOT EXISTS idx_qa_session_uid     ON qa_session (user_id);
CREATE INDEX IF NOT EXISTS idx_qa_session_kb      ON qa_session (kb_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_session ON qa_history (session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_qa_history_kb      ON qa_history (kb_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_uid     ON qa_history (user_id);
CREATE INDEX IF NOT EXISTS idx_qa_feedback_hid    ON qa_feedback (history_id);

-- ============ 5. 存量库约束升级（可重复执行） ============
-- kb_member 的 OWNER 唯一性由上面的部分唯一索引保证。
-- qa_history 的 kb_id/session_id 在存量库中可能缺少外键；下方 DO 块只在约束不存在时添加。
-- 添加 CASCADE 后，删除知识库或会话会级联删除问答历史，这是数据保留策略的预期行为。
-- 若存量数据存在孤儿历史，必须先清理孤儿行，否则外键创建会失败。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_qa_history_kb'
          AND conrelid = 'qa_history'::regclass
    ) THEN
        ALTER TABLE qa_history
            ADD CONSTRAINT fk_qa_history_kb
            FOREIGN KEY (kb_id) REFERENCES kb_base(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_qa_history_session'
          AND conrelid = 'qa_history'::regclass
    ) THEN
        ALTER TABLE qa_history
            ADD CONSTRAINT fk_qa_history_session
            FOREIGN KEY (session_id) REFERENCES qa_session(id) ON DELETE CASCADE;
    END IF;
END
$$;

-- ============ 6. vector_store metadata 表达式索引 ============
-- 向量隔离依赖 metadata 中的 kbId/docId（PgVectorFilterMapper 翻译为 metadata->>'kbId'）

CREATE INDEX IF NOT EXISTS idx_vector_store_kbid  ON vector_store (((metadata->>'kbId')));
CREATE INDEX IF NOT EXISTS idx_vector_store_docid ON vector_store (((metadata->>'docId')));

-- ============ 7. 表与字段中文注释 ============

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.username IS '登录用户名（唯一）';
COMMENT ON COLUMN sys_user.password IS 'BCrypt 加密密码';
COMMENT ON COLUMN sys_user.nickname IS '用户昵称';
COMMENT ON COLUMN sys_user.status IS '状态：1 启用 / 0 禁用';

COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON COLUMN sys_role.role_code IS '角色编码（唯一），如 ADMIN/USER';
COMMENT ON COLUMN sys_role.built_in IS '是否内置角色：内置角色不可删除';

COMMENT ON TABLE sys_permission IS '系统权限表';
COMMENT ON COLUMN sys_permission.perm_code IS '权限码（唯一），如 kb:create';
COMMENT ON COLUMN sys_permission.perm_group IS '权限分组（前端权限树用）';

COMMENT ON TABLE sys_user_role IS '用户-角色关联表';
COMMENT ON TABLE sys_role_permission IS '角色-权限关联表';

COMMENT ON TABLE kb_base IS '知识库表';
COMMENT ON COLUMN kb_base.owner_user_id IS '库主用户 ID';
COMMENT ON COLUMN kb_base.embedding_model IS '向量模型名（预留）';
COMMENT ON COLUMN kb_base.embedding_dimension IS '向量维度（预留）';
COMMENT ON COLUMN kb_base.status IS '状态：1 启用 / 0 停用';

COMMENT ON TABLE kb_member IS '知识库成员表';
COMMENT ON COLUMN kb_member.member_role IS '库内角色：OWNER 库主 / EDITOR 编辑者 / VIEWER 只读';

COMMENT ON TABLE kb_document IS '知识库文档表';
COMMENT ON COLUMN kb_document.file_path IS '文件落盘路径（UUID 重命名）';
COMMENT ON COLUMN kb_document.segment_count IS '实际分段数';
COMMENT ON COLUMN kb_document.status IS '处理状态：0 处理中 / 1 成功 / 2 失败';
COMMENT ON COLUMN kb_document.error_msg IS '解析失败原因';

COMMENT ON TABLE qa_session IS '问答会话表（id 即 LangChain4j memoryId）';
COMMENT ON TABLE qa_history IS '问答历史表';
COMMENT ON COLUMN qa_history.sources IS '引用来源 JSONB：[{seq,docId,docName,snippet,score}]';
COMMENT ON COLUMN qa_history.latency_ms IS '回答耗时（毫秒）';

COMMENT ON TABLE qa_feedback IS '问答反馈表';
COMMENT ON COLUMN qa_feedback.rating IS '评分：1 点赞 / -1 点踩 / 0 取消';
