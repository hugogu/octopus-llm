'use client';

import { classifyFence } from '@/lib/markdown/blocks';
import CodeBlock from './CodeBlock';
import BlockViewToggle from './BlockViewToggle';
import MermaidPreview from './MermaidPreview';
import PlantUmlPreview from './PlantUmlPreview';
import SvgPreview from './SvgPreview';
import RunnableArtifact from './RunnableArtifact';

/**
 * Dispatcher for a single fenced code block. Classifies the fence language and routes to the right
 * render strategy (bounded code, diagram/markup preview, or runnable HTML). This is the shared seam
 * used by both the in-app conversation and the public share view, so parity is structural.
 */
export default function FencedBlock({ language, source }: { language: string; source: string }) {
  const { strategy, diagram } = classifyFence(language);

  if (strategy === 'diagram-preview' && diagram) {
    const preview =
      diagram === 'mermaid' ? (
        <MermaidPreview code={source} />
      ) : diagram === 'plantuml' ? (
        <PlantUmlPreview code={source} />
      ) : (
        <SvgPreview code={source} />
      );
    return (
      <BlockViewToggle
        source={source}
        language={language}
        initialView="preview"
        previewLabel="Diagram"
        preview={preview}
      />
    );
  }

  // HTML defaults to source (Q2) and runs in an isolated sandbox only when the user switches to the
  // "Run" view — the explicit run action (FR-010). The iframe mounts lazily with the view.
  if (strategy === 'html-runnable') {
    return (
      <BlockViewToggle
        source={source}
        language={language}
        initialView="source"
        previewLabel="Run"
        preview={<RunnableArtifact html={source} />}
      />
    );
  }

  return <CodeBlock code={source} language={language} />;
}
