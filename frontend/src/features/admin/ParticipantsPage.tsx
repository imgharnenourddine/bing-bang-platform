import { useMemo, useState } from 'react';
import { Search, UsersRound } from 'lucide-react';
import { AdminLayout } from './AdminLayout';
import { useAdminParticipants } from './useAdminParticipants';
import { filterParticipants,paginateParticipants } from './adminParticipantsApi';
import { formatNumber } from '../participant/homeState';
import './participants.css';
export function ParticipantsPage(){
 const {people,total,loading,error,connection,refresh}=useAdminParticipants();const [query,setQuery]=useState('');const [page,setPage]=useState(0);
 const filtered=useMemo(()=>filterParticipants(people??[],query),[people,query]);const result=paginateParticipants(filtered,page);
 return <AdminLayout page="participants"><main className="participants-main"><section className="participants-card" aria-labelledby="participants-title"><header className="participants-heading"><h2 id="participants-title"><UsersRound/> Participant List {total!==null?`(${total.toLocaleString()})`:''}</h2><label className="participants-search"><Search aria-hidden="true"/><span className="admin-sr-only">Search by number or name</span><input placeholder="Search by number or name..." value={query} onChange={event=>{setQuery(event.target.value);setPage(0);}}/></label></header>
 {loading&&people===null?<p role="status">Loading participants…</p>:people===null?<p role="alert">Participant list unavailable.</p>:people.length===0?<p>No participants are registered yet.</p>:filtered.length===0?<p>No participants match your search.</p>:<><div className="participants-table-wrap"><table><thead><tr><th scope="col">Number</th><th scope="col">First name</th><th scope="col">Last name</th></tr></thead><tbody>{result.content.map(person=><tr key={person.id}><td>{formatNumber(person.participantNumber)}</td><td>{person.firstName}</td><td>{person.lastName}</td></tr>)}</tbody></table></div><nav className="participants-pagination" aria-label="Participant pages"><button disabled={result.page===0} onClick={()=>setPage(result.page-1)}>Previous</button><span>Page {result.page+1} of {result.totalPages}</span><button disabled={result.page+1>=result.totalPages} onClick={()=>setPage(result.page+1)}>Next</button></nav></>}
 {total===null&&!loading&&<p className="participants-feedback" role="status">Total participant count unavailable.</p>}{error&&<p className="participants-feedback" role="alert">{error} <button onClick={()=>void refresh()}>Retry</button></p>}{connection!=='connected'&&<p className="participants-feedback" role="status">Live updates unavailable; the list refreshes after reconnection.</p>}</section></main></AdminLayout>;
}
