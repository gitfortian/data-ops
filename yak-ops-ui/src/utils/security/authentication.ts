export const AUTHENTICATION_INVALIDATED_EVENT =
  'yak-security:authentication-invalidated';

export const dispatchAuthenticationInvalidated = () => {
  window.dispatchEvent(new Event(AUTHENTICATION_INVALIDATED_EVENT));
};
