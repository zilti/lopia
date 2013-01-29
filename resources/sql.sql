create table block (
id int auto_increment primary key,
title varchar,
opener varchar,
opener_next_remind timestamp,
opener_gen_remind varchar,
opener_remind_callback varchar,
supporter varchar,
supporter_next_remind timestamp,
supporter_gen_remind varchar,
supporter_remind_callback varchar,
observers varchar,
opened timestamp,
closed timestamp,
due timestamp,
block_type varchar,
target int,
followup int);

create table log (
id int auto_increment primary key,
block_id int,
log_type varchar,
log_title varchar,
log_text clob);

create table tag_group (
id int auto_increment primary key,
title varchar,
description varchar);
create table tag (
id int auto_increment primary key,
tag_group_id int,
tag_title varchar,
tag_description varchar,
tag_type varchar,
allowed_values varchar,
meta clob);
create table block_tag (
block_id int,
tag_id int,
tag_value varchar);

create table attachment (
id int auto_increment primary key,
block_id int,
mime varchar,
filename varchar,
content blob);
