import { useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle2,
  Clock,
  FileText,
  FileUp,
  Loader2,
  Search,
  Trash2,
  Upload,
  XCircle,
} from 'lucide-react';
import { deleteDocument, listDocuments, uploadDocument } from '../api/documentApi';
import { useToast } from '../context/ToastContext';
import { formatBytes, formatDateTime } from '../utils/formatters';

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.txt'];
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

const STATUS_BADGE = {
  COMPLETED:
    'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30',
  PROCESSING:
    'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30',
  PENDING: 'bg-slate-500/10 text-slate-600 dark:text-slate-300 border-slate-500/30',
  FAILED: 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/30',
};

export default function DocumentsPage() {
  const { showToast } = useToast();
  const fileInputRef = useRef(null);

  const [documents, setDocuments] = useState([]);
  const [loadingDocs, setLoadingDocs] = useState(true);
  const [selectedFile, setSelectedFile] = useState(null);
  const [title, setTitle] = useState('');
  const [dragActive, setDragActive] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [formError, setFormError] = useState('');

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const loadDocuments = async () => {
    setLoadingDocs(true);
    try {
      const list = await listDocuments();
      setDocuments(list);
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to load documents.', 'error');
    } finally {
      setLoadingDocs(false);
    }
  };

  useEffect(() => {
    loadDocuments();
  }, []);

  const validateAndSelectFile = (file) => {
    setFormError('');
    if (!file) {
      setSelectedFile(null);
      return;
    }

    const lowerName = file.name.toLowerCase();
    const hasValidExt = ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext));
    if (!hasValidExt) {
      setFormError('Unsupported file format. Please select a PDF, DOCX, or TXT file.');
      setSelectedFile(null);
      return;
    }

    if (file.size === 0) {
      setFormError('Selected file is empty.');
      setSelectedFile(null);
      return;
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      setFormError('File exceeds the 10 MB upload limit.');
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragActive(false);
    const file = e.dataTransfer.files?.[0];
    validateAndSelectFile(file);
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    setFormError('');

    if (!selectedFile) {
      setFormError('Please select a PDF, DOCX, or TXT document first.');
      return;
    }

    setUploading(true);
    try {
      const created = await uploadDocument(selectedFile, title);
      setDocuments((prev) => [created, ...prev]);
      setSelectedFile(null);
      setTitle('');
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      showToast(
        `"${created.title}" indexed (${created.chunkCount ?? 0} chunks).`,
        'success'
      );
    } catch (err) {
      const msg = err.response?.data?.message || 'Document processing failed.';
      setFormError(msg);
      showToast(msg, 'error');
      loadDocuments();
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (doc) => {
    setDeletingId(doc.id);
    try {
      await deleteDocument(doc.id);
      setDocuments((prev) => prev.filter((item) => item.id !== doc.id));
      setConfirmDeleteId(null);
      showToast(`Deleted "${doc.title}".`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete document.', 'error');
    } finally {
      setDeletingId(null);
    }
  };

  const filteredDocs = useMemo(() => {
    return documents.filter((doc) => {
      const matchesStatus =
        statusFilter === 'ALL' || doc.processingStatus === statusFilter;
      const query = searchQuery.trim().toLowerCase();
      const matchesSearch =
        !query ||
        doc.title.toLowerCase().includes(query) ||
        doc.originalFileName.toLowerCase().includes(query);
      return matchesStatus && matchesSearch;
    });
  }, [documents, searchQuery, statusFilter]);

  return (
    <div className="max-w-5xl mx-auto px-6 py-8 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
          Upload PDF, DOCX, or TXT files to chunk, embed, and index them for RAG search.
        </p>
      </div>

      {/* Upload Interface */}
      <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
        <h2 className="text-base font-semibold mb-4">Upload Technical Document</h2>

        {formError && (
          <div className="mb-4 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-600 dark:text-rose-300">
            {formError}
          </div>
        )}

        <form onSubmit={handleUpload} className="space-y-4">
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setDragActive(true);
            }}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`rounded-2xl border-2 border-dashed p-6 text-center transition cursor-pointer ${
              dragActive
                ? 'border-indigo-500 bg-indigo-500/10'
                : 'border-slate-300 dark:border-slate-700 hover:border-indigo-400 dark:hover:border-indigo-500/60 bg-slate-50 dark:bg-slate-950/50'
            }`}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.docx,.txt"
              onChange={(e) => validateAndSelectFile(e.target.files?.[0] || null)}
              className="hidden"
            />
            <FileUp className="w-8 h-8 text-indigo-500 mx-auto mb-2" />
            {selectedFile ? (
              <div>
                <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {selectedFile.name}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  {formatBytes(selectedFile.size)} &middot; Click or drop another file to replace
                </p>
              </div>
            ) : (
              <div>
                <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                  Drag and drop your file here, or click to browse
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  Supported formats: PDF, DOCX, TXT &middot; Max 10 MB
                </p>
              </div>
            )}
          </div>

          <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-end">
            <div className="flex-1">
              <label
                htmlFor="doc-title"
                className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5"
              >
                Document Title (optional)
              </label>
              <input
                id="doc-title"
                type="text"
                maxLength={255}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Defaults to file name if left blank"
                className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3.5 py-2.5 text-sm placeholder-slate-400 dark:placeholder-slate-500 focus:border-indigo-500 focus:outline-none"
              />
            </div>

            <button
              type="submit"
              disabled={uploading || !selectedFile}
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 px-5 py-2.5 text-sm font-medium text-white transition cursor-pointer"
            >
              {uploading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Upload className="w-4 h-4" />
              )}
              {uploading ? 'Extracting & Embedding...' : 'Upload & Process'}
            </button>
          </div>
        </form>
      </section>

      {/* Document Management List */}
      <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <h2 className="text-base font-semibold">
            Library ({filteredDocs.length}
            {filteredDocs.length !== documents.length ? ` of ${documents.length}` : ''})
          </h2>

          <div className="flex flex-col sm:flex-row gap-2.5">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search title or file..."
                className="w-full sm:w-56 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 pl-9 pr-3 py-1.5 text-xs focus:border-indigo-500 focus:outline-none"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 px-3 py-1.5 text-xs focus:border-indigo-500 focus:outline-none cursor-pointer"
            >
              <option value="ALL">All Statuses</option>
              <option value="COMPLETED">Completed</option>
              <option value="PROCESSING">Processing</option>
              <option value="FAILED">Failed</option>
            </select>
          </div>
        </div>

        {loadingDocs ? (
          <div className="py-12 flex items-center justify-center gap-2 text-sm text-slate-500 dark:text-slate-400">
            <Loader2 className="w-4 h-4 animate-spin text-indigo-500" />
            Loading your documents...
          </div>
        ) : filteredDocs.length === 0 ? (
          <div className="py-12 text-center border border-dashed border-slate-300 dark:border-slate-800 rounded-xl">
            <FileText className="w-8 h-8 text-slate-400 dark:text-slate-600 mx-auto mb-2" />
            <p className="text-sm font-medium text-slate-600 dark:text-slate-300">
              {documents.length === 0
                ? 'No documents uploaded yet'
                : 'No documents match your filter'}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
              {documents.length === 0
                ? 'Upload your first technical guide above to start asking questions.'
                : 'Try clearing your search query or changing the status filter.'}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-slate-200 dark:divide-slate-800">
            {filteredDocs.map((doc) => (
              <div
                key={doc.id}
                className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-medium text-slate-900 dark:text-slate-100 truncate">
                      {doc.title}
                    </span>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
                      {doc.fileType}
                    </span>
                    <span
                      className={`inline-flex items-center gap-1 text-[11px] font-medium px-2.5 py-0.5 rounded-full border ${
                        STATUS_BADGE[doc.processingStatus] || STATUS_BADGE.PENDING
                      }`}
                    >
                      {doc.processingStatus === 'COMPLETED' && (
                        <CheckCircle2 className="w-3 h-3" />
                      )}
                      {doc.processingStatus === 'FAILED' && (
                        <XCircle className="w-3 h-3" />
                      )}
                      {(doc.processingStatus === 'PROCESSING' ||
                        doc.processingStatus === 'PENDING') && (
                        <Clock className="w-3 h-3" />
                      )}
                      {doc.processingStatus}
                    </span>
                  </div>

                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                    {doc.originalFileName} &middot; {formatBytes(doc.fileSize)}
                    {doc.pageCount
                      ? ` · ${doc.pageCount} page${doc.pageCount === 1 ? '' : 's'}`
                      : ''}
                    {doc.chunkCount
                      ? ` · ${doc.chunkCount} chunk${doc.chunkCount === 1 ? '' : 's'}`
                      : ''}
                    {doc.createdAt ? ` · ${formatDateTime(doc.createdAt)}` : ''}
                  </p>

                  {doc.errorMessage && (
                    <p className="text-xs text-rose-600 dark:text-rose-400 mt-1">
                      {doc.errorMessage}
                    </p>
                  )}
                </div>

                <div className="flex items-center gap-2 self-start sm:self-center shrink-0">
                  {confirmDeleteId === doc.id ? (
                    <>
                      <button
                        type="button"
                        onClick={() => handleDelete(doc)}
                        disabled={deletingId === doc.id}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-rose-600 hover:bg-rose-500 disabled:opacity-50 px-3 py-1.5 text-xs font-medium text-white transition cursor-pointer"
                      >
                        {deletingId === doc.id && (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        )}
                        Confirm
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmDeleteId(null)}
                        disabled={deletingId === doc.id}
                        className="rounded-xl border border-slate-300 dark:border-slate-700 px-3 py-1.5 text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmDeleteId(doc.id)}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 dark:border-slate-700 hover:border-rose-500/50 hover:bg-rose-500/10 hover:text-rose-600 dark:hover:text-rose-300 px-3 py-1.5 text-xs font-medium text-slate-600 dark:text-slate-300 transition cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Delete
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
