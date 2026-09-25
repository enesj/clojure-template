-- FORWARD
DROP TRIGGER IF EXISTS payer_types_updated_at ON payer_types;

-- BACKWARD
CREATE TRIGGER payer_types_updated_at
       BEFORE UPDATE ON payer_types
       FOR EACH ROW
       EXECUTE FUNCTION update_updated_at_column();