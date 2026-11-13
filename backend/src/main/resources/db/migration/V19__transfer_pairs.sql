-- AP 27f3: a transfer booked with source and target account consists of two bookings that know each other; amount,
-- description and deletion apply to both, the date per side. Never change this file once merged.

alter table transactions add column transfer_peer_id bigint;
alter table transactions
    add constraint fk_transactions_transfer_peer foreign key (transfer_peer_id) references transactions (id);
