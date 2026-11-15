-- 27f4 (decision 60): an expense that fulfils the term of a rare item is paid from its reserve unless the user took it out
alter table transactions add column no_reserve boolean default false not null;
