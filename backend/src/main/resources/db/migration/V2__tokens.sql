create table users
(
    id       int          not null primary key auto_increment,
    username varchar(128) not null unique,
    added    timestamp(3) not null default current_timestamp(3)
);

create table tokens
(
    token   varchar(256) not null primary key,
    user    int          not null references users (id) on update cascade on delete cascade,
    added   timestamp(3) not null default current_timestamp(3),
    enabled boolean      not null default true
);

insert into users(username)
select distinct(owner)
from pics;

alter table pics
    add column user int references users (id) on update cascade on delete cascade;

update pics p join users u
on p.owner = u.username set user = u.id;

alter table pics drop column owner;
