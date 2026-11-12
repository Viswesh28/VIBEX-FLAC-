import { useEffect, useRef } from 'react';
import { createLongPress } from './long-press.js';
export function LongPressButton({ onLongPress, onClick, children, ...props }) {
  const latest=useRef(onLongPress), handler=useRef(); latest.current=onLongPress;
  if(!handler.current) handler.current=createLongPress({onLongPress:()=>{ if(navigator.vibrate) navigator.vibrate(10); latest.current?.(); }});
  useEffect(() => {
    const cancel = () => handler.current.cancel();
    // Scroll can begin on an ancestor without a corresponding pointermove.
    window.addEventListener('scroll', cancel, true);
    return () => { window.removeEventListener('scroll', cancel, true); cancel(); };
  }, []);
  return <button {...props}
    onPointerDown={(e)=>handler.current.down(e)} onPointerMove={(e)=>handler.current.move(e)}
    onPointerUp={(e)=>handler.current.up(e)} onPointerCancel={()=>handler.current.cancel()}
    onContextMenu={(e)=>{e.preventDefault();handler.current.context();}}
    onKeyDown={(e)=>{if(e.key==='ContextMenu'||(e.shiftKey&&e.key==='F10')){e.preventDefault();handler.current.context();}}}
    onClick={(e)=>{if(handler.current.consumeClick()){e.preventDefault();e.stopPropagation();return;}onClick?.(e);}}>{children}</button>;
}
