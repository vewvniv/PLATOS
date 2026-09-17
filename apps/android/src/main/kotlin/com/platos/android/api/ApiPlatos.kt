package com.platos.android.api

import com.platos.android.roster.AlunoDoRoster

import com.platos.android.net.CorpoCru
import com.platos.android.net.Retorno
import com.platos.android.net.retornoDe
import com.platos.android.net.retornoDeBytes
import com.platos.android.session.Organizacao
import com.platos.android.session.ProvaPublicada
import com.platos.android.session.ResultadoDasOrganizacoes
import com.platos.android.session.ResultadoDasProvas
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * A API do PLATOS, vista do aparelho.
 *
 * E o segundo destino de rede da decisao 2: a credencial nasce no Supabase Auth, e todo dado de
 * dominio vem daqui. Adaptador fino, como `AutenticacaoSupabase` — traduz, e nao decide.
 *
 * **Nem a credencial nem o 401 sao tratados aqui.** Os dois vivem em [clienteApi], que e o ponto
 * unico da decisao 3 — e existe como funcao separada para que "nenhuma tela trata 401 por conta
 * propria" seja verificavel sobre o cliente, e nao sobre a disciplina de quem escreve a proxima
 * chamada. Este arquivo so sabe a rota e a forma do contrato.
 *
 * [organizacoes] devolve `Retorno` cru de proposito: o 401 ja levou a sessao de volta a entrada
 * pelo interceptador antes de esta funcao retornar, e traduzi-lo tambem aqui seria a duplicacao que
 * a tarefa 4.4 existe para demonstrar.
 */
