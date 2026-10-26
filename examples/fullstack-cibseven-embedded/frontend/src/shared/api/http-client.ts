/**
 * The single fetch mutator every generated TanStack Query hook goes through. orval calls it as
 * `httpClient<T>(url, requestInit)` and expects the parsed response body back.
 *
 * All requests are same-origin relative URLs — in dev the Vite proxy forwards `/api` to the backend,
 * in the single-jar demo the frontend is served from the backend itself, so there is no CORS on the
 * production path and no base URL to configure.
 *
 * Two seams a fork that adds auth would touch live here: inject an `Authorization` header below, and
 * guard routes in `app/providers`. See frontend/README.md.
 */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly problem: unknown,
  ) {
    super(`Request failed with status ${status}`);
    this.name = "HttpError";
  }
}

export const httpClient = async <T>(url: string, options?: RequestInit): Promise<T> => {
  const response = await fetch(url, options);

  const text = await response.text();
  const body = text ? safeJsonParse(text) : undefined;

  if (!response.ok) {
    // The backend answers errors as RFC 9457 application/problem+json.
    throw new HttpError(response.status, body);
  }

  return body as T;
};

const safeJsonParse = (text: string): unknown => {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
};

export default httpClient;
