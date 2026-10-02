package com.platos.android.corpus

import java.io.File

/** A coleta do aplicativo de depuracao: a real. O release tem a sua em `src/release`, que devolve [SemColeta]. */
fun coletaDoCorpus(filesDir: File): ColetaDoCorpus = ColetaDoCorpusEmArquivo(filesDir)
