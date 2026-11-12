import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  BookOpen,
  Bot,
  ChevronRight,
  History,
  Loader2,
  MessageSquarePlus,
  Send,
  Sparkles,
  Trash2,
  User,
  X,
} from 'lucide-react';
import {
  createChatSession,
  deleteChatSession,
  getSessionMessages,
  listChatSessions,
  sendSessionMessage,
} from '../api/chatApi';
import { listDocuments } from '../api/documentApi';
import { useToast } from '../context/ToastContext';
import { formatDateTime } from '../utils/formatters';

const SUGGESTED_QUESTIONS = [
  'Summarize the key configuration steps in my uploaded docs.',
  'What authentication or security settings are described?',
  'List any troubleshooting tips or error codes documented.',
];

export default function ChatPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const messagesEndRef = useRef(null);

  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [messages, setMessages] = useState([]);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [completedDocCount, setCompletedDocCount] = useState(null);

  const [question, setQuestion] = useState('');
  const [sending, setSending] = useState(false);
  const [deletingSessionId, setDeletingSessionId] = useState(null);
  const [mobileHistoryOpen, setMobileHistoryOpen] = useState(false);

  useEffect(() => {
    let active = true;
    async function init() {
      setLoadingSessions(true);
      try {
        const [sessionList, docs] = await Promise.all([
          listChatSessions(),
          listDocuments(),
        ]);
        if (active) {
          setSessions(sessionList);
          setCompletedDocCount(
            docs.filter((d) => d.processingStatus === 'COMPLETED').length
          );
        }
      } catch (err) {
        if (active) {
          showToast(
            err.response?.data?.message || 'Failed to load chat workspace.',
            'error'
          );
        }
      } finally {
        if (active) setLoadingSessions(false);
      }
    }
    init();
    return () => {
      active = false;
    };
  }, [showToast]);

  useEffect(() => {
    let active = true;
    if (!sessionId) {
      setMessages([]);
      return;
    }

    async function loadHistory() {
      setLoadingMessages(true);
      try {
        const list = await getSessionMessages(sessionId);
        if (active) {
          setMessages(list);
        }
      } catch (err) {
        if (active) {
          showToast(
            err.response?.data?.message || 'Chat session not found.',
            'error'
          );
          navigate('/chat', { replace: true });
        }
      } finally {
        if (active) setLoadingMessages(false);
      }
    }
    loadHistory();
    return () => {
      active = false;
    };
  }, [sessionId, navigate, showToast]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const handleNewChat = async () => {
    try {
      const created = await createChatSession();
      setSessions((prev) => [created, ...prev]);
      setMobileHistoryOpen(false);
      navigate(`/chat/${created.id}`);
    } catch (err) {
      showToast(err.response?.data?.message || 'Could not create chat session.', 'error');
    }
  };

  const handleDeleteSession = async (e, id) => {
    e.stopPropagation();
    setDeletingSessionId(id);
    try {
      await deleteChatSession(id);
      setSessions((prev) => prev.filter((s) => s.id !== id));
      if (sessionId === id) {
        setMessages([]);
        navigate('/chat', { replace: true });
      }
      showToast('Chat session deleted.', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete session.', 'error');
    } finally {
      setDeletingSessionId(null);
    }
  };

  const handleSend = async (e, overrideText) => {
    if (e) e.preventDefault();
    const promptText = (overrideText ?? question).trim();
    if (!promptText) {
      showToast('Please enter a question.', 'error');
      return;
    }
    if (promptText.length > 2000) {
      showToast('Question must not exceed 2000 characters.', 'error');
      return;
    }

    setSending(true);
    let targetSessionId = sessionId;

    try {
      if (!targetSessionId) {
        const created = await createChatSession();
        targetSessionId = created.id;
        setSessions((prev) => [created, ...prev]);
        navigate(`/chat/${targetSessionId}`, { replace: true });
      }

      const optimisticUserMsg = {
        id: `temp-${Date.now()}`,
        sessionId: targetSessionId,
        role: 'USER',
        content: promptText,
        grounded: null,
        sources: [],
        createdAt: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, optimisticUserMsg]);
      if (!overrideText) setQuestion('');

      const result = await sendSessionMessage(targetSessionId, promptText);
      setMessages((prev) => [
        ...prev.filter((m) => m.id !== optimisticUserMsg.id),
        result.userMessage,
        result.assistantMessage,
      ]);
      setSessions((prev) => {
        const others = prev.filter((s) => s.id !== result.session.id);
        return [result.session, ...others];
      });
    } catch (err) {
      setMessages((prev) => prev.filter((m) => !String(m.id).startsWith('temp-')));
      showToast(
        err.response?.data?.message || 'Failed to get an AI response. Please try again.',
        'error'
      );
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!sending && question.trim()) {
        handleSend();
      }
    }
  };

  const activeSession = sessions.find((s) => s.id === sessionId);

  const SidebarContent = () => (
    <div className="flex flex-col h-full">
      <div className="p-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={handleNewChat}
          className="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 px-3.5 py-2 text-xs font-medium text-white transition cursor-pointer"
        >
          <MessageSquarePlus className="w-4 h-4" />
          New Chat
        </button>
        <button
          type="button"
          onClick={() => setMobileHistoryOpen(false)}
          className="md:hidden p-1.5 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
          aria-label="Close chat history"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-2.5 space-y-1">
        {loadingSessions ? (
          <div className="py-8 flex items-center justify-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            Loading history...
          </div>
        ) : sessions.length === 0 ? (
          <div className="px-3 py-8 text-center">
            <History className="w-6 h-6 text-slate-400 dark:text-slate-600 mx-auto mb-2" />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              No saved chats yet. Ask a question to begin.
            </p>
          </div>
        ) : (
          sessions.map((s) => {
            const isSelected = s.id === sessionId;
            return (
              <div
                key={s.id}
                onClick={() => {
                  setMobileHistoryOpen(false);
                  navigate(`/chat/${s.id}`);
                }}
                className={`group flex items-start justify-between gap-2 rounded-xl px-3 py-2.5 text-left transition cursor-pointer ${
                  isSelected
                    ? 'bg-indigo-600/10 border border-indigo-500/30 text-indigo-700 dark:text-indigo-300'
                    : 'hover:bg-slate-100 dark:hover:bg-slate-800/70 border border-transparent text-slate-700 dark:text-slate-300'
                }`}
              >
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium truncate">{s.title}</p>
                  <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                    {s.messageCount} msg{s.messageCount === 1 ? '' : 's'} &middot;{' '}
                    {formatDateTime(s.updatedAt)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={(e) => handleDeleteSession(e, s.id)}
                  disabled={deletingSessionId === s.id}
                  className="opacity-70 sm:opacity-0 sm:group-hover:opacity-100 p-1 rounded-lg text-slate-400 hover:text-rose-500 hover:bg-rose-500/10 transition cursor-pointer shrink-0"
                  aria-label="Delete session"
                >
                  {deletingSessionId === s.id ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <Trash2 className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>
            );
          })
        )}
      </div>
    </div>
  );

  return (
    <div className="h-[calc(100vh-3.5rem)] lg:h-screen flex overflow-hidden">
      {/* Desktop Chat History Sidebar */}
      <aside className="hidden md:flex md:w-72 md:flex-col border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/70">
        <SidebarContent />
      </aside>

      {/* Mobile Chat History Drawer */}
      {mobileHistoryOpen && (
        <div className="fixed inset-0 z-40 md:hidden flex">
          <div
            className="fixed inset-0 bg-slate-950/60"
            onClick={() => setMobileHistoryOpen(false)}
          />
          <aside className="relative z-50 w-72 max-w-full bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800">
            <SidebarContent />
          </aside>
        </div>
      )}

      {/* Main Conversation Pane */}
      <div className="flex-1 flex flex-col min-w-0 bg-slate-50 dark:bg-slate-950">
        {/* Top Chat Bar */}
        <div className="px-5 py-3.5 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/50 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <button
              type="button"
              onClick={() => setMobileHistoryOpen(true)}
              className="md:hidden inline-flex items-center gap-1.5 rounded-lg border border-slate-300 dark:border-slate-700 px-2.5 py-1.5 text-xs font-medium cursor-pointer"
            >
              <History className="w-3.5 h-3.5" />
              Chats
            </button>
            <h1 className="text-sm font-semibold truncate">
              {activeSession ? activeSession.title : 'New Conversation'}
            </h1>
          </div>

          <span className="text-[11px] text-slate-500 dark:text-slate-400 hidden sm:inline">
            Grounded in your uploaded documentation
          </span>
        </div>

        {/* Missing docs warning banner */}
        {completedDocCount === 0 && (
          <div className="mx-5 mt-4 rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-xs text-amber-700 dark:text-amber-300 flex items-center justify-between gap-3">
            <span className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              You have no processed documents yet. Upload a PDF, DOCX, or TXT so DevDocs AI can cite your files.
            </span>
            <Link
              to="/documents"
              className="inline-flex items-center gap-1 font-semibold underline shrink-0"
            >
              Upload
              <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        )}

        {/* Message Thread */}
        <div className="flex-1 overflow-y-auto px-5 py-6 space-y-5">
          {loadingMessages ? (
            <div className="h-full flex items-center justify-center gap-2 text-sm text-slate-500 dark:text-slate-400">
              <Loader2 className="w-4 h-4 animate-spin text-indigo-500" />
              Loading conversation...
            </div>
          ) : messages.length === 0 ? (
            <div className="max-w-2xl mx-auto py-12 text-center space-y-6">
              <div className="inline-flex p-3 rounded-2xl bg-indigo-500/10 text-indigo-600 dark:text-indigo-400">
                <Sparkles className="w-7 h-7" />
              </div>
              <div>
                <h2 className="text-lg font-semibold">Ask your documentation anything</h2>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  DevDocs AI searches your uploaded files using pgvector and answers strictly from retrieved passages with citations.
                </p>
              </div>

              <div className="grid gap-2.5 text-left">
                {SUGGESTED_QUESTIONS.map((sample) => (
                  <button
                    key={sample}
                    type="button"
                    onClick={() => handleSend(null, sample)}
                    disabled={sending}
                    className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-indigo-500/50 px-4 py-3 text-xs font-medium text-slate-700 dark:text-slate-300 transition flex items-center justify-between gap-2 cursor-pointer"
                  >
                    <span>{sample}</span>
                    <ChevronRight className="w-4 h-4 text-slate-400 shrink-0" />
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="max-w-3xl mx-auto space-y-5">
              {messages.map((msg) => {
                const isUser = msg.role === 'USER';
                return (
                  <div
                    key={msg.id}
                    className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}
                  >
                    {!isUser && (
                      <div className="w-8 h-8 rounded-xl bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0 mt-0.5">
                        <Bot className="w-4 h-4" />
                      </div>
                    )}

                    <div
                      className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm ${
                        isUser
                          ? 'bg-indigo-600 text-white'
                          : 'border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-200 shadow-xs'
                      }`}
                    >
                      {!isUser && (
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <span className="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400">
                            DevDocs AI
                          </span>
                          <span
                            className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${
                              msg.grounded
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30'
                                : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30'
                            }`}
                          >
                            {msg.grounded ? 'Grounded in Docs' : 'Insufficient Context'}
                          </span>
                        </div>
                      )}

                      <p className="whitespace-pre-wrap leading-relaxed">{msg.content}</p>

                      {!isUser && msg.sources && msg.sources.length > 0 && (
                        <div className="mt-4 pt-3 border-t border-slate-200 dark:border-slate-800 space-y-2">
                          <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
                            <BookOpen className="w-3.5 h-3.5" />
                            Sources ({msg.sources.length})
                          </p>
                          <div className="grid gap-2">
                            {msg.sources.map((src, idx) => (
                              <div
                                key={`${src.documentId}-${src.pageNumber}-${src.chunkIndex}-${idx}`}
                                className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/70 p-2.5 text-xs"
                              >
                                <div className="flex items-center justify-between gap-2 font-medium text-slate-800 dark:text-slate-200">
                                  <span className="truncate">
                                    [Source {idx + 1}] {src.documentName}
                                  </span>
                                  <span className="text-[11px] text-slate-500 dark:text-slate-400 font-normal shrink-0">
                                    {src.pageNumber != null ? `Page ${src.pageNumber}` : ''}
                                    {src.chunkIndex != null ? ` · Chunk ${src.chunkIndex}` : ''}
                                  </span>
                                </div>
                                <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1 line-clamp-2">
                                  {src.excerpt}
                                </p>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    {isUser && (
                      <div className="w-8 h-8 rounded-xl bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 flex items-center justify-center shrink-0 mt-0.5">
                        <User className="w-4 h-4" />
                      </div>
                    )}
                  </div>
                );
              })}

              {sending && (
                <div className="flex gap-3 justify-start">
                  <div className="w-8 h-8 rounded-xl bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0">
                    <Bot className="w-4 h-4" />
                  </div>
                  <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 py-3 text-xs text-slate-500 dark:text-slate-400 flex items-center gap-2">
                    <Loader2 className="w-3.5 h-3.5 animate-spin text-indigo-500" />
                    Searching your documentation & generating a grounded answer...
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* Prompt Composer */}
        <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/70">
          <form onSubmit={handleSend} className="max-w-3xl mx-auto flex gap-2.5 items-end">
            <div className="flex-1">
              <textarea
                rows={1}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={handleKeyDown}
                maxLength={2000}
                placeholder="Ask a question about your uploaded documentation... (Enter to send)"
                className="w-full resize-none rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 px-4 py-2.5 text-sm placeholder-slate-400 dark:placeholder-slate-500 focus:border-indigo-500 focus:outline-none"
              />
            </div>
            <button
              type="submit"
              disabled={sending || !question.trim()}
              className="inline-flex items-center justify-center gap-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 px-4 py-2.5 text-sm font-medium text-white transition cursor-pointer shrink-0"
            >
              {sending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
              Send
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
