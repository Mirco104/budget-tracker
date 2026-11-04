-- Category icons (US28, AP 19d): a key from a fixed list, default "tag". Existing categories get the icon of
-- their default name (spec section 4). Never change this file once merged.

alter table category add column icon varchar(20) default 'tag' not null;

update category set icon = case name
    when 'Wohnen' then 'home'
    when 'Nebenkosten' then 'bolt'
    when 'Versicherungen' then 'shield'
    when 'Mobilität' then 'car'
    when 'Kredite' then 'card'
    when 'Spenden' then 'heart'
    when 'Lebensmittel' then 'cart'
    when 'Freizeit' then 'music'
    when 'Urlaub' then 'suitcase'
    when 'Persönlicher Bedarf' then 'bag'
    when 'Geschenke' then 'gift'
    when 'Gesundheit' then 'pulse'
    when 'Investitionen' then 'trend'
    when 'Selbstständigkeit' then 'briefcase'
    when 'Gehalt/Lohn (netto)' then 'wallet'
    when 'Nebenjob' then 'laptop'
    when 'Einnahmen Selbstständigkeit' then 'receipt'
    when 'Kapitalerträge' then 'coins'
    when 'Staatliche Leistungen' then 'landmark'
    when 'Sonstige Einnahmen' then 'plus'
    else 'tag'
end;
