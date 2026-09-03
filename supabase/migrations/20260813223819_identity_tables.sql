-- Nucleo de identidade. §3.2 e D39: toda tabela de dominio e chaveada por organization_id,
-- nunca por user_id. created_by_user_id existe como metadado e jamais entra em politica de RLS
-- ou em verificacao de autorizacao.

-- D-0.5: id proprio (UUIDv7) separado do sub do provedor de autenticacao. Trocar de provedor,
-- ou existir usuario sem conta no provedor, nao obriga a reescrever chave estrangeira nenhuma.
create table public.app_user (
    id            uuid primary key default public.uuid_generate_v7(),
    auth_subject  text        not null unique,
    email         text,
    display_name  text,
    created_at    timestamptz not null default now(),
    constraint app_user_auth_subject_nao_vazio check (length(trim(auth_subject)) > 0)
);

create table public.organization (
    id                  uuid primary key default public.uuid_generate_v7(),
    kind                text        not null check (kind in ('personal', 'school')),
    name                text        not null check (length(trim(name)) > 0),
    created_by_user_id  uuid references public.app_user (id) on delete set null,
    created_at          timestamptz not null default now()
);

comment on column public.organization.created_by_user_id is
    'Metadado de autoria. NUNCA usar como chave de autorizacao (§3.2, D39).';

-- D39: membership N:N. Entrar numa escola e inserir uma linha, nao migrar dados.
create table public.membership (
    id               uuid primary key default public.uuid_generate_v7(),
    user_id          uuid        not null references public.app_user (id) on delete cascade,
    organization_id  uuid        not null references public.organization (id) on delete cascade,
    role             text        not null check (role in ('owner', 'admin', 'teacher')),
    created_at       timestamptz not null default now(),
    constraint membership_unico_por_par unique (user_id, organization_id)
);

create index membership_organization_idx on public.membership (organization_id);
create index membership_user_idx on public.membership (user_id);

alter table public.app_user     owner to app_owner;
alter table public.organization owner to app_owner;
alter table public.membership   owner to app_owner;
