'use client';

import React, { useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export default function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  const components = useMemo(() => ({
    code({ node, inline, className: codeClassName, children, ...props }: any) {
      const match = /language-(\w+)/.exec(codeClassName || '');
      const language = match ? match[1] : '';
      
      if (!inline && language) {
        return (
          <div className="rounded-lg overflow-hidden my-3">
            <div className="bg-gray-800 px-4 py-2 text-xs text-gray-400 flex items-center justify-between">
              <span>{language}</span>
            </div>
            <SyntaxHighlighter
              language={language}
              style={oneDark}
              PreTag="div"
              {...props}
            >
              {String(children).replace(/\n$/, '')}
            </SyntaxHighlighter>
          </div>
        );
      }
      
      return (
        <code className="bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded text-sm font-mono text-pink-600 dark:text-pink-400" {...props}>
          {children}
        </code>
      );
    },
    table({ children }: any) {
      return (
        <div className="overflow-x-auto my-4">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700 border border-gray-200 dark:border-gray-700 rounded-lg">
            {children}
          </table>
        </div>
      );
    },
    thead({ children }: any) {
      return <thead className="bg-gray-50 dark:bg-gray-800">{children}</thead>;
    },
    th({ children }: any) {
      return (
        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
          {children}
        </th>
      );
    },
    td({ children }: any) {
      return (
        <td className="px-4 py-3 text-sm text-gray-900 dark:text-gray-100 border-t border-gray-200 dark:border-gray-700">
          {children}
        </td>
      );
    },
    tr({ children }: any) {
      return <tr className="hover:bg-gray-50 dark:hover:bg-gray-800/50">{children}</tr>;
    },
    h1({ children }: any) {
      return <h1 className="text-2xl font-bold mt-6 mb-4 text-gray-900 dark:text-gray-100">{children}</h1>;
    },
    h2({ children }: any) {
      return <h2 className="text-xl font-bold mt-5 mb-3 text-gray-900 dark:text-gray-100">{children}</h2>;
    },
    h3({ children }: any) {
      return <h3 className="text-lg font-semibold mt-4 mb-2 text-gray-900 dark:text-gray-100">{children}</h3>;
    },
    p({ children }: any) {
      return <p className="mb-3 leading-relaxed text-gray-700 dark:text-gray-300">{children}</p>;
    },
    ul({ children }: any) {
      return <ul className="list-disc list-inside mb-3 space-y-1 text-gray-700 dark:text-gray-300">{children}</ul>;
    },
    ol({ children }: any) {
      return <ol className="list-decimal list-inside mb-3 space-y-1 text-gray-700 dark:text-gray-300">{children}</ol>;
    },
    li({ children }: any) {
      return <li className="ml-4">{children}</li>;
    },
    blockquote({ children }: any) {
      return (
        <blockquote className="border-l-4 border-blue-500 pl-4 my-4 italic text-gray-600 dark:text-gray-400">
          {children}
        </blockquote>
      );
    },
    a({ children, href }: any) {
      return (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 dark:text-blue-400 hover:underline"
        >
          {children}
        </a>
      );
    },
    hr() {
      return <hr className="my-6 border-gray-200 dark:border-gray-700" />;
    },
  }), []);

  return (
    <div className={`prose dark:prose-invert max-w-none ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize]}
        components={components}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
