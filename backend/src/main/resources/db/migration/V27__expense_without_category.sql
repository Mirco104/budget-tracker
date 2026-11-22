-- AP 32f (R37): a booking that is no transfer may be without a category (an expense or income without category); a
-- transfer still has none. Existing rows stay valid. Never change this file once merged.

alter table transactions drop constraint ck_transactions_transfer_category;
alter table transactions add constraint ck_transactions_transfer_category
    check (transfer = false or category_id is null);
