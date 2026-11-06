-- Decision 36 (spec R3): a target is only an amount in euro; percent is an input helper in the frontend.
-- An entry that had only a percent value keeps its month with an empty amount, which means "no target from
-- this month". Percent targets are not converted, because that needs the average income of each month.
alter table category_target drop column target_percent;
