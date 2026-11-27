import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import './styles/index.css';

function Home() {
  return <main className="shell"><div className="mark" aria-hidden="true">✳</div><p className="eyebrow">ENSET MOHAMMEDIA · KICK-OFF</p><h1>THE BIG<br /><span>BANG</span><b>.</b></h1><p className="tagline">Three worlds.<br />One beginning.</p><div className="divider" /><p className="note">Something big is coming together.</p><p className="clubs">N7GEEKS <i>×</i> GDG <i>×</i> AWS CLOUD CLUBS</p></main>;
}
function NotFound() { return <main className="shell"><p className="eyebrow">404</p><h1>LOST IN<br />SPACE<span>.</span></h1><p className="tagline">This page doesn't exist.</p><Link className="link" to="/">Back to the beginning →</Link></main>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><Routes><Route path="/" element={<Home />} /><Route path="*" element={<NotFound />} /></Routes></BrowserRouter></React.StrictMode>);
