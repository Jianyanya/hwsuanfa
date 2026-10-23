ALTER TABLE `user`
    ADD COLUMN `luogu_handle` VARCHAR(64) DEFAULT NULL AFTER `atcoder_handle`;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_user_luogu_handle` (`luogu_handle`);
