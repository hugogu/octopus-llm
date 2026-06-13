import SharedConversation from "@/components/share/SharedConversation";

export default async function SharedConversationPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <SharedConversation shareToken={token} />;
}
