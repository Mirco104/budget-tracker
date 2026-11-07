-- "Ist ersetzt Plan" (R28, US35, AP 26g): an exception of a series can point to the single transaction that replaces
-- the occurrence; deleting the transaction brings the occurrence back. A transaction replaces at most one occurrence.
-- Never change this file once merged.

alter table recurring_exceptions add column transaction_id bigint;
alter table recurring_exceptions
    add constraint fk_recurring_exceptions_transaction foreign key (transaction_id) references transactions (id);
alter table recurring_exceptions add constraint uk_recurring_exceptions_transaction unique (transaction_id);
