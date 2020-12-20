create table admin_action(
	id varchar(255) default snowflake() not null
		constraint admin_action_pk
			primary key,
	admin_id varchar(255),
	target_id varchar(255),
	type varchar(255),
	reason varchar(255),
	timestamp timestamp,
	end_timestamp timestamp
);

