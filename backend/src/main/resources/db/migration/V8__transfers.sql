-- Transfers between own accounts (R27, US31, AP 26c). A transfer has no category and counts in no calculation;
-- every other transaction keeps its category. Never change this file once merged.

alter table transactions alter column category_id drop not null;
alter table transactions add column transfer boolean default false not null;
alter table transactions add constraint ck_transactions_transfer_category
    check ((transfer = true and category_id is null) or (transfer = false and category_id is not null));
