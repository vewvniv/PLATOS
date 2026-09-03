-- Fatia 2a. As tres tabelas da publicacao: a identidade da prova (`exam`), o artefato imutavel
-- (`exam_package`) e o dado pessoal que precisa poder mudar (`exam_roster`).
--
-- §3.2 e D39: as tres sao chaveadas por organization_id, nunca por user_id. created_by_user_id
-- continua metadado e nao entra em politica nenhuma.

-- ---------------------------------------------------------------------------
-- exam: a prova como identidade. O conteudo nao mora aqui — mora no pacote publicado (D-2a.6).
-- ---------------------------------------------------------------------------
create table public.exam (
    id                  uuid primary key default public.uuid_generate_v7(),
    organization_id     uuid        not null references public.organization (id) on delete cascade,
    -- §8: o QR impresso carrega `{exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}`, e a
    -- captura e offline — ela resolve a prova por este identificador sem saber de organizacao.
    -- Por isso o unique e global, e nao por organizacao. E o mesmo valor que o pacote declara em
    -- `meta.exam_id` e que `LayoutEngine.qrPayloadOf` recebe.
    short_id            text        not null,
    title               text        not null check (length(trim(title)) > 0),
    created_by_user_id  uuid references public.app_user (id) on delete set null,
    created_at          timestamptz not null default now(),
    constraint exam_short_id_unico unique (short_id),
    constraint exam_short_id_formato check (short_id ~ '^[a-z0-9][a-z0-9-]{2,63}$'),
    -- Alvo das chaves estrangeiras compostas abaixo: e o que impede uma linha filha de apontar
    -- prova de uma organizacao carregando organization_id de outra.
    constraint exam_id_org_unico unique (id, organization_id)
);

comment on column public.exam.created_by_user_id is
    'Metadado de autoria. NUNCA usar como chave de autorizacao (§3.2, D39).';

create index exam_organization_idx on public.exam (organization_id);

-- ---------------------------------------------------------------------------
-- exam_package: o artefato imutavel (§5, D-2a.3).
-- ---------------------------------------------------------------------------
create table public.exam_package (
    id               uuid primary key default public.uuid_generate_v7(),
    organization_id  uuid        not null references public.organization (id) on delete restrict,
    exam_id          uuid        not null,
    content_hash     text        not null check (content_hash ~ '^[0-9a-f]{64}$'),
    -- TEXT, e nao JSONB, de proposito. `jsonb` normaliza: reordena chaves e descarta espacamento.
    -- O hash e calculado sobre a serializacao canonica (D-2a.4), entao um pacote guardado como
    -- `jsonb` voltaria com outros bytes, e o dispositivo que o puxasse na fatia 4 recalcularia um
    -- hash diferente do declarado. A verificacao falharia sem nada estar errado com o pacote — ou,
    -- pior, alguem "consertaria" rehasheando no servidor. A validacao de sintaxe continua
    -- existindo, pelo cast abaixo, que valida sem normalizar o que fica gravado.
    content          text        not null,
    published_at     timestamptz not null default now(),
    constraint exam_package_content_e_json check (content::json is not null),
    -- Uma prova publicada tem um pacote, e um so. O QR identifica a prova por short_id e nada mais
    -- (§8): se a mesma prova tivesse dois pacotes, a captura da fatia 3 nao teria como saber de
    -- qual geometria saiu a folha em cima da mesa. Corrigir prova publicada e publicar prova nova,
    -- com short_id proprio — que e o que a folha ja distribuida diz que ela e.
    constraint exam_package_um_por_prova unique (exam_id),
    -- Sem ON DELETE CASCADE, de proposito: apagar a prova levaria o pacote junto, e DELETE que
    -- chega por cascata continua sendo DELETE.
    constraint exam_package_prova_da_mesma_org
        foreign key (exam_id, organization_id) references public.exam (id, organization_id)
        on delete restrict
);

create index exam_package_organization_idx on public.exam_package (organization_id);

