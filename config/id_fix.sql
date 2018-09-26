-- we need this to be done to our db first by user 'postgres':  create extension "uuid-ossp";
-- if that didnt work, we may need to install this first:  apt-get install postgresql-contrib-9.5



-- this creates a new column with key-like features, but not *primary* key
--ALTER TABLE "USERS" ADD COLUMN "ID" VARCHAR(36) NOT NULL UNIQUE DEFAULT uuid_generate_v4();

-- oh! turns out we dont need all the constraint-y stuff *IFFFFFFF* we are going to make it PRIMARY KEY (below)
ALTER TABLE "USERS" ADD COLUMN "UUID" VARCHAR(36) DEFAULT uuid_generate_v4();



-- h/t  https://gist.github.com/scaryguy/6269293


-- Firstly, remove PRIMARY KEY attribute of former PRIMARY KEY
ALTER TABLE "USERS" DROP CONSTRAINT "USERS_pkey";

-- Then change column name of  your PRIMARY KEY and PRIMARY KEY candidates properly.  NOTE: we dont need this part.  :P
--ALTER TABLE "USERS" RENAME COLUMN <primary_key_candidate> TO id;

-- Lastly set your new PRIMARY KEY  (this will enforce the not-null & unique contstraint magically!)
ALTER TABLE "USERS" ADD PRIMARY KEY ("UUID");



-- now we need to add unique constraint to USERNAME which was previously that because of primary key (but still is NOT NULL)
ALTER TABLE "USERS" ADD CONSTRAINT users_username_unique UNIQUE ("USERNAME");

-- now drop the not null constraint on USERS tabel
alter table "USERS" alter column "USERNAME" drop not null;

