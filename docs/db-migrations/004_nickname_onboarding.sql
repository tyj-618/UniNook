-- Existing accounts have already selected a nickname and remain out of the new onboarding flow.
ALTER TABLE `user`
    ADD COLUMN nickname_confirmed TINYINT NOT NULL DEFAULT 1 COMMENT '是否已确认昵称' AFTER nickname;
