
CREATE FUNCTION update_version() RETURNS "trigger" AS $$
BEGIN
	NEW."VERSION" := extract(epoch from now()) * 1000;
	RETURN NEW;
END;$$
LANGUAGE plpgsql;


CREATE TRIGGER occurrence_create_trigger
    BEFORE INSERT ON "OCCURRENCE"
    FOR EACH ROW
    EXECUTE PROCEDURE update_version();

CREATE TRIGGER occurrence_update_trigger
    BEFORE UPDATE ON "OCCURRENCE"
    FOR EACH ROW
    EXECUTE PROCEDURE update_version();


