
-- if any one part of this fails the whole transaction will stop, so data must be in good order to get thru the whole thing


BEGIN;

	-- ensure email addresses on users are all unique
	CREATE UNIQUE INDEX user_unique_email ON "USERS"(LOWER("EMAILADDRESS"));



END;
