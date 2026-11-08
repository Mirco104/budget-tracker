-- Undoing an import (US34, code review 08.11.2026): a stored transaction that an import made the counterpart of a
-- transfer (R27) remembers that import and its category, so undoing the import gives the category back; a series
-- that an import created (R31) remembers the import, so undoing the import deletes it.
-- Never change this file once merged.

alter table transactions add column transfer_batch_id bigint;
alter table transactions add column category_before_transfer_id bigint;
alter table transactions
    add constraint fk_transactions_transfer_batch foreign key (transfer_batch_id) references import_batches (id);
alter table transactions
    add constraint fk_transactions_category_before_transfer
        foreign key (category_before_transfer_id) references category (id);
create index ix_transactions_transfer_batch on transactions (transfer_batch_id);

alter table recurring_transactions add column import_batch_id bigint;
alter table recurring_transactions
    add constraint fk_recurring_transactions_import_batch foreign key (import_batch_id) references import_batches (id);
create index ix_recurring_transactions_import_batch on recurring_transactions (import_batch_id);
