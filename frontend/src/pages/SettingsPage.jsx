import { Cpu, LogOut, Mail, Moon, ShieldCheck, Sun, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { formatDate } from '../utils/formatters';

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();

  return (
    <div className="max-w-4xl mx-auto px-6 py-8 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Account Settings</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
          Manage your profile, appearance preferences, and session security.
        </p>
      </div>

      {/* Profile Information */}
      <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-4">
        <div className="flex items-center gap-2.5">
          <User className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
          <h2 className="text-base font-semibold">Profile Details</h2>
        </div>

        <div className="grid sm:grid-cols-2 gap-4 pt-1">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60 p-4">
            <span className="text-xs text-slate-500 dark:text-slate-400">Full Name</span>
            <p className="text-sm font-medium mt-1">{user?.name || '—'}</p>
          </div>
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60 p-4">
            <span className="text-xs text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
              <Mail className="w-3.5 h-3.5" />
              Email Address
            </span>
            <p className="text-sm font-medium mt-1">{user?.email || '—'}</p>
          </div>
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60 p-4">
            <span className="text-xs text-slate-500 dark:text-slate-400">User ID</span>
            <p className="text-xs font-mono mt-1.5 text-slate-600 dark:text-slate-300 break-all">
              {user?.id || '—'}
            </p>
          </div>
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60 p-4">
            <span className="text-xs text-slate-500 dark:text-slate-400">Member Since</span>
            <p className="text-sm font-medium mt-1">{formatDate(user?.createdAt)}</p>
          </div>
        </div>
      </section>

      {/* Appearance */}
      <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-4">
        <div>
          <h2 className="text-base font-semibold">Appearance</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Choose between dark and light interface themes.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => setTheme('dark')}
            className={`flex items-center gap-3 rounded-xl border p-4 text-left transition cursor-pointer ${
              theme === 'dark'
                ? 'border-indigo-500 bg-indigo-500/10 text-indigo-600 dark:text-indigo-300'
                : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/60'
            }`}
          >
            <Moon className="w-5 h-5" />
            <div>
              <p className="text-sm font-medium">Dark Mode</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Low-glare workspace for developers
              </p>
            </div>
          </button>

          <button
            type="button"
            onClick={() => setTheme('light')}
            className={`flex items-center gap-3 rounded-xl border p-4 text-left transition cursor-pointer ${
              theme === 'light'
                ? 'border-indigo-500 bg-indigo-500/10 text-indigo-600 dark:text-indigo-300'
                : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/60'
            }`}
          >
            <Sun className="w-5 h-5" />
            <div>
              <p className="text-sm font-medium">Light Mode</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                High-contrast daylight workspace
              </p>
            </div>
          </button>
        </div>
      </section>

      {/* AI & Security Overview */}
      <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-4">
        <div className="flex items-center gap-2.5">
          <Cpu className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
          <h2 className="text-base font-semibold">AI & Data Isolation</h2>
        </div>

        <div className="grid sm:grid-cols-2 gap-4 text-xs text-slate-600 dark:text-slate-300">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 p-4 space-y-1">
            <p className="font-semibold text-slate-900 dark:text-slate-100">RAG Pipeline</p>
            <p>Chat Model: Gemini 3.5 Flash Lite</p>
            <p>Embeddings: gemini-embedding-001 (768 dims, pgvector HNSW)</p>
          </div>
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 p-4 space-y-1">
            <p className="font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-500" />
              Tenant Isolation
            </p>
            <p>All documents, vector chunks, and chat histories are scoped strictly to your JWT user ID.</p>
          </div>
        </div>

        <div className="pt-2 flex justify-end">
          <button
            type="button"
            onClick={logout}
            className="inline-flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-500/10 hover:bg-rose-500/20 px-4 py-2 text-xs font-medium text-rose-600 dark:text-rose-300 transition cursor-pointer"
          >
            <LogOut className="w-3.5 h-3.5" />
            Sign Out of DevDocs AI
          </button>
        </div>
      </section>
    </div>
  );
}
