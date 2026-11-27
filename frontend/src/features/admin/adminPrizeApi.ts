import { api } from '../../services/api';
export type Category = 'STANDARD'|'PREMIUM';
export interface Prize {id:string;name:string;description:string|null;category:Category;baseWeight:number;initialStock:number;remainingStock:number;enabled:boolean}
export interface WheelConfig {geometricGrowthFactor:number;maximumPremiumMultiplier:number;consecutiveStandardAwards:number}
export interface PrizeInput {name:string;category:Category;baseWeight:number;enabled:boolean;initialStock?:number}
export async function listPrizes(){return (await api.get<Prize[]>('/admin/prizes')).data;}
export async function loadConfig(){return (await api.get<WheelConfig>('/admin/wheel/config')).data;}
export async function createPrize(input:PrizeInput){await api.post('/admin/prizes',input);}
export async function updatePrize(id:string,input:Partial<PrizeInput>){await api.patch(`/admin/prizes/${encodeURIComponent(id)}`,input);}
export async function adjustStock(id:string,quantityDelta:number,reason:string){await api.post(`/admin/prizes/${encodeURIComponent(id)}/stock`,{quantityDelta,reason});}
export async function saveConfig(geometricGrowthFactor:number,maximumPremiumMultiplier:number){await api.patch('/admin/wheel/config',{geometricGrowthFactor,maximumPremiumMultiplier});}
export function validDecimal(value:string,min:number,max:number,integerDigits:number):boolean {const text=value.trim();if(!/^\d+(\.\d{1,6})?$/.test(text))return false;const parts=text.split('.');return parts[0].replace(/^0+/,'').length<=integerDigits&&Number(text)>=min&&Number(text)<=max;}
export function validPrize(name:string,category:string,weight:string,stock?:string):boolean {return !!name.trim()&&name.trim().length<=120&&['STANDARD','PREMIUM'].includes(category)&&validDecimal(weight,0.000001,1000000000,10)&&(stock===undefined||(/^\d+$/.test(stock)&&Number.isSafeInteger(Number(stock))&&Number(stock)<=2147483647));}
export function validAdjustment(delta:string,reason:string,current:number):boolean {return /^[+-]?\d+$/.test(delta)&&Number.isSafeInteger(Number(delta))&&Number(delta)!==0&&Number(delta)>=-2147483648&&Number(delta)<=2147483647&&current+Number(delta)>=0&&current+Number(delta)<=2147483647&&!!reason.trim()&&reason.trim().length<=500;}
export function validConfig(r:string,m:string):boolean{return validDecimal(r,1,100,3)&&validDecimal(m,1,1000,4);}
export function filterPrizes(prizes:Prize[],query:string):Prize[]{return prizes.filter(p=>p.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));}
export function isPrizeEvent(type:string):boolean{return ['PRIZE_INVENTORY_UPDATED','PRIZE_AWARDED','WHEEL_STATE_UPDATED','WHEEL_RESULT'].includes(type);}
