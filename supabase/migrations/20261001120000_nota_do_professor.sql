-- A nota do professor (slice-5c-2-a-nota-do-professor, ADR-0021).
--
-- O fato continua em `grading_result` e `answer_observation` (§11 ja nomeia `grading_result(origin, path)`).
-- Nada aqui relaxa as invariantes das duas tabelas: append-only por gatilho, RLS forcada, organization_id.

-- ---------------------------------------------------------------------------
-- 1. A pontuacao deixa de ser inteira. `numeric(8,2)` cobre 0..999999.99, que e o alcance de `Pontos`
-- no dominio. ATENCAO: `numeric(8,2)` ARREDONDA 3 casas em silencio no insert (medido em
-- GradingResultCurrentViewTest); a recusa de mais de 2 casas e do dominio, antes do SQL.
-- `max_score` e `worth` seguem `int`: o pacote e inteiro e imutavel. O alargamento de int para numeric
-- preserva todo valor existente (3 passa a ser 3.00). Reescrever a tabela nao dispara os gatilhos de
-- UPDATE/DELETE, que sao de linha.
-- ---------------------------------------------------------------------------
alter table public.grading_result
    alter column points type numeric(8,2) using points::numeric(8,2);

alter table public.answer_observation
    alter column earned type numeric(8,2) using earned::numeric(8,2);

-- ---------------------------------------------------------------------------
-- 2. Quem corrigiu pelo olho, e qual captura da parcial a nota do professor completa.
-- Nulos nas linhas automaticas (`omr`); obrigatorios na linha `teacher`. O check e a guarda do banco,
-- escrita independente de quem escreve as linhas (ADR-0015, decisao 3).
-- ---------------------------------------------------------------------------
alter table public.grading_result
    add column path text,
    add column completes_capture_id text;

alter table public.grading_result
    add constraint grading_result_origem_do_professor check (
        (origin = 'teacher' and path is not null and completes_capture_id is not null)
        or (origin <> 'teacher' and path is null and completes_capture_id is null)
    ),
    add constraint grading_result_caminho_conhecido check (path is null or path = 'image'),
    add constraint grading_result_completa_captura_nao_vazia
        check (completes_capture_id is null or length(trim(completes_capture_id)) > 0);

-- ---------------------------------------------------------------------------
-- 3. A evidencia da discursiva corrigida. Quem enumera os valores de answer_kind sao tres registros que
-- se conhecem: o dominio (`AnswerKind`), este check, e tools/parity/answer-kind.mjs, que le o check da
-- ULTIMA migration que o declara.
-- ---------------------------------------------------------------------------
alter table public.answer_observation drop constraint answer_observation_answer_kind_check;
alter table public.answer_observation
    add constraint answer_observation_answer_kind_check
    check (answer_kind in ('marcada', 'em_branco', 'multipla_marcacao', 'indecisa', 'discursiva_corrigida'));

-- A excecao e so para o tipo novo: objetiva que depende de revisao continua nao rendendo ponto.
alter table public.answer_observation drop constraint answer_observation_pendente_sem_ponto;
alter table public.answer_observation
    add constraint answer_observation_pendente_sem_ponto
    check (answer_kind in ('marcada', 'em_branco', 'discursiva_corrigida') or earned = 0);

alter table public.answer_observation
    add constraint answer_observation_discursiva_sem_alternativa
    check (answer_kind <> 'discursiva_corrigida' or cardinality(answer_options) = 0);

-- ---------------------------------------------------------------------------
-- 4. A revisao corrente, derivada: a regra "revisao humana vence a automatica da mesma captura, em
-- qualquer ordem de chegada" mora aqui, num lugar so. Nada e gravado nem atualizado (append-only).
--
-- Uma revisao automatica nao e elegivel se alguma revisao `teacher` da mesma prova declara completar a
-- captura dela. Entre as elegiveis, a de maior `revision` e a corrente. A chave da folha e o token; na
-- folha avulsa (token nulo) e a captura da parcial (`completes_capture_id` na nota do professor, o
-- proprio `capture_id` na automatica), para a nota seguir a propria parcial sem se misturar com outra
-- avulsa.
--
-- `security_invoker`: a view roda com os privilegios de QUEM CONSULTA, e a RLS de `grading_result`
-- continua valendo. Sem isso, cada escola leria as folhas das outras.
-- ---------------------------------------------------------------------------
create view public.grading_result_current
    with (security_invoker = true) as
select distinct on (
           r.exam_id,
           r.student_token,
           case when r.student_token is null then coalesce(r.completes_capture_id, r.capture_id) end
       )
       r.id, r.organization_id, r.exam_id, r.student_token, r.revision, r.capture_id, r.origin,
       r.path, r.completes_capture_id, r.package_hash, r.variant_id, r.points, r.max_score,
       r.closed, r.captured_at, r.created_at
from public.grading_result r
where r.origin = 'teacher'
   or not exists (
        select 1
        from public.grading_result t
        where t.exam_id = r.exam_id
          and t.origin = 'teacher'
          and t.completes_capture_id = r.capture_id
   )
order by r.exam_id,
         r.student_token,
         case when r.student_token is null then coalesce(r.completes_capture_id, r.capture_id) end,
         r.revision desc;

alter view public.grading_result_current owner to app_owner;
grant select on public.grading_result_current to app_backend;

comment on view public.grading_result_current is
    'A revisao corrente de cada folha: a nota do professor vence a revisao automatica da mesma captura, '
    'em qualquer ordem de chegada. Derivada, nao gravada.';
