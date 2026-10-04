import { useIntl } from '@umijs/max';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import DataSculpture from './DataSculpture';
import LoginPanel from './LoginPanel';
import './login.less';

export default function LoginPage() {
  const intl = useIntl();
  return (
    <main className="yak-login-page dataops-login-page" style={BRAND_CSS_VARIABLES}>
      <div className="dataops-login-shell">
        <header className="dataops-login-header">
          <div className="dataops-login-brand" aria-label="DataOps">
            <svg viewBox="0 0 40 40" fill="none" aria-hidden="true">
              <path d="M10 10h13a10 10 0 1 1 0 20H10" stroke="currentColor" strokeWidth="5" strokeLinecap="round" />
              <path d="M7 19h12m-7 9 9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
              <circle cx="7" cy="10" r="3" fill="currentColor" />
              <circle cx="7" cy="29" r="3" fill="currentColor" />
            </svg>
            <span aria-hidden="true">
              Data<span className="dataops-login-brand-ops">Ops</span>
            </span>
          </div>
          <span className="dataops-login-edition">
            {intl.formatMessage({ id: 'pages.login.edition', defaultMessage: '数据治理与运营' })}
          </span>
        </header>
        <div className="dataops-login-content">
          <div className="dataops-login-auth-column">
            <LoginPanel />
          </div>
          <section className="dataops-login-hero" aria-labelledby="dataops-login-title">
            <h1 id="dataops-login-title">
              {intl.formatMessage({ id: 'pages.login.title.lead', defaultMessage: '让数据，' })}
              <br />
              <span>{intl.formatMessage({ id: 'pages.login.title.emphasis', defaultMessage: '值得信任。' })}</span>
            </h1>
            <p>{intl.formatMessage({ id: 'pages.login.subtitle', defaultMessage: '连接数据，沉淀可信价值。' })}</p>
            <DataSculpture />
          </section>
        </div>
        <footer className="dataops-login-footer">© {new Date().getFullYear()} DataOps</footer>
      </div>
    </main>
  );
}
