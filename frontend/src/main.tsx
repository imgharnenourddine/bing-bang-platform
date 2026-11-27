import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './features/participant/auth';
import { RegistrationPage } from './features/participant/RegistrationPage';
import { ParticipantHome } from './features/participant/ParticipantHome';
import { AdminLoginPage } from './features/admin/AdminLoginPage';
import { AdminProtectedRoute } from './features/admin/AdminProtectedRoute';
import { AdminDashboard } from './features/admin/AdminDashboard';
import { LuckyDrawPage } from './features/admin/LuckyDrawPage';
import { PrizeWheelPage } from './features/admin/PrizeWheelPage';
import { PrizeManagementPage } from './features/admin/PrizeManagementPage';
import { ParticipantsPage } from './features/admin/ParticipantsPage';
import './styles/index.css';

function NotFound() { return <main className="shell"><p className="eyebrow">404</p><h1>LOST IN<br />SPACE<span>.</span></h1><p className="tagline">This page doesn't exist.</p><Link className="link" to="/">Back to the beginning →</Link></main>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><AuthProvider><Routes><Route path="/" element={<RegistrationPage />} /><Route path="/participant" element={<ParticipantHome />} /><Route path="/admin/login" element={<AdminLoginPage />} /><Route path="/admin" element={<AdminProtectedRoute><AdminDashboard /></AdminProtectedRoute>} /><Route path="/admin/draw" element={<AdminProtectedRoute><LuckyDrawPage /></AdminProtectedRoute>} /><Route path="/admin/wheel" element={<AdminProtectedRoute><PrizeWheelPage /></AdminProtectedRoute>} /><Route path="/admin/prizes" element={<AdminProtectedRoute><PrizeManagementPage /></AdminProtectedRoute>} /><Route path="/admin/participants" element={<AdminProtectedRoute><ParticipantsPage /></AdminProtectedRoute>} /><Route path="*" element={<NotFound />} /></Routes></AuthProvider></BrowserRouter></React.StrictMode>);
