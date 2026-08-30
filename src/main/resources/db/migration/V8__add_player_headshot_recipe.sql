-- How a stored thumbnail was rendered. Changing the rendering — a different crop, a different
-- size — leaves every stored image stale while its source URL is untouched, so the sync has
-- nothing to notice and would keep serving the old pictures forever. Recording the recipe beside
-- the image gives it something to compare: a row rendered by an older one is refreshed exactly
-- as a row whose source changed is. NULL marks the centre-cropped thumbnails that came before,
-- which the next sync therefore replaces; they keep being served until it does.
ALTER TABLE player_headshots ADD COLUMN recipe VARCHAR(32);
