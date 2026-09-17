-- Fatia do outbox de resultado. As duas tabelas onde a correcao feita no aparelho passa a existir
-- no servidor: o resultado (`grading_result`) e a evidencia por questao (`answer_observation`).
--
-- Os nomes sao os da §11, e nao nomes novos: a arquitetura ja os tinha, e o que faltava era o
-- consumidor. `capture_session`, `capture_region` e `transcription` continuam fora — elas vem com
-- lote e com o modo degradado, que guardam imagem.
--
-- §3.2 e D39: as duas sao chaveadas por organization_id, nunca por user_id. O resultado de leitura
-- optica e fato sobre a folha do aluno, e nao sobre o professor que a escaneou — por isso nao ha
-- coluna de autoria aqui, e por isso qualquer membro da organizacao pode empurrar um pendente que
-- ficou no aparelho.

-- ---------------------------------------------------------------------------
-- grading_result: a nota apurada, append-only, com revisao.
--
-- §10 fixa a idempotencia em `(exam_id, student_id)`. `student_id` nao existe: nao ha tabela
-- `student`, e o pacote leva so o token porque I5 proibe dado pessoal direto no artefato imutavel.
-- Dentro de uma prova os dois sao a mesma chave — `exam_roster` impoe `unique (exam_id,
-- student_token)` —, e a idempotencia da §10 e intra-prova por definicao. Quando `student` existir,
-- o caminho `(exam_id, student_token) -> exam_roster -> student_id` resolve o historico por juncao,
-- sem reescrever fato nenhum, que e o que ADR-0003 manda.
-- ---------------------------------------------------------------------------
create table public.grading_result (
    id               uuid        primary key default public.uuid_generate_v7(),
    organization_id  uuid        not null references public.organization (id) on delete cascade,
    exam_id          uuid        not null,
    -- NULO na folha avulsa, e nao string vazia. §7 admite aluno fora da lista, com atribuicao feita
    -- depois da captura, e a nota dela e valida. Vazio faria todas as avulsas da mesma prova
    -- colidirem no unique de revisao abaixo; nulo as mantem distintas, que e o que elas sao.
    student_token    text        check (student_token is null or length(trim(student_token)) > 0),
    -- A revisao corrente e a de maior numero para o par (exam_id, student_token). Recaptura cria
    -- revisao nova; nenhuma anterior e tocada.
    revision         int         not null check (revision >= 1),
    -- A chave de idempotencia, gerada no aparelho, uma por captura. Reenvio do mesmo resultado —
    -- a confirmacao que se perdeu no caminho — chega com o mesmo valor e nao grava nada novo.
    -- Recaptura e outra captura, logo outro valor, logo revisao nova. Comparar conteudo no lugar
    -- disto seria errado: recaptura que desse a mesma nota e recaptura, e nao reenvio.
    capture_id       text        not null check (length(trim(capture_id)) > 0),
    origin           text        not null default 'omr' check (origin in ('omr', 'ai', 'teacher')),
    -- Contra qual pacote e qual variante a nota foi apurada. Nota sem dizer de qual pacote e vira
    -- numero sem prova no dia em que existir mais de uma versao da prova.
    package_hash     text        not null check (package_hash ~ '^[0-9a-f]{64}$'),
    variant_id       text        not null check (length(trim(variant_id)) > 0),
    points           int         not null check (points >= 0),
    max_score        int         not null check (max_score >= 0),
    -- Falso quando ha questao pendente de revisao humana. Sem esta coluna, nota parcial e nota
    -- fechada chegariam indistinguiveis, e a invariante que manda revisao humana vencer o
    -- automatico morreria no transporte.
    closed           boolean     not null,
    -- Quando o aparelho apurou, e nao quando o servidor recebeu: o modelo offline separa os dois,
    -- e as vezes por dias.
    captured_at      timestamptz not null,
    created_at       timestamptz not null default now(),
    constraint grading_result_captura_unica   unique (exam_id, capture_id),
    constraint grading_result_revisao_unica   unique (exam_id, student_token, revision),
    constraint grading_result_nota_na_escala  check (points <= max_score),
    constraint grading_result_id_org_unico    unique (id, organization_id),
    constraint grading_result_prova_da_mesma_org
        foreign key (exam_id, organization_id) references public.exam (id, organization_id)
        on delete restrict
);

