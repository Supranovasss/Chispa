/* Chispa: abre al instante con lo guardado y, en segundo plano, baja la versión nueva para la próxima vez */
var C='chispa-v3', FILES=['./','index.html','icon-192.png','icon-512.png','manifest.webmanifest'];
self.addEventListener('install',function(e){e.waitUntil(caches.open(C).then(function(c){return c.addAll(FILES)}).then(function(){return self.skipWaiting()}))});
self.addEventListener('activate',function(e){e.waitUntil(caches.keys().then(function(k){return Promise.all(k.filter(function(n){return n!==C}).map(function(n){return caches.delete(n)}))}).then(function(){return self.clients.claim()}))});
self.addEventListener('fetch',function(e){
  var r=e.request, u=new URL(r.url);
  if(r.method!=='GET') return;
  // Tipografías de Google: se guardan y se usan siempre desde el celular
  if(/fonts\.(googleapis|gstatic)\.com$/.test(u.hostname)){
    e.respondWith(caches.open(C).then(function(c){return c.match(r).then(function(m){
      return m||fetch(r).then(function(res){if(res&&(res.ok||res.type==='opaque')) c.put(r,res.clone()); return res});
    })}));
    return;
  }
  if(u.origin!==location.origin) return;
  // La revisión de versión siempre va a la red
  if(/version\.json$/.test(u.pathname)) return;
  // La página principal: primero la red (así siempre abre la versión más nueva); si no hay internet o tarda, lo guardado
  if(r.mode==='navigate'||/\/$|index\.html$/.test(u.pathname)){
    e.respondWith(caches.open(C).then(function(c){
      var net=new Promise(function(ok,no){var t=setTimeout(function(){no(new Error('lento'))},3000); fetch(r,{cache:'no-store'}).then(function(res){clearTimeout(t); if(res&&res.ok){c.put(r,res.clone()); c.put('index.html',res.clone()); ok(res)}else no(new Error('http'))},function(er){clearTimeout(t); no(er)})});
      return net.catch(function(){return c.match(r,{ignoreSearch:true}).then(function(m){return m||c.match('index.html')||c.match('./')})});
    }));
    return;
  }
  // El resto: responde ya con lo guardado y se actualiza por detrás
  e.respondWith(caches.open(C).then(function(c){return c.match(r,{ignoreSearch:true}).then(function(m){
    var net=fetch(r,{cache:'no-store'}).then(function(res){if(res&&res.ok) c.put(r,res.clone()); return res}).catch(function(){return null});
    if(m){e.waitUntil(net); return m}
    return net.then(function(res){return res||c.match('index.html')});
  })}));
});
