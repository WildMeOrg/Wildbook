alter table "OCCURRENCE" add column "VERSION" bigint not null default (extract(epoch from now()) * 1000);
update "OCCURRENCE" set "VERSION" = "MILLIS" where "MILLIS" is not null;
