-- D-0.1: o isolamento e imposto pelo armazenamento, lendo o usuario corrente de uma variavel de
-- sessao, e nao por filtro escrito na aplicacao. Uma consulta sem WHERE de organizacao ja devolve
-- apenas o permitido. Filtrar por organization_id em cada repositorio seria exatamente o erro que
-- §3.2 chama de unico erro caro possivel aqui.
--
-- Nenhuma politica abaixo referencia created_by_user_id ou user_id como chave de acesso: a
-- autorizacao vem sempre de membership sobre organization_id (§3.2, D39).

create or replace function public.app_current_user_id()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('app.current_user_id', true), '')::uuid
$$;

comment on function public.app_current_user_id() is
    'Usuario corrente da transacao, definido por set_config(''app.current_user_id'', ..., true). '
    'Ausente => NULL => nenhuma politica casa => negacao por omissao.';

alter function public.app_current_user_id() owner to app_owner;

-- FORCE: o dono das tabelas tambem fica sujeito as politicas. Sem isso, qualquer caminho que
-- rodasse como app_owner teria acesso irrestrito de forma implicita. Com FORCE, a unica excecao
-- e uma politica nomeada (as *_bootstrap abaixo), o que a torna auditavel por grep.
alter table public.app_user       enable row level security;
alter table public.app_user       force row level security;
alter table public.organization   enable row level security;
alter table public.organization   force row level security;
alter table public.membership     enable row level security;
alter table public.membership     force row level security;
alter table public.subscription   enable row level security;
alter table public.subscription   force row level security;
alter table public.credit_ledger  enable row level security;
alter table public.credit_ledger  force row level security;

-- ---------------------------------------------------------------------------
-- Caminho privilegiado: exclusivo de bootstrap_identity (D-0.4), que roda como app_owner via
-- SECURITY DEFINER. RLS nao consegue autorizar a criacao da primeira organizacao de um usuario,
-- porque no instante do INSERT ele ainda nao tem vinculo nenhum.
-- ---------------------------------------------------------------------------
create policy app_user_bootstrap on public.app_user
    for all to app_owner using (true) with check (true);

create policy organization_bootstrap on public.organization
    for all to app_owner using (true) with check (true);

create policy membership_bootstrap on public.membership
    for all to app_owner using (true) with check (true);

-- ---------------------------------------------------------------------------
-- Caminho normal: app_backend, o papel de login da API.
-- ---------------------------------------------------------------------------

-- O usuario enxerga a si mesmo. Nao ha necessidade, nesta fatia, de enxergar outros usuarios.
create policy app_user_self_select on public.app_user
    for select to app_backend
    using (id = public.app_current_user_id());

create policy organization_member_select on public.organization
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = organization.id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy membership_self_select on public.membership
    for select to app_backend
    using (user_id = public.app_current_user_id());

create policy subscription_member_select on public.subscription
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = subscription.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy credit_ledger_member_select on public.credit_ledger
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = credit_ledger.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

-- Append-only: ha INSERT, e nao ha politica de UPDATE nem de DELETE. Correcao so por lancamento
-- compensatorio.
create policy credit_ledger_member_insert on public.credit_ledger
    for insert to app_backend
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = credit_ledger.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

-- ---------------------------------------------------------------------------
-- Privilegios. Politica sem GRANT nao concede nada; GRANT sem politica tambem nao. Os dois
-- precisam concordar, e o conjunto abaixo e o minimo desta fatia: a unica escrita que a API faz
-- fora de bootstrap_identity e o lancamento no ledger.
-- ---------------------------------------------------------------------------
grant select on public.app_user      to app_backend;
grant select on public.organization  to app_backend;
grant select on public.membership    to app_backend;
grant select on public.subscription  to app_backend;
grant select on public.credit_ledger to app_backend;
grant insert on public.credit_ledger to app_backend;

-- D-0.9: append-only que depende de disciplina de codigo nao e append-only.
revoke update, delete, truncate on public.credit_ledger from app_backend;
revoke insert, update, delete, truncate on public.app_user     from app_backend;
revoke insert, update, delete, truncate on public.organization from app_backend;
revoke insert, update, delete, truncate on public.membership   from app_backend;
revoke insert, update, delete, truncate on public.subscription from app_backend;
