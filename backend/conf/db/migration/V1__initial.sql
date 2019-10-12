create table pics
(
    `key` varchar(128)                           not null primary key,
    owner varchar(128)                           not null,
    added timestamp default CURRENT_TIMESTAMP(3) not null
) charset = utf8mb4;
