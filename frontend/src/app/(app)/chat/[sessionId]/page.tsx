import { redirect } from "next/navigation";

interface PageProps {
  params: Promise<{ sessionId: string }>;
}

export default async function SessionPageRedirect({ params }: PageProps) {
  const { sessionId } = await params;
  redirect(`/chat?session=${encodeURIComponent(sessionId)}`);
}