-- D-2a.3: a imutabilidade e do banco, e nao da aplicacao. O REVOKE mais abaixo ja barra
-- app_backend; o gatilho barra tambem app_owner, que e dono da tabela, e qualquer caminho que
-- alguem venha a abrir depois. Garantia que depende de todo chamador se comportar nao e garantia.
--
-- TRUNCATE nao passa por gatilho de linha, e fica coberto so pelo REVOKE. E deliberado: quem tem
-- privilegio de truncar a tabela tem privilegio de derruba-la, e a fronteira que esta fatia precisa
-- defender e a da aplicacao.
create or replace function public.exam_package_imutavel()
returns trigger
language plpgsql
as $funcao$
begin
    raise exception 'exam_package e imutavel: % recusado sobre o pacote %', tg_op, old.id
        using errcode = 'PT001',
              hint = 'corrigir prova publicada e publicar um pacote novo, com hash proprio';
end
$funcao$;

alter function public.exam_package_imutavel() owner to app_owner;

create trigger exam_package_sem_update
    before update on public.exam_package
    for each row execute function public.exam_package_imutavel();

create trigger exam_package_sem_delete
    before delete on public.exam_package
    for each row execute function public.exam_package_imutavel();

-- ---------------------------------------------------------------------------
-- exam_roster: ADR-0002 e I5. Nome, turma e matricula vivem aqui, fora do artefato imutavel.
-- Esta tabela PRECISA poder mudar e ser apagada — e o direito de eliminacao, que nao alcanca copia
-- ja distribuida. Nao ha segundo hash sobre ela: a integridade do impresso vem do QR.
-- ---------------------------------------------------------------------------
create table public.exam_roster (
    id               uuid        primary key default public.uuid_generate_v7(),
    organization_id  uuid        not null references public.organization (id) on delete cascade,
    exam_id          uuid        not null,
    -- O mesmo token que o pacote e o QR carregam. E a unica ligacao entre esta linha e o artefato
    -- imutavel — e o artefato nao guarda o caminho de volta.
    student_token    text        not null check (length(trim(student_token)) > 0),
    display_name     text        not null check (length(trim(display_name)) > 0),
    class_group      text,
    enrollment_id    text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint exam_roster_token_unico_por_prova unique (exam_id, student_token),
    constraint exam_roster_prova_da_mesma_org
        foreign key (exam_id, organization_id) references public.exam (id, organization_id)
        on delete cascade
);

create index exam_roster_organization_idx on public.exam_roster (organization_id);

alter table public.exam         owner to app_owner;
alter table public.exam_package owner to app_owner;
alter table public.exam_roster  owner to app_owner;

-- ---------------------------------------------------------------------------
-- RLS. As tres nascem habilitadas e forcadas; a guarda derivada do catalogo, em ConnectionRoleTest,
-- reprova o build se alguma nascer sem.
-- ---------------------------------------------------------------------------
alter table public.exam         enable row level security;
alter table public.exam         force  row level security;
alter table public.exam_package enable row level security;
alter table public.exam_package force  row level security;
alter table public.exam_roster  enable row level security;
alter table public.exam_roster  force  row level security;

create policy exam_member_select on public.exam
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy exam_member_insert on public.exam
    for insert to app_backend
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy exam_package_member_select on public.exam_package
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_package.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy exam_package_member_insert on public.exam_package
    for insert to app_backend
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_package.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy exam_roster_member_select on public.exam_roster
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_roster.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy exam_roster_member_insert on public.exam_roster
    for insert to app_backend
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_roster.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

-- O roster e mutavel por exigencia de I5: e o unico lugar desta fatia com UPDATE e DELETE.
create policy exam_roster_member_update on public.exam_roster
    for update to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_roster.organization_id
              and m.user_id = public.app_current_user_id()
        )
    )
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_roster.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy exam_roster_member_delete on public.exam_roster
    for delete to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = exam_roster.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

-- ---------------------------------------------------------------------------
-- Privilegios. Politica sem GRANT nao concede nada, e GRANT sem politica tambem nao; os dois
-- precisam concordar. O conjunto abaixo e o minimo desta fatia.
-- ---------------------------------------------------------------------------
grant select, insert                 on public.exam         to app_backend;
grant select, insert                 on public.exam_package to app_backend;
grant select, insert, update, delete on public.exam_roster  to app_backend;

revoke update, delete, truncate on public.exam         from app_backend;
revoke update, delete, truncate on public.exam_package from app_backend;
revoke truncate                 on public.exam_roster  from app_backend;
