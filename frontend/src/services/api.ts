import axios, { AxiosError, AxiosHeaders } from 'axios';
import { config } from './config';

export interface CsrfToken {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface ApiProblem {
  status?: number;
  code?: string;
  detail?: string;
  error?: string;
  title?: string;
}

export class ApiError extends Error {
  constructor(public readonly status: number | undefined, public readonly problem: ApiProblem | undefined) {
    super(problem?.detail ?? problem?.error ?? problem?.title ?? 'The request could not be completed.');
    this.name = 'ApiError';
  }
}

// A separate transport avoids recursion while retrieving a token for mutations.
const transport = axios.create({ baseURL: config.apiBaseUrl, timeout: 10000, withCredentials: true });
export const api = axios.create({ baseURL: config.apiBaseUrl, timeout: 10000, withCredentials: true });

export async function getCsrfToken(): Promise<CsrfToken> {
  const response = await transport.get<CsrfToken>('/auth/csrf');
  return response.data;
}

api.interceptors.request.use(async request => {
  const method = (request.method ?? 'get').toLowerCase();
  if (!['get', 'head', 'options', 'trace'].includes(method)) {
    // A fresh token handles rotation after login, registration and logout.
    const csrf = await getCsrfToken();
    request.headers = AxiosHeaders.from(request.headers);
    request.headers.set(csrf.headerName, csrf.token);
  }
  return request;
});

api.interceptors.response.use(response => response, (error: unknown) => {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiProblem>;
    const status = axiosError.response?.status;
    // Notify the app without redirecting or retrying a possibly non-idempotent request.
    if (status === 401 && !axiosError.config?.url?.endsWith('/auth/admin/login') && typeof window !== 'undefined') {
      window.dispatchEvent(new Event('session-expired'));
    }
    return Promise.reject(new ApiError(status, axiosError.response?.data));
  }
  return Promise.reject(error);
});
