-- AP 32h (decision 90, R35): automatic entries (P6) only from this day on; set when the user leaves stage 1 and
-- chooses "ab heute" or "rückwirkend ab". Empty means from "Gilt ab" of each item as before. Never change this file
-- once merged.

alter table users add column auto_entries_from date;
