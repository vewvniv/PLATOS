package com.platos.domain.exam

import com.platos.domain.capture.QrEncoder
import com.platos.domain.capture.linhasDeModulo
import com.platos.domain.layout.LayoutEngine
import com.platos.domain.layout.LayoutProfile

/**
 * Monta o `ExamPackage` a partir da definicao versionada.
 *
 * Funcao pura: nao le banco, nao le relogio, nao sorteia. E o que permite publicar duas vezes a
 * mesma prova e obter o mesmo hash — a propriedade que o requisito pede — e o que permite ao
 * dispositivo, na fatia 4, remontar e conferir o que recebeu.
 *
 * Quem grava e o servidor (D-2a.1). Aqui so se monta.
 *
 * [tokens] vem de fora porque atribuir aluno a variante e decisao de quem publica, e porque **o
 * token e a unica coisa de aluno que entra no pacote** (ADR-0002, I5). O nome vive no roster.
 *
 * **Sao tokens, e nao atribuicoes prontas, de proposito.** O payload do QR e produzido aqui, uma vez
 * por atribuicao, pelo mesmo escritor que a folha da variante usa. Aceitar atribuicao com QR ja
 * montado deixaria o chamador fabricar payload — dois escritores, que e o que a fatia 2b fechou.
 */
fun ExamDefinition.buildPackage(
    profile: LayoutProfile = LayoutProfile.DEFAULT,
    variantId: String = DEFAULT_VARIANT,
    tokens: List<String> = emptyList(),
): ExamPackage {
    val map = LayoutEngine(profile = profile).layout(this)

    // Uma atribuicao por token, e um QR por regiao do layout, resolvidos aqui (D23). A geometria nao
    // se repete: ela fica em `layout[variantId]`, e a folha do aluno e ela com estes QRs no lugar dos
    // da variante. As regioes vem do mapa, e nao de uma contagem de discursivas: o conjunto de regioes
    // de uma folha e o que o layout declara, e nao se declara uma segunda vez (spec de `exam-package`).
    val regioes = map.regions.sortedBy { it.index }
    val assignments = tokens.map { token ->
        PackageAssignment(
            studentToken = token,
            variantId = variantId,
            qrs = regioes.map { regiao ->
                val payload = LayoutEngine.qrPayloadDaAtribuicao(
                    examId = id,
                    studentToken = token,
                    variant = variantId,
                    regiao = regiao,
                )
                RegionQr(
                    regionIndex = regiao.index,
                    payload = payload,
                    modules = linhasDeModulo(QrEncoder.encode(payload)),
                )
            },
        )
    }

    val items = questions.map { question ->
        PackageItem(
            id = question.id,
            statement = question.statement,
            options = question.options,
            skills = question.skills,
            assets = buildList {
                question.formula?.let { add(it.reference) }
                question.inline.forEach { add(it.reference) }
            },
            kind = question.kind,
            rubric = question.rubric,
            // Na discursiva o modo sai resolvido: cinza quando a questao nao declara (§8). Na objetiva
            // fica nulo — `requireSupported` ja recusou objetiva que o declarasse.
            answerCaptureMode = when (question.kind) {
                QuestionKind.ESSAY -> question.answerCaptureMode ?: AnswerCaptureMode.GRAY
                QuestionKind.OBJECTIVE -> null
            },
        )
    }

    // A letra e COMPUTADA do texto declarado. Se a alternativa correta nao estiver entre as
    // opcoes, ou estiver duas vezes, a publicacao falha aqui — antes de existir pacote.
    val answerKey = questions.mapNotNull { question ->
        val answer = question.answer ?: return@mapNotNull null
        val ocorrencias = question.options.count { it == answer }
        if (ocorrencias != 1) {
            throw ExamPackageException(
                "questao `${question.id}` declara a resposta `$answer`, que aparece $ocorrencias " +
                    "vezes entre as alternativas ${question.options}; precisa aparecer exatamente uma",
            )
        }
        AnswerKeyEntry(
            itemId = question.id,
            correct = LETRAS[question.options.indexOf(answer)].toString(),
            points = question.points,
        )
    }

    val pacote = ExamPackage(
        meta = PackageMeta(
            examId = id,
            layoutEngineVersion = map.layoutEngineVersion,
            minRendererVersion = map.minRendererVersion,
            // Prova objetiva sem discursiva: corrigivel inteiramente no dispositivo (§15, fatia 3).
            fullyOfflineGradable = questions.all { it.kind == QuestionKind.OBJECTIVE },
        ),
        items = items,
        variants = listOf(
            // Uma variante nesta fatia. Randomizacao e a 7; o contrato ja e plural porque §5 o exige,
            // e mudar de singular para plural depois seria mexer em artefato ja publicado.
            PackageVariant(
                variantId = variantId,
                positions = questions.withIndex().associate { (index, q) -> "${index + 1}" to q.id },
            ),
        ),
        assignments = assignments,
        layout = mapOf(variantId to map),
        answerKey = answerKey,
        scoring = Scoring(maxScore = questions.sumOf { it.points }),
    )

    pacote.requireCoherent()
    return pacote
}

const val DEFAULT_VARIANT = "v1"

private const val LETRAS = "ABCDE"
