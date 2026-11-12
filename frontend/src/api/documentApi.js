import apiClient from './client';

export async function listDocuments() {
  const { data } = await apiClient.get('/documents');
  return data;
}

export async function uploadDocument(file, title) {
  const formData = new FormData();
  formData.append('file', file);
  if (title && title.trim()) {
    formData.append('title', title.trim());
  }
  const { data } = await apiClient.post('/documents', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

export async function deleteDocument(id) {
  await apiClient.delete(`/documents/${id}`);
}
