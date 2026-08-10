-- Apply once to an existing UniNook database created before school onboarding.
-- Existing school assignments are preserved; only the incorrect default is removed.
ALTER TABLE `user`
    MODIFY school_id BIGINT NULL DEFAULT NULL;
