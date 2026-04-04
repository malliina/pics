alter table users
  add column cognito_sub varchar(128) unique,
  add column email       varchar(128) unique;