create index grading_result_organization_idx on public.grading_result (organization_id);
create index grading_result_corrente_idx
    on public.grading_result (exam_id, student_token, revision desc);

-- ---------------------------------------------------------------------------
-- answer_observation: a evidencia da correcao, questao a questao (§11, append-only).
--
-- NAO e nota por habilidade. A unidade continua sendo o ponto por questao que o professor declarou
-- no gabarito. O vinculo item->habilidade vive no `ExamPackage`, que e imutavel e hasheado: com
-- item e pontos gravados aqui, o fato analitico e derivavel depois por juncao com o pacote. Sem
-- esta tabela, a prova corrigida hoje ficaria sem dimensao analitica para sempre — nao por falta de
-- codigo, e sim porque a folha de papel sai de circulacao e o dado nao volta.
-- ---------------------------------------------------------------------------
create table public.answer_observation (
    id                 uuid   primary key default public.uuid_generate_v7(),
    organization_id    uuid   not null references public.organization (id) on delete cascade,
    grading_result_id  uuid   not null,
    item_id            text   not null check (length(trim(item_id)) > 0),
    -- A forma da resposta, como a leitura a entregou. `em_branco` e afirmacao sobre o que o aluno
    -- fez; `indecisa` e afirmacao sobre o que a leitura conseguiu apurar. As duas so parecem iguais
    -- ate a nota, e por isso sao valores diferentes.
    answer_kind        text   not null
        check (answer_kind in ('marcada', 'em_branco', 'multipla_marcacao', 'indecisa')),
    -- As alternativas envolvidas. Uma em `marcada`; todas as marcadas em `multipla_marcacao`; as
    -- duvidosas em `indecisa`; nenhuma em `em_branco`. Guardar todas, e nao a "vencedora",
    -- e o mesmo motivo de `QuestionAnswer`: desempatar transformaria rasura em resposta.
    answer_options     text[] not null default '{}',
    worth              int    not null check (worth >= 0),
    earned             int    not null check (earned >= 0),
    constraint answer_observation_item_unico   unique (grading_result_id, item_id),
    constraint answer_observation_na_escala    check (earned <= worth),
    -- Questao que depende de revisao nao rende ponto. E a mesma guarda que `QuestionOutcome` faz no
    -- dominio, do lado do banco: transformar duvida em nota e o defeito que ela existe para impedir.
    constraint answer_observation_pendente_sem_ponto
        check (answer_kind in ('marcada', 'em_branco') or earned = 0),
    constraint answer_observation_em_branco_sem_alternativa
        check (answer_kind <> 'em_branco' or cardinality(answer_options) = 0),
    constraint answer_observation_resultado_da_mesma_org
        foreign key (grading_result_id, organization_id)
        references public.grading_result (id, organization_id)
        on delete restrict
);

create index answer_observation_organization_idx on public.answer_observation (organization_id);
create index answer_observation_resultado_idx on public.answer_observation (grading_result_id);

