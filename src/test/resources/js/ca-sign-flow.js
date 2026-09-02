const els = [ ...document.querySelectorAll( 'button,a,[role=button]' ) ].filter( e => e.offsetParent !== null );
const signs = els.filter( e => { const t = String( e.innerText ).replace( /\s+/g, ' ' ).trim().toLowerCase(); return t === 'sign'; } );
if ( !signs.length ) return '';
const button = signs.filter( e => e.tagName === 'BUTTON' ).pop();
const choice = button || signs[ signs.length - 1 ];
choice.scrollIntoView( { 'block': 'center', 'inline': 'center' } );
choice.click();
return choice.tagName + ' ' + ( choice.getAttribute( 'href' ) || '' );