-- Ticket #145 (decision 101): a saving goal may lie on an account, which has to cover it like its reserves (P8)
alter table saving_goals add column account_id bigint;
alter table saving_goals
    add constraint fk_saving_goals_account foreign key (account_id) references bank_accounts (id);
