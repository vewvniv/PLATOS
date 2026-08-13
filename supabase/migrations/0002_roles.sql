-- D-0.2: a API conecta como app_backend, papel SEM SUPERUSER e SEM BYPASSRLS.
-- Conectar como postgres faria toda politica RLS ser silenciosamente ignorada — a falha mais
-- perigosa possivel nesta fatia, porque os testes passariam mesmo com politica errada.
--
-- app_owner e o dono das tabelas e da funcao de bootstrap. Nao tem LOGIN: ninguem se conecta
-- como ele. Ele existe para que a unica excecao a RLS seja uma politica nomeada e auditavel
-- (ver 0005) em vez de um atributo de papel espalhado.

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'app_owner') then
        create role app_owner nologin nosuperuser nobypassrls nocreatedb;
    end if;

    if not exists (select 1 from pg_roles where rolname = 'app_backend') then
        create role app_backend login nosuperuser nobypassrls nocreatedb nocreaterole
            password 'app_backend';
    end if;
end
$$;

-- Garante os atributos mesmo que os papeis ja existissem de uma execucao anterior.
alter role app_owner nologin nosuperuser nobypassrls;
alter role app_backend login nosuperuser nobypassrls;

-- Necessario para transferir a posse das tabelas para app_owner nas migrations seguintes
-- sem exigir superusuario.
grant app_owner to current_user;

do $$
begin
    execute format('grant connect on database %I to app_backend', current_database());
end
$$;

grant usage on schema public to app_backend;
grant usage on schema public to app_owner;
