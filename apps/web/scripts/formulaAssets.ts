import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Resolve as referencias de formula do `LayoutMap` nos bytes do raster (D-1.5.5).
 *
 * Um caminho unico, e nao cada consumidor procurando o arquivo do seu jeito: sao **os mesmos
 * bytes** que o renderizador Android recebe dos assets, e e disso que a paridade da formula
 * depende inteiramente. O manifesto e a fonte da verdade sobre qual arquivo pertence a qual
 * referencia — nao ha convencao de nome implicita no meio.
 */
export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

interface ManifestEntry {
  id: string;
  raster: string;
  sha256: string;
  width_um: number;
  height_um: number;
}

export interface FormulaManifest {
  raster_dpi: number;
  body_size_um: number;
  formulas: ManifestEntry[];
}

export async function loadFormulaManifest(): Promise<FormulaManifest> {
  return JSON.parse(await readFile(resolve(repoRoot, 'fixtures/formulas.manifest.json'), 'utf8'));
}

/** Referencia declarada no mapa -> bytes do PNG versionado. */
export async function loadFormulaRasters(): Promise<Map<string, Uint8Array>> {
  const manifest = await loadFormulaManifest();
  const rasters = new Map<string, Uint8Array>();
  for (const formula of manifest.formulas) {
    rasters.set(
      formula.id,
      new Uint8Array(await readFile(resolve(repoRoot, 'fixtures', formula.raster))),
    );
  }
  return rasters;
}

export async function loadFontBytes(): Promise<Uint8Array> {
  return new Uint8Array(
    await readFile(resolve(repoRoot, 'packages/domain/fonts/SourceSerif4-Regular.ttf')),
  );
}