class ApiPlatos(
    http: HttpClient,
    private val urlBase: String,
    credencial: () -> String?,
    aoExpirarSessao: () -> Unit,
) {

    private val autenticado: HttpClient = clienteApi(http, credencial, aoExpirarSessao)

    /**
     * As organizacoes do usuario da sessao.
     *
     * A rota ja faz o provisionamento idempotente da organizacao pessoal antes de listar (D-0.4), e
     * esta fatia nao mexe nisso: do lado do aparelho, primeira abertura e abertura seguinte sao a
     * mesma chamada.
     */
    suspend fun organizacoes(): Retorno<List<Organizacao>> =
        when (val retorno = retornoDe<List<OrganizacaoDto>> { pedirOrganizacoes() }) {
            is Retorno.Respondeu -> Retorno.Respondeu(retorno.valor.map { it.paraOrganizacao() })
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirOrganizacoes(): HttpResponse =
        autenticado.get("$urlBase/me/organizations")

    /**
     * As provas publicadas de uma organizacao.
     *
     * Devolve `Retorno` cru pelo mesmo motivo de [organizacoes]: o 401 ja levou a sessao de volta a
     * entrada pelo interceptador antes de esta funcao retornar.
     */
    suspend fun provas(organizacaoId: String): Retorno<List<ProvaPublicada>> =
        when (val retorno = retornoDe<List<ProvaDto>> { pedirProvas(organizacaoId) }) {
            is Retorno.Respondeu -> Retorno.Respondeu(retorno.valor.map { it.paraProva() })
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirProvas(organizacaoId: String): HttpResponse =
        autenticado.get("$urlBase/organizations/$organizacaoId/exams")

    /**
     * O pacote de uma prova: os bytes exatos, mais o hash que o servidor declarou.
     *
     * **Nao passa por [retornoDe]**, e isso e o ponto. Aquele desserializa o corpo, e o que a
     * conferencia de integridade hasheia precisa ser o byte que chegou do fio — reserializar entre
     * receber e conferir tornaria a conferencia uma afirmacao sobre o parser, e nao sobre o
     * transporte.
     *
     * O hash vem no mesmo `Retorno` que os bytes, e nao numa segunda chamada.
     */
    suspend fun pacote(organizacaoId: String, shortId: String): Retorno<PacoteRecebido> =
        when (val retorno = retornoDeBytes(listOf(CABECALHO_DO_HASH)) { pedirPacote(organizacaoId, shortId) }) {
            is Retorno.Respondeu -> Retorno.Respondeu(retorno.valor.paraPacoteRecebido())
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirPacote(organizacaoId: String, shortId: String): HttpResponse =
        autenticado.get("$urlBase/organizations/$organizacaoId/exams/$shortId/package")

    /**
     * O roster de uma prova: para cada aluno atribuido, o token e o nome de apresentacao.
     *
     * **Passa por [retornoDe]**, ao contrario do pacote, e a assimetria tem razao: la os bytes crus
     * sao obrigatorios porque o `content_hash` foi calculado sobre eles, e reserializar entre receber
     * e conferir tornaria a conferencia uma afirmacao sobre o parser. Aqui nao ha hash a proteger —
     * ADR-0002 recusou um segundo hash sobre o roster —, entao desserializar e o caminho normal.
     *
     * **Lista vazia e resposta valida, e nao ausencia.** Prova publicada sem aluno atribuido devolve
     * `[]`, e isso e afirmacao sobre o mundo: o gate abre com ela. Quem distingue "nao ha alunos" de
     * "nunca puxei" e a presenca do arquivo no aparelho, e nao esta chamada.
     */
    suspend fun roster(organizacaoId: String, shortId: String): Retorno<List<AlunoDoRoster>> =
        when (val retorno = retornoDe<List<RosterEntryDto>> { pedirRoster(organizacaoId, shortId) }) {
            is Retorno.Respondeu -> Retorno.Respondeu(retorno.valor.map { it.paraAluno() })
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirRoster(organizacaoId: String, shortId: String): HttpResponse =
        autenticado.get("$urlBase/organizations/$organizacaoId/exams/$shortId/roster")

    /**
     * Empurra um resultado apurado. **A unica escrita que o aparelho faz.**
     *
     * **O corpo vai como texto, ja pronto, e nao como objeto para serializar aqui.** Ele foi
     * congelado no momento da apuracao e guardado assim (`ResultadoPendenteEntity.corpo`): serializar
     * no envio faria uma mudanca futura no caminho de serializacao reescrever, em silencio, notas
     * apuradas por uma versao anterior do aplicativo. O que sobe e o que foi apurado, byte a byte.
     *
     * **`Retorno<Unit>`, e nao a revisao que o servidor devolve.** O aparelho nao tem consumidor
     * para ela: confirmado e confirmado, e a revisao e a forma como o **servidor** organiza o
     * historico. Devolve-la aqui seria numero sem consumidor.
     *
     * 404 chega como [Retorno.Recusou], e o chamador **nao apaga o pendente por causa dele**: prova
     * de organizacao cujo vinculo foi revogado responde 404 — a rota nao distingue, de proposito —,
     * e outro membro da organizacao consegue enviar o mesmo pendente depois.
     */
    suspend fun enviarResultado(
        organizacaoId: String,
        shortId: String,
        corpo: String,
    ): Retorno<Unit> =
        when (val retorno = retornoDe<Unit> { pedirEnvio(organizacaoId, shortId, corpo) }) {
            is Retorno.Respondeu -> Retorno.Respondeu(Unit)
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirEnvio(
        organizacaoId: String,
        shortId: String,
        corpo: String,
    ): HttpResponse =
        autenticado.post("$urlBase/organizations/$organizacaoId/exams/$shortId/results") {
            contentType(ContentType.Application.Json)
            setBody(corpo)
        }

    companion object {
        /** Espelha `PACKAGE_CONTENT_HASH_HEADER` do servidor (ADR-0013, decisao 2). */
        const val CABECALHO_DO_HASH = "X-Package-Content-Hash"
    }
}

/**
 * O que a rota de pacote entregou: bytes, e o hash que o servidor disse que os cobre.
 *
 * [hashDeclarado] e nulo quando o cabecalho nao veio. **Nulo, e nao string vazia**: sem hash
 * declarado nao ha contra o que conferir, e tratar a ausencia como "" faria a comparacao ser feita
 * contra um valor que nunca vai bater — recusa pela razao errada, com a mensagem errada. Quem decide
 * o que fazer com a ausencia e a conferencia, e nao o transporte.
 */
data class PacoteRecebido(
    val bytes: ByteArray,
    val hashDeclarado: String?,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is PacoteRecebido && bytes.contentEquals(other.bytes) && hashDeclarado == other.hashDeclarado)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + (hashDeclarado?.hashCode() ?: 0)
}

private fun CorpoCru.paraPacoteRecebido(): PacoteRecebido =
    PacoteRecebido(bytes = bytes, hashDeclarado = cabecalhos[ApiPlatos.CABECALHO_DO_HASH])

/**
 * A traducao para o que a maquina de estados aceita, espelhando `ResultadoDaAutenticacao.paraSessao`.
 *
 * **`Recusou` vira `Falhou` sem olhar o status, 401 inclusive, e isso e deliberado.** Escrever
 * `if (status == 401)` aqui poria na traducao a regra que a decisao 3 tirou dela — o defeito que a
 * tarefa 4.4 demonstrou. Nao e preciso: quando o status e 401, o interceptador ja levou a sessao a
 * expirada **antes** desta funcao existir no tempo (`ClienteApiTest` afirma essa ordem pelo nome), e
 * a guarda da tarefa 3.8 descarta este `Falhou` porque a sessao ja saiu de `Consultando`.
 *
 * Sao tres pecas segurando uma regra: o interceptador detecta, a ordem garante quem chega primeiro,
 * e a guarda descarta o retrasado. Nenhuma delas nomeia 401 fora do interceptador.
 */
fun Retorno<List<Organizacao>>.paraSessao(): ResultadoDasOrganizacoes = when (this) {
    is Retorno.Respondeu -> ResultadoDasOrganizacoes.Chegaram(valor)
    is Retorno.Recusou -> ResultadoDasOrganizacoes.Falhou
    is Retorno.SemRede -> ResultadoDasOrganizacoes.SemRede
}

/**
 * A traducao da listagem para o vocabulario do preparo, espelhando [paraSessao].
 *
 * `Recusou` vira `Falhou` **sem olhar o status**, 401 inclusive, pela mesma razao registrada la: o
 * interceptador de `clienteApi` ja levou a sessao a expirada antes desta funcao existir no tempo, e
 * nomear 401 aqui poria na traducao a regra que a decisao 3 tirou dela.
 */
fun Retorno<List<ProvaPublicada>>.paraProvas(): ResultadoDasProvas = when (this) {
    is Retorno.Respondeu -> ResultadoDasProvas.Chegaram(valor)
    is Retorno.Recusou -> ResultadoDasProvas.Falhou
    is Retorno.SemRede -> ResultadoDasProvas.SemRede
}
