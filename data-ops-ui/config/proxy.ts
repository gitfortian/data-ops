/**
 * @name 代理的配置
 * @see 在生产环境 代理是无法生效的，所以这里没有生产环境的配置
 * -------------------------------
 * The agent cannot take effect in the production environment
 * so there is no configuration of the production environment
 * For details, please see
 * https://pro.ant.design/docs/deploy
 *
 * @doc https://umijs.org/docs/guides/proxy
 */

export default {
  dev: {
    '/api/': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: { '^/api': '/api' },
    },
    // Keep security calls same-origin in development and preserve the API's
    // /yak-security prefix expected by the backend controllers.
    '/yak-security/': {
      target: 'http://localhost:8080',
      // changeOrigin: true,
      // Do not leak an upstream Domain, and scope its session to this ingress.
      // The backend remains responsible for HttpOnly/Secure and SameSite=Lax.
      cookieDomainRewrite: '',
      cookiePathRewrite: {
        '*': '/',
      },
    },
    '/profile/avatar/': {
      changeOrigin: true,
      target: 'http://localhost:80',
    },
  },
  '/api/': {
    test: {
      target: 'http://localhost:80',
      changeOrigin: true,
      pathRewrite: { '^': '' },
    },
  },
  pre: {
    '/api/': {
      target: 'your pre url',
      changeOrigin: true,
      pathRewrite: { '^': '' },
    },
  },
};
