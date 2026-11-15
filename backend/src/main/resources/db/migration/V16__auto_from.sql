-- P6 (AP 27c): automatic entries start at the day an item was created, so a new item does not book past terms.
-- Items converted by V14 start today; their past terms are bookings already. Never change this file once merged.

alter table planned_items add column auto_from date;
update planned_items set auto_from = current_date;
alter table planned_items alter column auto_from set not null;
