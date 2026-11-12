import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CheckCircle2,
  Database,
  FileText,
  FolderPlus,
  Loader2,
  MessageSquare,
  MessagesSquare,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { listDocuments } from '../api/documentApi';
import { listChatSessions } from '../api/chatApi';
import { useToast } from '../context/ToastContext';
import { formatBytes, formatDateTime } from '../utils/formatters';

export default function DashboardPage() {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [documents, setDocuments] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function loadOverview() {
      setLoading(true);
      try {
        const [docs, chats] = await Promise.all([listDocuments(), listChatSessions()]);
        if (active) {
          setDocuments(docs);
          setSessions(chats);
        }
      } catch (err) {
        if (active) {
          showToast(err.response?.data?.message || 'Failed to load dashboard summary.', 'error');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    loadOverview();
    return () => {
      active = false;
    };
  }, [showToast]);

  const completedDocs = documents.filter((d) => d.processingStatus === 'COMPLETED');
  const totalChunks = completedDocs.reduce((sum, d) => sum + (d.chunkCount || 0), 0);

  return (
    <div className="max-w-5xl mx-auto px-6 py-8 space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Welcome back, {user?.name?.split(' ')[0] || 'Developer'}
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Manage your private documentation library and ask grounded AI questions.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Link
            to="/documents"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 hover:bg-slate-100 dark:hover:bg-slate-800 px-4 py-2 text-xs font-medium transition"
          >
            <FolderPlus className="w-4 h-4 text-indigo-500" />
            Upload Docs
          </Link>
          <Link
            to="/chat"
            className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 px-4 py-2 text-xs font-medium text-white transition"
          >
            <MessageSquare className="w-4 h-4" />
            Ask AI
          </Link>
        </div>
      </div>

      {/* Metrics */}
      <div className="grid sm:grid-cols-3 gap-4">
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-medium">
            <span>Indexed Documents</span>
            <FileText className="w-4 h-4 text-indigo-500" />
          </div>
          <p className="text-2xl font-semibold mt-2">
            {loading ? '—' : completedDocs.length}
          </p>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            {documents.length} total uploaded
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-medium">
            <span>Vector Chunks</span>
            <Database className="w-4 h-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-semibold mt-2">
            {loading ? '—' : totalChunks}
          </p>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            768-dim pgvector embeddings
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-medium">
            <span>Chat Sessions</span>
            <MessagesSquare className="w-4 h-4 text-indigo-500" />
          </div>
          <p className="text-2xl font-semibold mt-2">
            {loading ? '—' : sessions.length}
          </p>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Private multi-turn conversations
          </p>
        </div>
      </div>

      {loading ? (
        <div className="py-12 flex items-center justify-center gap-2 text-sm text-slate-500 dark:text-slate-400">
          <Loader2 className="w-4 h-4 animate-spin text-indigo-500" />
          Loading workspace summary...
        </div>
      ) : (
        <div className="grid lg:grid-cols-2 gap-6">
          {/* Recent Documents */}
          <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-semibold">Recent Documents</h2>
              <Link
                to="/documents"
                className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                Manage all
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            {documents.length === 0 ? (
              <div className="py-8 text-center border border-dashed border-slate-300 dark:border-slate-800 rounded-xl">
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  No documents uploaded yet.
                </p>
                <Link
                  to="/documents"
                  className="inline-block mt-2 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
                >
                  Upload your first file
                </Link>
              </div>
            ) : (
              <div className="divide-y divide-slate-200 dark:divide-slate-800">
                {documents.slice(0, 5).map((doc) => (
                  <div key={doc.id} className="py-3 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-medium truncate">{doc.title}</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                        {doc.fileType} &middot; {formatBytes(doc.fileSize)}
                        {doc.chunkCount ? ` · ${doc.chunkCount} chunks` : ''}
                      </p>
                    </div>
                    <span className="inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full border border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 shrink-0">
                      {doc.processingStatus === 'COMPLETED' && (
                        <CheckCircle2 className="w-3 h-3" />
                      )}
                      {doc.processingStatus}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Recent Conversations */}
          <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-semibold">Recent Conversations</h2>
              <Link
                to="/chat"
                className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                Open chat
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            {sessions.length === 0 ? (
              <div className="py-8 text-center border border-dashed border-slate-300 dark:border-slate-800 rounded-xl">
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  No chat sessions yet.
                </p>
                <Link
                  to="/chat"
                  className="inline-block mt-2 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
                >
                  Start asking questions
                </Link>
              </div>
            ) : (
              <div className="divide-y divide-slate-200 dark:divide-slate-800">
                {sessions.slice(0, 5).map((session) => (
                  <Link
                    key={session.id}
                    to={`/chat/${session.id}`}
                    className="py-3 flex items-center justify-between gap-3 hover:bg-slate-50 dark:hover:bg-slate-800/40 -mx-2 px-2 rounded-lg transition"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium truncate">{session.title}</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {session.messageCount} messages &middot; {formatDateTime(session.updatedAt)}
                      </p>
                    </div>
                    <ArrowRight className="w-4 h-4 text-slate-400 shrink-0" />
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
