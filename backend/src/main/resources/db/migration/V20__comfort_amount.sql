-- AP 27f4: an optional comfort amount per account; the page Konten marks a balance below it. Never change this file
-- once merged.

alter table bank_accounts add column comfort_amount numeric(12, 2);
