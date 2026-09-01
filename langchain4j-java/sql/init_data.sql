-- =====================================================================
-- 企业 AI 知识问答库 — 内置数据初始化脚本（init_data.sql）
-- 用途：初始化内置角色（ADMIN/USER）、4 个权限码、角色-权限绑定、初始 admin 账号
-- 幂等说明：全部使用 ON CONFLICT DO NOTHING / WHERE NOT EXISTS，
--           依赖列上的 UNIQUE 约束去重，重复执行不报错、不产生重复数据
-- 执行方式（本机 PG 跑在 Docker 容器 local-postgres 中）：
--   docker exec -i local-postgres psql -U postgres -d xufg_db -f /dev/stdin < sql/init_data.sql
-- 依赖：需先执行 schema.sql
-- 【安全提示】初始 admin 密码为 Admin@123456，仅供本地初始化，
--             生产环境部署后必须立即修改！
-- =====================================================================

-- ============ 1. 内置角色 ============

-- 超级管理员角色（内置，不可删除）
INSERT INTO sys_role (role_code, role_name, description, built_in)
VALUES ('ADMIN', '超级管理员', '拥有全部权限，含用户管理与角色权限管理', TRUE)
ON CONFLICT (role_code) DO NOTHING;

-- 普通用户角色（内置，不可删除）
INSERT INTO sys_role (role_code, role_name, description, built_in)
VALUES ('USER', '普通用户', '拥有知识库创建、文档管理、问答等基础权限', TRUE)
ON CONFLICT (role_code) DO NOTHING;

-- ============ 2. 权限码 ============

-- 创建知识库
INSERT INTO sys_permission (perm_code, perm_name, perm_group, description)
VALUES ('kb:create', '创建知识库', 'knowledge-base', '允许创建新的知识库')
ON CONFLICT (perm_code) DO NOTHING;

-- 管理任意知识库（管理员用，可改/删任意库）
INSERT INTO sys_permission (perm_code, perm_name, perm_group, description)
VALUES ('kb:manage', '管理任意知识库', 'knowledge-base', '可编辑/删除任意知识库（管理员用）')
ON CONFLICT (perm_code) DO NOTHING;

-- 用户管理
INSERT INTO sys_permission (perm_code, perm_name, perm_group, description)
VALUES ('sys:user:manage', '用户管理', 'system', '用户列表、启用/禁用、分配角色')
ON CONFLICT (perm_code) DO NOTHING;

-- 角色权限管理
INSERT INTO sys_permission (perm_code, perm_name, perm_group, description)
VALUES ('sys:role:manage', '角色权限管理', 'system', '角色 CRUD 与权限分配')
ON CONFLICT (perm_code) DO NOTHING;

-- ============ 3. 角色-权限绑定 ============

-- ADMIN 拥有全部 4 个权限
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, perm_id) DO NOTHING;

-- USER 仅拥有创建知识库权限
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'kb:create'
WHERE r.role_code = 'USER'
ON CONFLICT (role_id, perm_id) DO NOTHING;

-- ============ 4. 初始 admin 账号 ============

-- 【安全提示】以下密文对应初始密码 Admin@123456（BCrypt 加密），
-- 仅供本地初始化使用，生产环境部署后必须立即修改！
INSERT INTO sys_user (username, password, nickname, status)
VALUES ('admin', '$2a$10$MdKoFOPs27jtSufekVrbiOODIE1fGBgXnvKF70PBkawp0Cz.RroSi', '系统管理员', 1)
ON CONFLICT (username) DO NOTHING;

-- admin 绑定 ADMIN 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ADMIN'
WHERE u.username = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;
