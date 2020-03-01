update "TAXONOMY" set "NONSPECIFIC" = 'true' where (SELECT count(*) FROM regexp_matches("SCIENTIFICNAME", ' ', 'g')) > 2;
update "TAXONOMY" set "NONSPECIFIC" = 'true' where (SELECT count(*) FROM regexp_matches("SCIENTIFICNAME", ',', 'g')) > 0;
update "TAXONOMY" set "NONSPECIFIC" = 'true' where (SELECT count(*) FROM regexp_matches("SCIENTIFICNAME", '"', 'g')) > 0;
update "TAXONOMY" set "NONSPECIFIC" = 'true' where (SELECT count(*) FROM regexp_matches("SCIENTIFICNAME", '\?', 'g')) > 0;
update "TAXONOMY" set "NONSPECIFIC" = 'true' where (SELECT count(*) FROM regexp_matches("SCIENTIFICNAME", '\(', 'g')) > 0;
update "TAXONOMY" set "NONSPECIFIC" = 'true' where (SELECT count(*) FROM regexp_matches("SCIENTIFICNAME", '\)', 'g')) > 0;
