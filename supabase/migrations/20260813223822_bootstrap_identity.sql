-- D-0.4: bootstrap de identidade por funcao SECURITY DEFINER chamada explicitamente pelo Kotlin.
--
-- RLS nao consegue autorizar a criacao da primeira organizacao de um usuario: no instante do
-- INSERT ele ainda nao tem vinculo nenhum. As alternativas eram (a) politica permitindo
-- auto-vinculo em organizacao sem membros, que deixa reivindicavel qualquer organizacao que fique
-- vazia, e (b) created_by_user_id como chave de bootstrap, que viola §3.2 diretamente.
--
-- Esta e a UNICA funcao privilegiada do sistema. Toda escrita de dominio permanece sob RLS.
-- Nao e trigger escondida: a chamada e explicita, uma por requisicao autenticada, e o *quando*
-- e decidido em Kotlin, o que a torna testavel.

create or replace function public.bootstrap_identity(
    p_auth_subject text,
    p_email        text default null,
    p_display_name text default null
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user_id  uuid;
    v_org_id   uuid;
    v_org_name text;
begin
    if p_auth_subject is null or length(trim(p_auth_subject)) = 0 then
        raise exception 'auth_subject e obrigatorio' using errcode = '22023';
    end if;

    -- Serializa o bootstrap concorrente do mesmo sujeito. Sem isso, duas requisicoes simultaneas
    -- de um usuario novo poderiam criar duas organizacoes pessoais ou vazar violacao de
    -- restricao unica ao cliente. O lock e liberado no fim da transacao.
    perform pg_advisory_xact_lock(hashtextextended(p_auth_subject, 0));

    select id into v_user_id
    from app_user
    where auth_subject = p_auth_subject;

    if v_user_id is null then
        insert into app_user (auth_subject, email, display_name)
        values (p_auth_subject, p_email, p_display_name)
        returning id into v_user_id;
    else
        update app_user
        set email        = coalesce(p_email, email),
            display_name = coalesce(p_display_name, display_name)
        where id = v_user_id;
    end if;

    -- Organizacao pessoal apenas quando o usuario nao tem NENHUM vinculo. Um usuario que so
    -- pertence a uma organizacao escolar nao ganha organizacao pessoal.
    if not exists (select 1 from membership where user_id = v_user_id) then
        v_org_name := coalesce(
            nullif(trim(coalesce(p_display_name, '')), ''),
            nullif(split_part(coalesce(p_email, ''), '@', 1), ''),
            'Minha organizacao'
        );

        insert into organization (kind, name, created_by_user_id)
        values ('personal', v_org_name, v_user_id)
        returning id into v_org_id;

        insert into membership (user_id, organization_id, role)
        values (v_user_id, v_org_id, 'owner');
    end if;

    return v_user_id;
end;
$$;

alter function public.bootstrap_identity(text, text, text) owner to app_owner;

revoke all on function public.bootstrap_identity(text, text, text) from public;
grant execute on function public.bootstrap_identity(text, text, text) to app_backend;

comment on function public.bootstrap_identity(text, text, text) is
    'Unica funcao SECURITY DEFINER do sistema (D-0.4). Idempotente. Revisar esta funcao e revisar '
    'toda a superficie privilegiada do banco.';
