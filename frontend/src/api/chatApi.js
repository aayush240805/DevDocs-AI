import apiClient from './client';

export async function askQuestion(question) {
  const { data } = await apiClient.post('/chat/ask', { question });
  return data;
}

export async function createChatSession(title) {
  const payload = title && title.trim() ? { title: title.trim() } : {};
  const { data } = await apiClient.post('/chat/sessions', payload);
  return data;
}

export async function listChatSessions() {
  const { data } = await apiClient.get('/chat/sessions');
  return data;
}

export async function getSessionMessages(sessionId) {
  const { data } = await apiClient.get(`/chat/sessions/${sessionId}/messages`);
  return data;
}

export async function sendSessionMessage(sessionId, question) {
  const { data } = await apiClient.post(`/chat/sessions/${sessionId}/messages`, { question });
  return data;
}

export async function deleteChatSession(sessionId) {
  await apiClient.delete(`/chat/sessions/${sessionId}`);
}
