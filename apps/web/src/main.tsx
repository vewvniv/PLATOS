import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { renderLayoutMap } from './renderer.js';
import type { LayoutMap } from './layoutMap.js';

/**
 * UI minima da fatia 1: carregar um `LayoutMap`, desenhar, baixar o PDF.
 *
 * Sem navegacao, sem estado compartilhado e sem rede. A autoria e o preview ao vivo pertencem a
 * fatias posteriores; aqui a web existe para provar que o mesmo mapa vira papel.
 */
function App() {
  const [status, setStatus] = useState<string>('Selecione um LayoutMap em JSON.');

  async function handleFile(file: File) {
    try {
      setStatus('Desenhando…');
      const map: LayoutMap = JSON.parse(await file.text());
      const fontResponse = await fetch('/SourceSerif4-Regular.ttf');
      const fontBytes = new Uint8Array(await fontResponse.arrayBuffer());

      const pdf = await renderLayoutMap(map, fontBytes);
      const url = URL.createObjectURL(
        new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }),
      );
      const link = document.createElement('a');
      link.href = url;
      link.download = `${map.exam_id}.pdf`;
      link.click();
      URL.revokeObjectURL(url);

      setStatus(`Pronto: ${map.pages.length} pagina(s) de ${map.exam_id}.`);
    } catch (error) {
      setStatus(`Falhou: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '40rem' }}>
      <h1>Renderizador de prova</h1>
      <input
        type="file"
        accept="application/json"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) void handleFile(file);
        }}
      />
      <p>{status}</p>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