-- ---------------------------------------------------------------------------
-- Append-only, e a garantia e do banco.
--
-- Mesmo desenho de `exam_package_imutavel`, e pela mesma razao registrada la: o REVOKE barra
-- app_backend, e o gatilho barra tambem app_owner e qualquer caminho que alguem venha a abrir
-- depois. Garantia que depende de todo chamador se comportar nao e garantia.
--
-- I2: resultados sao fatos append-only. Corrigir um resultado e gravar revisao nova, nunca editar a
-- que esta la — e a revisao anterior continua legivel, porque revisao humana que vence o automatico
-- precisa poder mostrar o que venceu.
-- ---------------------------------------------------------------------------
create or replace function public.resultado_append_only()
returns trigger
language plpgsql
as $funcao$
begin
    raise exception 'fato de avaliacao e append-only: % recusado sobre %.%',
        tg_op, tg_table_name, old.id
        using errcode = 'PT001',
              hint = 'corrigir resultado e gravar revisao nova, com capture_id proprio';
end
$funcao$;

alter function public.resultado_append_only() owner to app_owner;

create trigger grading_result_sem_update
    before update on public.grading_result
    for each row execute function public.resultado_append_only();

create trigger grading_result_sem_delete
    before delete on public.grading_result
    for each row execute function public.resultado_append_only();

create trigger answer_observation_sem_update
    before update on public.answer_observation
    for each row execute function public.resultado_append_only();

create trigger answer_observation_sem_delete
    before delete on public.answer_observation
    for each row execute function public.resultado_append_only();

alter table public.grading_result     owner to app_owner;
alter table public.answer_observation owner to app_owner;

-- ---------------------------------------------------------------------------
-- RLS. As duas nascem habilitadas e forcadas; a guarda derivada do catalogo, em
-- ConnectionRoleTest, reprova o build se alguma nascer sem.
-- ---------------------------------------------------------------------------
alter table public.grading_result     enable row level security;
alter table public.grading_result     force  row level security;
alter table public.answer_observation enable row level security;
alter table public.answer_observation force  row level security;

create policy grading_result_member_select on public.grading_result
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = grading_result.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy grading_result_member_insert on public.grading_result
    for insert to app_backend
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = grading_result.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy answer_observation_member_select on public.answer_observation
    for select to app_backend
    using (
        exists (
            select 1
            from public.membership m
            where m.organization_id = answer_observation.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

create policy answer_observation_member_insert on public.answer_observation
    for insert to app_backend
    with check (
        exists (
            select 1
            from public.membership m
            where m.organization_id = answer_observation.organization_id
              and m.user_id = public.app_current_user_id()
        )
    );

-- ---------------------------------------------------------------------------
-- Privilegios. Politica sem GRANT nao concede nada, e GRANT sem politica tambem nao.
-- Nenhum UPDATE, nenhum DELETE: e o que append-only quer dizer em privilegio.
-- ---------------------------------------------------------------------------
grant select, insert on public.grading_result     to app_backend;
grant select, insert on public.answer_observation to app_backend;

revoke update, delete, truncate on public.grading_result     from app_backend;
revoke update, delete, truncate on public.answer_observation from app_backend;

-- ---------------------------------------------------------------------------
-- Finalidade e classe de retencao (ADR-0006, ADR-0012). Sem isto,
-- RetentionDeclarationTest reprova a construcao — e e para isso que a guarda existe.
--
-- Classe B, "fato de avaliacao e nota": pontuacao por questao, nota final, associacao a habilidade
-- da BNCC, periodo e historico de revisao. E a classe passivel de anonimizacao, porque o valor
-- pedagogico esta na chave e no numero, e nao na identidade do titular. A retencao executavel dela
-- — anonimizacao apos o prazo e pedido de eliminacao — NAO entra nesta fatia: o prazo e de cinco
-- anos, nao ha o que exercitar, e o item esta registrado no §16 com fatia-limite e dono.
-- ---------------------------------------------------------------------------
comment on table public.grading_result is
    'Nota objetiva apurada no aparelho contra pacote publicado, append-only por revisao, para '
    'historico escolar e comparacao longitudinal. [retencao:B]';

comment on table public.answer_observation is
    'Resultado por questao de um grading_result, evidencia da correcao e insumo do fato analitico '
    'por habilidade, que e derivado do pacote e nao gravado aqui. [retencao:B]';
