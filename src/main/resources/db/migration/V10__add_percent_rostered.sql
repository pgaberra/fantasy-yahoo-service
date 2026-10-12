-- Yahoo's percent rostered: the share of all Yahoo leagues that roster the player, as Yahoo
-- reported it at the last sync. Null when Yahoo gave none.
ALTER TABLE skaters ADD COLUMN percent_rostered DOUBLE PRECISION;
ALTER TABLE goalies ADD COLUMN percent_rostered DOUBLE PRECISION;
