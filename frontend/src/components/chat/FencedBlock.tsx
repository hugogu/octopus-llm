'use client';

import { classifyFence } from '@/lib/markdown/blocks';
import CodeBlock from './CodeBlock';
import BlockViewToggle from './BlockViewToggle';
import MermaidPreview from './MermaidPreview';
import PlantUmlPreview from './PlantUmlPreview';
import SvgPreview from './SvgPreview';

/**
 * Dispatcher for a single fenced code block. Classifies the fence language and routes to the right
 * render strategy (bounded code, diagram/markup preview, or HTML). This is the shared seam used by both
 * the in-app conversation and the public share view, so parity is structural.
 *
 * HTML currently renders as bounded source (clarification Q2 default); the explicit "Run" sandbox is
 * added by User Story 3.
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

  // `code` and `html-runnable` both render as bounded, copyable source in this pass.
  return <CodeBlock code={source} language={language} />;
}
