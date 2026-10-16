/* Chispa: guarda la página para abrirla rápido, pero siempre intenta traer la versión nueva primero */
var C='chispa-v1', FILES=['./','index.html','icon-192.png','icon-512.png','manifest.webmanifest'];
self.addEventListener('install',function(e){e.waitUntil(caches.open(C).then(function(c){return c.addAll(FILES)}).then(function(){return self.skipWaiting()}))});
self.addEventListener('activate',function(e){e.waitUntil(caches.keys().then(function(k){return Promise.all(k.filter(function(n){return n!==C}).map(function(n){return caches.delete(n)}))}).then(function(){return self.clients.claim()}))});
self.addEventListener('fetch',function(e){
  var r=e.request, u=new URL(r.url);
  if(r.method!=='GET'||u.origin!==location.origin) return;
  e.respondWith(fetch(r,{cache:'no-store'}).then(function(res){
    if(res&&res.ok&&!/version\.json/.test(u.pathname)){var cp=res.clone(); caches.open(C).then(function(c){c.put(r,cp)})}
    return res;
  }).catch(function(){return caches.match(r).then(function(m){return m||caches.match('index.html')})}));
});
