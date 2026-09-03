-- D40: a assinatura pertence a organizacao, nunca ao usuario. Um professor com plano Pro pessoal
-- que tambem e membro de uma escola Enterprise simplesmente tem direitos diferentes em cada
-- contexto — sem conflito e sem merge de assinatura.

create table public.subscription (
    id                    uuid primary key default public.uuid_generate_v7(),
    organization_id       uuid        not null references public.organization (id) on delete cascade,
    plan                  text        not null check (length(trim(plan)) > 0),
    billing_period        text        not null check (billing_period in ('monthly', 'semiannual', 'annual')),
    status                text        not null check (status in ('active', 'past_due', 'canceled', 'expired')),
    current_period_start  timestamptz not null,
    current_period_end    timestamptz not null,
    created_at            timestamptz not null default now(),
    constraint subscription_periodo_valido check (current_period_end > current_period_start)
);

-- No maximo uma assinatura nao encerrada por organizacao. Encerrada = canceled ou expired.
create unique index subscription_uma_aberta_por_org
    on public.subscription (organization_id)
    where status in ('active', 'past_due');

-- D25 / D-0.9: ledger unificado e append-only. Quotas de plano e pacotes avulsos usam a mesma
-- estrutura, o que evita reescrever a cobranca quando add-ons aparecerem. O saldo e sempre a soma
-- dos lancamentos; nunca um valor mutavel.
create table public.credit_ledger (
    id               uuid primary key default public.uuid_generate_v7(),
    organization_id  uuid        not null references public.organization (id) on delete cascade,
    credit_type      text        not null check (length(trim(credit_type)) > 0),
    amount           bigint      not null check (amount <> 0),
    reason           text        not null check (length(trim(reason)) > 0),
    occurred_at      timestamptz not null default now()
);

create index credit_ledger_org_type_idx on public.credit_ledger (organization_id, credit_type);

alter table public.subscription   owner to app_owner;
alter table public.credit_ledger  owner to app_owner;
