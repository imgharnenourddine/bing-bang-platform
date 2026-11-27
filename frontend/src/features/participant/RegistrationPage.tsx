import { useRef, useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, CalendarDays, MapPin, UserRound } from 'lucide-react';
import { ApiError } from '../../services/api';
import partners from '../../assets/participant/official-partners.webp';
import artwork from '../../assets/participant/poster-art.webp';
import { useAuth } from './auth';
import { NAME_MAX_LENGTH, validateName } from './validation';
import { createSubmissionGate } from './registrationApi';
import './registration.css';

type Field = 'firstName' | 'lastName';

export function RegistrationPage() {
  const { state, checkSession, register } = useAuth();
  const navigate = useNavigate();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const gate = useRef(createSubmissionGate());

  if (state.status === 'authenticated' && state.identity.role === 'PARTICIPANT') return <Navigate to="/participant" replace />;
  const checking = state.status === 'checking';
  const admin = state.status === 'authenticated' && state.identity.role === 'ADMIN';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state.status !== 'anonymous') return;
    const next = { firstName: validateName(firstName, 'First name'), lastName: validateName(lastName, 'Last name') };
    setErrors(next);
    if (next.firstName || next.lastName) return;
    if (!gate.current.start()) return;
    setSubmitting(true);
    setServerError('');
    try {
      const created = await register(firstName.trim(), lastName.trim());
      navigate('/participant', { replace: true, state: created ? { registered: true } : null });
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setServerError('Please check your names and try again.');
      } else if (error instanceof ApiError && error.status === 403) {
        setServerError('Registration was not allowed. Please refresh the page and check your session.');
      } else {
        setServerError('We could not confirm your registration. Check your connection and try again.');
      }
    } finally {
      gate.current.finish();
      setSubmitting(false);
    }
  }

  return <main className="registration-page">
    <div className="registration-canvas">
      <header className="event-header">
        <img className="partners" src={partners} alt="N7Geeks, Google Developer Groups On Campus, UH2C ENSET, AWS Cloud Clubs and FDE" />
        <p className="event-kicker">THREE WORLDS. ONE BEGINNING</p>
        <h1 className="event-title"><span>THE BIG</span><span>BANG</span></h1>
        <p className="event-description">Discover the people, ideas,<br className="desktop-break" /> and opportunities shaping the year ahead.</p>
      </header>

      <section className="registration-stage" aria-labelledby="registration-title">
        <div className="color-field color-field-blue" aria-hidden="true" />
        <div className="color-field color-field-purple" aria-hidden="true" />
        <div className="registration-card">
          <h2 id="registration-title">Join the experience.</h2>
          <p className="card-description">Register to get your unique participant number and take part in the event.</p>
          {checking ? <p className="session-message" role="status">Checking your session…</p> :
            state.status === 'error' ? <div className="session-message" role="alert"><p>We couldn’t check your session. Please check your connection.</p><button type="button" className="text-action" onClick={() => void checkSession()}>Try again</button></div> :
            admin ? <p className="session-message" role="status">You’re signed in as an administrator. Sign out of that session before registering as a participant.</p> :
            <form noValidate onSubmit={event => void submit(event)}>
              <div className="fields">
                <label className={`field ${errors.firstName ? 'field-invalid' : ''}`}>
                  <span className="visually-hidden">First name</span><UserRound aria-hidden="true" size={22} strokeWidth={1.8} />
                  <input value={firstName} onChange={event => { setFirstName(event.target.value); setErrors(previous => ({ ...previous, firstName: undefined })); }} name="firstName" autoComplete="given-name" placeholder="First name *" maxLength={NAME_MAX_LENGTH} aria-required="true" aria-invalid={!!errors.firstName} aria-describedby={errors.firstName ? 'first-name-error' : undefined} />
                </label>
                {errors.firstName && <p id="first-name-error" className="field-error" role="alert">{errors.firstName}</p>}
                <label className={`field ${errors.lastName ? 'field-invalid' : ''}`}>
                  <span className="visually-hidden">Last name</span><UserRound aria-hidden="true" size={22} strokeWidth={1.8} />
                  <input value={lastName} onChange={event => { setLastName(event.target.value); setErrors(previous => ({ ...previous, lastName: undefined })); }} name="lastName" autoComplete="family-name" placeholder="Last name *" maxLength={NAME_MAX_LENGTH} aria-required="true" aria-invalid={!!errors.lastName} aria-describedby={errors.lastName ? 'last-name-error' : undefined} />
                </label>
                {errors.lastName && <p id="last-name-error" className="field-error" role="alert">{errors.lastName}</p>}
              </div>
              {serverError && <p className="server-error" role="alert">{serverError}</p>}
              <button className="register-button" type="submit" disabled={submitting}>
                {submitting ? <><span className="spinner" aria-hidden="true" /> Registering…</> : <>Get my number <ArrowRight aria-hidden="true" size={23} /></>}
              </button>
              {submitting && <span className="visually-hidden" role="status">Registering, please wait.</span>}
            </form>}
        </div>
        <img className="poster-art" src={artwork} alt="" aria-hidden="true" />
      </section>
      <footer className="event-footer">
        <div><CalendarDays aria-hidden="true" /><span>28-11-2026 · 13:00</span></div>
        <div><MapPin aria-hidden="true" /><span>Amphithéâtre ·<br /> ENSET Mohammedia</span></div>
      </footer>
    </div>
  </main>;
}
