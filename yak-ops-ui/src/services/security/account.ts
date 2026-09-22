import {
  securityGetData,
  securityPostData,
  type SecurityRequestOptions,
} from './client';

/**
 * Yak Security AccountController contract.
 *
 * Authentication state is managed by the server and carried by cookies. The
 * shared request client sends cookies with `credentials: "include"`; this
 * module neither stores credentials nor depends on the server-side
 * authentication implementation.
 */
const ACCOUNT_API = '/api/v1/account';

/** AccountController's unauthenticated business/HTTP code. */
export const ACCOUNT_UNAUTHENTICATED_CODE = 401;

export type AccountLoginDTO = {
  userName: string;
  pw: string;
};

export const login = (body: AccountLoginDTO): Promise<void> =>
  securityPostData<void>(`${ACCOUNT_API}/login`, body);

export const googleLogin = (credential: string): Promise<void> =>
  securityPostData<void>('/api/v1/auth/google/login', { credential });

export const getCurrentUser = (
  options?: SecurityRequestOptions,
): Promise<API.CurrentUserVO> =>
  securityGetData<API.CurrentUserVO>(`${ACCOUNT_API}/current`, options);

export const logout = (): Promise<void> =>
  securityPostData<void>(`${ACCOUNT_API}/logout`);
