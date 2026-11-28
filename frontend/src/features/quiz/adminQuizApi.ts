import { api } from '../../services/api';
export type Status='DRAFT'|'OPEN'|'QUESTION_ACTIVE'|'QUESTION_CLOSED'|'FINISHED';
export interface Quiz {id:string;title:string;status:Status;version:number;questionCount:number}
export interface Option {id:string;text:string;position:number;correct:boolean}
export interface Question {id:string;text:string;position:number;durationSeconds:number;points:number;options:Option[];quizVersion:number}
export interface Readiness {ready:boolean;questionCount:number;issues:string[]}
export interface State {quizId:string;status:Status;currentQuestionId:string|null;currentQuestionPosition:number|null;totalQuestions:number;questionDeadline:string|null;serverTime:string;allowedActions:string[];lifecycleVersion:number}
export interface Statistics {questionId:string;submittedAnswers:number;registeredParticipants:number}
export interface QuestionInput {text:string;durationSeconds:number|null;points:number|null;options:{text:string;correct:boolean}[];expectedVersion:number}
export const quizApi={
 quiz:async()=> (await api.get<Quiz>('/admin/quiz')).data,
 questions:async()=> (await api.get<Question[]>('/admin/quiz/questions')).data,
 readiness:async()=> (await api.get<Readiness>('/admin/quiz/readiness')).data,
 status:async()=> (await api.get<State>('/admin/quiz/status')).data,
 statistics:async(id:string)=> (await api.get<Statistics>(`/admin/quiz/questions/${id}/statistics`)).data,
 create:async(title:string)=>{await api.post('/admin/quiz',{title});},
 rename:async(title:string,expectedVersion:number)=>{await api.patch('/admin/quiz',{title,expectedVersion});},
 add:async(input:QuestionInput)=>{await api.post('/admin/quiz/questions',input);},
 update:async(id:string,input:QuestionInput)=>{await api.put(`/admin/quiz/questions/${id}`,input);},
 remove:async(id:string,expectedVersion:number)=>{await api.delete(`/admin/quiz/questions/${id}`,{params:{expectedVersion}});},
 reorder:async(questionIds:string[],expectedVersion:number)=>{await api.put('/admin/quiz/questions/order',{questionIds,expectedVersion});},
 transition:async(action:'start'|'open'|'close'|'next'|'finish',state:State)=>{const path=action==='start'||action==='finish'?`/admin/quiz/${action}`:`/admin/quiz/questions/${action}`;const body=action==='start'||action==='open'?{expectedVersion:state.lifecycleVersion}:{expectedQuestionId:state.currentQuestionId,expectedVersion:state.lifecycleVersion};await api.post(path,body);},
};
export function validateQuestion(text:string,options:string[],correct:number,duration:string,points:string):string {
 if(!text.trim()||text.trim().length>2000)return 'Enter a question of at most 2,000 characters.';
 if(options.length<2||options.length>10||options.some(o=>!o.trim()||o.trim().length>1000))return 'Provide 2–10 non-empty options, each at most 1,000 characters.';
 if(new Set(options.map(o=>o.trim().toLowerCase())).size!==options.length)return 'Answer options must be unique.';
 if(correct<0||correct>=options.length)return 'Select one correct answer.';
 if(![duration,points].every(v=>v===''||(/^[0-9]+$/.test(v)&&Number(v)>0&&Number(v)<=2147483647)))return 'Duration and points must be positive whole numbers.';
 return '';
}
export function remainingSeconds(deadline:string|null,serverTime:string,receivedAt:number,now:number):number {if(!deadline)return 0;return Math.max(0,Math.ceil((Date.parse(deadline)-(Date.parse(serverTime)+now-receivedAt))/1000));}
