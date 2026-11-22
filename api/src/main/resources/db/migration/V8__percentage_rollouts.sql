ALTER TABLE flag_environment_configs
    ADD COLUMN rollout_percentage smallint CHECK (rollout_percentage BETWEEN 0 AND 100);
