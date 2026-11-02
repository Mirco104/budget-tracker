-- User overview (US25, AP 18c): the first admin becomes the system admin, who can never be demoted,
-- and every sign-in is recorded. Never change this file once merged.

alter table users drop constraint ck_users_role;
alter table users alter column role set data type varchar(20);
alter table users add constraint ck_users_role check (role in ('USER', 'ADMIN', 'SYSTEM_ADMIN'));

-- The admin created on the first start is the oldest one
update users set role = 'SYSTEM_ADMIN' where id = (select min(id) from users where role = 'ADMIN');

alter table users add column last_login_at timestamp with time zone;
