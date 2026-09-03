-- ADR-0012. O modo de identificacao de aluno, e a declaracao de finalidade e retencao.
--
-- Duas coisas que a politica de privacidade v1.0 afirma e o schema nao sustentava:
--
-- 1. §3.4 diz que a plataforma "oferece, e recomenda como padrao" o modo sem identificacao
--    nominal. Ela nao oferecia: `exam_roster.display_name` e `not null` e nada distinguia nome
--    civil de apelido.
-- 2. ADR-0006 exige que toda tabela com dado pessoal nasca com finalidade e classe de retencao
--    declaradas. `..._exam_tables.sql` nasceu sem, e nada acusava.

-- ---------------------------------------------------------------------------
-- O modo e da organizacao, e o padrao e o codificado.
--
-- Da organizacao, e nao da prova nem da linha: se uma prova pudesse estar nominal e outra
-- codificada, nao haveria resposta para "esta organizacao trata nome civil de menor?", que e a
-- pergunta que §3.3 da politica obriga a responder.
--
-- Padrao codificado porque recomendar um padrao na politica e nascer no outro seria descrever um
-- sistema que nao existe. `default` cobre as linhas existentes sem update de dados.
-- ---------------------------------------------------------------------------
alter table public.organization
    add column identification_mode text not null default 'coded'
        check (identification_mode in ('coded', 'nominal'));

comment on column public.organization.identification_mode is
    'ADR-0012. `coded`: aluno por numero, codigo ou apelido. `nominal`: nome civil e identificador '
    'da instituicao. Novo nasce `coded`; `nominal` e escolha explicita.';

-- O composto que o roster referencia. Sem ele nao ha como uma restricao declarativa do roster
-- alcancar o modo da organizacao.
alter table public.organization
    add constraint organization_id_modo_unico unique (id, identification_mode);

-- ---------------------------------------------------------------------------
-- O roster obedece ao modo, e a restricao e declarativa.
--
-- `identification_mode` aqui e denormalizacao deliberada: e o que permite um `check` alcancar o
-- modo da organizacao sem trigger. A chave estrangeira composta com `on update cascade` mantem os
-- dois lados iguais, e e o mesmo recurso que `exam_roster` ja usa para amarrar `exam_id` a
-- `organization_id`.
--
-- Um efeito colateral que e a propriedade mais util daqui: trocar uma organizacao de `nominal`
-- para `coded` **falha** enquanto existir linha com `enrollment_id`. A transicao insegura fica
-- impossivel por construcao, em vez de depender de alguem lembrar de limpar antes.
--
-- Trigger foi descartado: e imperativo, e `alter table ... disable trigger` o contorna. Restricao
-- declarativa nao tem esse caminho.
-- ---------------------------------------------------------------------------
alter table public.exam_roster
    add column identification_mode text not null default 'coded'
        check (identification_mode in ('coded', 'nominal'));

-- O `default 'coded'` aqui tem um efeito que vale nomear: um insert cru numa organizacao
-- `nominal`, sem informar o modo, **falha** na chave estrangeira. Falha alto, e do lado seguro —
-- o caminho que passa sem o chamador pensar e o codificado. Quem insere em `nominal` precisa ser
-- explicito, e e a API que carimba o modo lendo a organizacao na mesma transacao.
alter table public.exam_roster
    add constraint exam_roster_modo_da_organizacao
        foreign key (organization_id, identification_mode)
        references public.organization (id, identification_mode)
        on update cascade
        on delete cascade;

-- O unico campo cuja presenca e, por si, incompativel com o modo codificado: identificador
-- emitido pela instituicao, que existe para ligar o aluno ao cadastro escolar e nao tem uso
-- pedagogico aqui.
--
-- `display_name` continua texto livre, e isso e deliberado: nenhuma coluna distingue "Maria Silva"
-- de "aluno 17", e uma checagem que tentasse — so digitos, sem espaco, tamanho maximo — reprovaria
-- "Ana B." e aprovaria "Maria". O sistema nao afirma o que nao consegue sustentar; quem fecha esse
-- lado e a coercao na interface (§3.5), da fatia da tela.
alter table public.exam_roster
    add constraint exam_roster_matricula_so_em_modo_nominal
        check (identification_mode = 'nominal' or enrollment_id is null);

-- ---------------------------------------------------------------------------
-- Finalidade e classe de retencao, em TODA tabela de `public`.
--
-- Em todas, e nao so nas que tem dado pessoal: exigencia que dependa de alguem manter a lista de
-- quais tem deixa a tabela nova de fora — o mesmo defeito que a guarda de RLS ja teve. Por isso
-- uma das classes admitidas e `nenhum`, e quem cria tabela e obrigado a dizer conscientemente em
-- qual caso esta.
--
-- CONVENCAO, que a guarda derivada do catalogo le e que toda migration nova precisa seguir:
--
--     comment on table public.<tabela> is
--         '<finalidade em uma frase> [retencao:<classe>]';
--
-- As classes sao as do item 10 da politica de privacidade — A a H — mais `nenhum` para tabela sem
-- dado pessoal. Prosa nao e parseavel; o marcador entre colchetes e o que a guarda le. Acrescentar
-- classe na politica sem acrescenta-la a guarda reprova a construcao, que e como os dois envelhecem
-- juntos.
-- ---------------------------------------------------------------------------
comment on table public.app_user is
    'Identidade do usuario da plataforma, derivada do provedor de autenticacao. [retencao:D]';

comment on table public.organization is
    'Unidade de isolamento de dados. Nome da organizacao nao e dado pessoal de aluno. [retencao:D]';

comment on table public.membership is
    'Vinculo N:N entre usuario e organizacao, e a fronteira de autorizacao. [retencao:D]';

comment on table public.subscription is
    'Assinatura da organizacao: plano, periodo e status, para cobranca. [retencao:D]';

comment on table public.credit_ledger is
    'Lancamentos de credito append-only da organizacao, para auditoria de consumo. [retencao:D]';

comment on table public.exam is
    'Identidade da prova. Nao contem dado pessoal de aluno; o conteudo mora no pacote. [retencao:nenhum]';

comment on table public.exam_package is
    'Artefato imutavel hasheado. I5: nunca contem dado pessoal direto. [retencao:nenhum]';

comment on table public.exam_roster is
    'Identificacao do aluno para correcao e boletim, mantida fora do artefato imutavel (ADR-0002) '
    'para que a eliminacao seja efetiva mesmo apos a distribuicao de copias. [retencao:C]';
