-- AP 32b (R35, US40, US41): the Ist stage of a user and whether the setup wizard is done. Existing users keep the
-- behaviour they know (stage 4) and see the wizard once with their data. Never change this file once merged.

alter table users add column ist_stage integer default 4 not null;
alter table users add column setup_done boolean default false not null;
alter table users add constraint ck_users_ist_stage check (ist_stage between 1 and 4);
