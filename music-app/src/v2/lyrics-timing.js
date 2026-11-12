export function lineAt(lines, position) {
 let low=0, high=lines.length;
 while(low<high){const mid=(low+high)>>>1;if(lines[mid].t<=position)low=mid+1;else high=mid;}
 return low-1;
}
