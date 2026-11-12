/** Pointer gesture state machine; scheduler injected for deterministic tests. */
export function createLongPress({ onLongPress, delay = 550, distance = 12, clock = globalThis }) {
  let timer, pointer, origin, fired = false;
  const cancel = () => { clock.clearTimeout(timer); timer = undefined; pointer = undefined; origin = undefined; };
  return {
    down(e) {
      if (e.button !== 0 || pointer !== undefined) return;
      fired = false; pointer = e.pointerId; origin = { x:e.clientX, y:e.clientY };
      timer = clock.setTimeout(() => { timer=undefined; if(pointer !== undefined) { fired=true; onLongPress(); } },delay);
    },
    move(e) { if(pointer===e.pointerId && origin && Math.hypot(e.clientX-origin.x,e.clientY-origin.y)>distance) cancel(); },
    up(e) { if(pointer===e.pointerId) cancel(); },
    cancel,
    consumeClick() { if(!fired)return false; fired=false; return true; },
    context() { cancel(); if (!fired) onLongPress(); },
  };
}
