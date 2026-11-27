export function segmentPath(index:number,count:number):string {
  if(count===1)return 'M 200 200 m -190 0 a 190 190 0 1 0 380 0 a 190 190 0 1 0 -380 0';
  const step=2*Math.PI/count;const a=-Math.PI/2+index*step;const b=a+step;
  const x=(angle:number)=>200+190*Math.cos(angle);const y=(angle:number)=>200+190*Math.sin(angle);
  return `M 200 200 L ${x(a)} ${y(a)} A 190 190 0 ${step>Math.PI?1:0} 1 ${x(b)} ${y(b)} Z`;
}
export function targetRotation(current:number,index:number,count:number):number {
  if(!Number.isInteger(index)||!Number.isInteger(count)||count<1||index<0||index>=count)throw new Error('Invalid segment');
  const target=(360-(index+.5)*360/count)%360;
  return current+1800+((target-(current%360)+360)%360);
}
export function labelPosition(index:number,count:number) {const angle=-Math.PI/2+(index+.5)*2*Math.PI/count;return {x:200+125*Math.cos(angle),y:200+125*Math.sin(angle)};}
