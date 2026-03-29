alter table users
  add column language varchar(128) not null default 'en-US',
  add column role     varchar(128) not null default 'normal';

update users
set role = 'admin'
where username = 'malliina123@gmail.com';
