-- AP 28a (R32): a category may have one parent category. Names are unique per level: top-level categories per user,
-- subcategories per parent. Rows with an empty parent_id count as distinct in this index, so the service checks the
-- top level (as it already ignores case). Never change this file once merged.
alter table category add column parent_id bigint;
alter table category add constraint fk_category_parent foreign key (parent_id) references category (id);
create index ix_category_parent on category (parent_id);

alter table category drop constraint uk_category_owner_name;
alter table category add constraint uk_category_owner_parent_name unique (owner_id, parent_id, name);
