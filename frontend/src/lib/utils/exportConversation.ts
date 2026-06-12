import type { GetSessionResponseV2 } from '@/lib/types/api';

/**
 * Renders a chat session (all turns and per-model responses) as a single
 * Markdown document suitable for download or sharing.
 */
export function conversationToMarkdown(
  session: GetSessionResponseV2,
): string {
  const lines: string[] = [`# ${session.title ?? 'Conversation'}`, ''];

  for (const turn of session.turns) {
    lines.push(`## You`, '', turn.promptText, '');
    for (const response of turn.responses) {
      const name = response.connectionLabel
        ? `${response.modelDisplayName} (${response.connectionLabel})`
        : response.modelDisplayName;
      lines.push(`### ${name}`, '');
      if (response.status === 'error') {
        lines.push(`> ⚠️ Error: ${response.errorMessage ?? 'Unknown error'}`, '');
        continue;
      }
      if (response.reasoningText) {
        lines.push(
          '<details>',
          '<summary>Thought process</summary>',
          '',
          response.reasoningText,
          '',
          '</details>',
          '',
        );
      }
      lines.push(response.responseText ?? '', '');
    }
  }

  return lines.join('\n');
}

/** Sanitizes a session title into a safe markdown filename. */
export function conversationFilename(title: string | null): string {
  const base = (title ?? 'conversation')
    .trim()
    .replace(/[\\/:*?"<>|\s]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
  return `${base || 'conversation'}.md`;
}

/** Triggers a browser download of the given text as a file. */
export function downloadTextFile(filename: string, text: string): void {
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
