drop table if exists user_devices;

create table user_devices (
  user_id uuid references auth.users not null primary key,
  device_name text not null,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

-- 启用 RLS
alter table user_devices enable row level security;

-- 重新创建策略
create policy "Users can upsert their own devices" 
on user_devices for insert to authenticated 
with check (auth.uid() = user_id);

create policy "Users can update their own devices" 
on user_devices for update to authenticated 
using (auth.uid() = user_id);

create policy "Users can view their own devices" 
on user_devices for select to authenticated 
using (auth.uid() = user_id);