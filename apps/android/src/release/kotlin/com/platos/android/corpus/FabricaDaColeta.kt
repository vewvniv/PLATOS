package com.platos.android.corpus

import java.io.File

/**
 * A coleta do aplicativo de release: nenhuma. O codigo que copia resposta para o corpus **nao existe** neste APK
 * (`slice-5d-corpus-de-medicao`; verificado por `verificarApkSemColeta`).
 */
@Suppress("UNUSED_PARAMETER")
fun coletaDoCorpus(filesDir: File): ColetaDoCorpus = SemColeta
