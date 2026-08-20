package com.platos.domain.exam

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
 * [assignments] vem de fora porque atribuir aluno a variante e decisao de quem publica, e porque
 * **o token e a unica coisa de aluno que entra no pacote** (ADR-0002, I5). O nome vive no roster.
 */
fun ExamDefinition.buildPackage(
    profile: LayoutProfile = LayoutProfile.DEFAULT,
    variantId: String = DEFAULT_VARIANT,
    assignments: List<PackageAssignment> = emptyList(),
): ExamPackage {
    val map = LayoutEngine(profile = profile).layout(this)

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
