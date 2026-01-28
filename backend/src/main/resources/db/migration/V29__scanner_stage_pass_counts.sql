ALTER TABLE scanner_runs
    ADD COLUMN IF NOT EXISTS stage_pass_counts TEXT;
